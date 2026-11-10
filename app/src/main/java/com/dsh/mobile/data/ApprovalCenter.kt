package com.dsh.mobile.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 统一待办状态层（spec §5.2）：唯一事实源。
 * 主构造全部依赖注入，纯逻辑可用假件单测；便利构造接 DshConnection。
 */
class ApprovalCenter(
    private val events: Flow<DshConnection.Event>,
    private val answerApprovalFn: suspend (sessionId: String, approvalId: String, outcome: String) -> Unit,
    private val answerQuestionsFn: suspend (sessionId: String, answers: List<DshConnection.QuestionAnswer>) -> Unit,
    private val state: Flow<DshConnection.State>,
    private val listSessionsFn: suspend () -> List<SessionSummary>,
    private val historyFn: suspend (sessionId: String) -> HistoryValue,
    private val scope: CoroutineScope,
    private val targetKey: () -> String? = { null },
    /** 查审批对应工具调用的参数（用于风险评估和显示命令）；查不到返回 null */
    private val argumentsFn: suspend (sessionId: String, callId: String) -> String? = { _, _ -> null },
) {
    companion object {
        /** 历史恢复窗口：最近 N 个活跃会话（spec §5.2） */
        const val RECOVERY_SESSION_LIMIT = 5
    }

    constructor(connection: DshConnection, scope: CoroutineScope) : this(
        events = connection.events,
        answerApprovalFn = connection::answerApproval,
        answerQuestionsFn = connection::answerQuestions,
        state = connection.state,
        listSessionsFn = {
            val sessions = connection.directory.sessions
            if (connection.currentProfile()?.paired != true) emptyList() else {
                sessions.load()
                val snapshot = sessions.state.first { it.value != null || it.error != null }
                snapshot.value.orEmpty()
            }
        },
        historyFn = { connection.history(it) },
        scope = scope,
        targetKey = { connection.currentProfile()?.takeIf { it.paired }?.computerScope() },
        argumentsFn = { sessionId, callId -> connection.toolCallArguments(sessionId, callId) },
    )

    /**
     * 别处（电脑或其他设备）处理掉了一条这里还显示着的待办：给用户一句说明，
     * 而不是让卡片无声消失。自己在这里处理的会先被乐观移除，不会触发。
     */
    private val _notices = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notices: kotlinx.coroutines.flow.SharedFlow<String> = _notices

    private val mutex = Mutex()
    private var currentTarget: String? = targetKey()

    private suspend fun syncTarget() {
        mutex.withLock {
            val next = targetKey()
            if (next != currentTarget) {
                currentTarget = next
                _items.value = emptyList()
                resolvedTombstones = emptySet()
            }
        }
    }
    private val _items = MutableStateFlow<List<PendingItem>>(emptyList())

    /** 已解决项的墓碑（键同 itemKey 格式），阻止乐观回滚「复活」并发已解决的项。 */
    private var resolvedTombstones: Set<String> = mutableSetOf()

    /** 已排序的待办列表（唯一事实源） */
    val items: StateFlow<List<PendingItem>> = _items
    val pendingCount: StateFlow<Int> =
        _items.map { it.size }.stateIn(scope, SharingStarted.Eagerly, 0)

    init {
        scope.launch { events.collect { syncTarget(); onEvent(it) } }
        scope.launch {
            state.distinctUntilChanged().collectLatest { s ->
                syncTarget()
                if (s is DshConnection.State.Connected) recover()
            }
        }
    }

    private fun itemKey(i: PendingItem): String = when (i) {
        is PendingItem.Approval -> "a:${i.sessionId}:${i.approvalId}"
        is PendingItem.Question -> "q:${i.sessionId}"
        is PendingItem.Error -> "e:${i.sessionId}"
    }

    private suspend fun mutate(transform: (MutableList<PendingItem>) -> Unit) {
        mutex.withLock {
            val list = _items.value.toMutableList()
            transform(list)
            _items.value = sortPendingItems(list)
        }
    }

    private suspend fun onEvent(e: DshConnection.Event) {
        when (e) {
            is DshConnection.Event.ApprovalRequested -> {
                var known: String? = null
                mutate { l ->
                    resolvedTombstones -= "a:${e.sessionId}:${e.approvalId}"
                    // 重连后重发的同一审批：保留已查到的参数
                    known = l.filterIsInstance<PendingItem.Approval>()
                        .firstOrNull { it.sessionId == e.sessionId && it.approvalId == e.approvalId }?.arguments
                    l.removeAll { it is PendingItem.Approval && it.sessionId == e.sessionId && it.approvalId == e.approvalId }
                    l += PendingItem.Approval(
                        e.sessionId, e.approvalId, e.toolName, e.reason, e.callId,
                        System.currentTimeMillis(), fromHistory = false, arguments = known,
                    )
                }
                val callId = e.callId
                if (known == null && callId != null) scope.launch { fillArguments(e.sessionId, e.approvalId, callId) }
            }
            is DshConnection.Event.ApprovalResolved -> {
                var removed: PendingItem.Approval? = null
                mutate { l ->
                    resolvedTombstones += "a:${e.sessionId}:${e.approvalId}"
                    if (resolvedTombstones.size > 200) resolvedTombstones = trimTombstones(resolvedTombstones)
                    removed = l.filterIsInstance<PendingItem.Approval>()
                        .firstOrNull { it.sessionId == e.sessionId && it.approvalId == e.approvalId }
                    l.removeAll { it is PendingItem.Approval && it.sessionId == e.sessionId && it.approvalId == e.approvalId }
                }
                removed?.let { _notices.tryEmit(resolvedElsewhereNotice(it.toolName, e.outcome)) }
            }
            is DshConnection.Event.QuestionRequested -> mutate { l ->
                resolvedTombstones -= "q:${e.sessionId}"
                l.removeAll { it is PendingItem.Question && it.sessionId == e.sessionId }
                l += PendingItem.Question(e.sessionId, e.questions, System.currentTimeMillis(), false)
            }
            is DshConnection.Event.QuestionResolved -> {
                var had = false
                mutate { l ->
                    resolvedTombstones += "q:${e.sessionId}"
                    if (resolvedTombstones.size > 200) resolvedTombstones = trimTombstones(resolvedTombstones)
                    had = l.removeAll { it is PendingItem.Question && it.sessionId == e.sessionId }
                }
                if (had) _notices.tryEmit(if (e.outcome == "cancelled") "一个问题已在别处回答或已取消" else "一个问题已在别处回答")
            }
            is DshConnection.Event.SessionEvent ->
                if (e.event.type == "agent/error") mutate { l ->
                    val prev = l.filterIsInstance<PendingItem.Error>()
                        .firstOrNull { it.sessionId == e.sessionId }
                    if (prev == null || e.event.seq > prev.seq) {
                        l.removeAll { it is PendingItem.Error && it.sessionId == e.sessionId }
                        l += PendingItem.Error(
                            e.sessionId, errorMessageOf(e.event.data), e.event.seq,
                            System.currentTimeMillis(), false,
                        )
                    }
                }
            else -> {}
        }
    }

    /** 补上审批对应工具调用的参数（异步，查不到就保持 null，按工具名评估） */
    private suspend fun fillArguments(sessionId: String, approvalId: String, callId: String) {
        val args = try {
            argumentsFn(sessionId, callId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return
        mutate { l ->
            val i = l.indexOfFirst { it is PendingItem.Approval && it.sessionId == sessionId && it.approvalId == approvalId }
            if (i >= 0) l[i] = (l[i] as PendingItem.Approval).copy(arguments = args)
        }
    }

    /** 连接成功后扫描最近 5 个会话历史；实时优先，失败静默（spec §5.2/§7） */
    private suspend fun recover() {
        val target = currentTarget
        val summaries = runCatching { listSessionsFn() }.getOrElse { return }
        val recovered = mutableListOf<PendingItem>()
        for (s in summaries.sortedByDescending { it.updatedAt }.take(RECOVERY_SESSION_LIMIT)) {
            if (target != targetKey()) return
            val hv = runCatching { historyFn(s.sessionId) }.getOrNull() ?: continue
            recovered += scanHistoryEvents(s.sessionId, hv.events)
        }
        mutex.withLock {
            if (target != targetKey()) return
            val merged = _items.value.toMutableList()
            for (item in recovered) {
                if (itemKey(item) !in resolvedTombstones && merged.none { itemKey(it) == itemKey(item) }) merged += item
            }
            _items.value = sortPendingItems(merged)
        }
    }

    // ── 应答（乐观更新 + 失败回滚，spec §5.2）──

    private suspend fun answerApprovalItem(sessionId: String, approvalId: String, outcome: String) {
        syncTarget()
        val target = currentTarget
        val item = items.value.filterIsInstance<PendingItem.Approval>()
            .firstOrNull { it.sessionId == sessionId && it.approvalId == approvalId } ?: return
        mutate { l -> l.removeAll { it is PendingItem.Approval && it.sessionId == sessionId && it.approvalId == approvalId } }
        if (target != targetKey()) return
        try {
            answerApprovalFn(sessionId, approvalId, outcome)
        } catch (e: Exception) {
            mutate { l ->
                if (target == targetKey() && itemKey(item) !in resolvedTombstones && l.none { itemKey(it) == itemKey(item) }) l += item
            }
            throw e
        }
    }

    suspend fun allow(sessionId: String, approvalId: String) =
        answerApprovalItem(sessionId, approvalId, "allowed-once")

    suspend fun reject(sessionId: String, approvalId: String) =
        answerApprovalItem(sessionId, approvalId, "rejected")

    suspend fun answerQuestions(sessionId: String, answers: List<DshConnection.QuestionAnswer>) {
        syncTarget()
        val target = currentTarget
        val item = items.value.filterIsInstance<PendingItem.Question>()
            .firstOrNull { it.sessionId == sessionId } ?: return
        mutate { l -> l.removeAll { it is PendingItem.Question && it.sessionId == sessionId } }
        if (target != targetKey()) return
        try {
            answerQuestionsFn(sessionId, answers)
        } catch (e: Exception) {
            mutate { l ->
                if (target == targetKey() && itemKey(item) !in resolvedTombstones && l.none { itemKey(it) == itemKey(item) }) l += item
            }
            throw e
        }
    }

    suspend fun skipQuestions(sessionId: String) = answerQuestions(sessionId, emptyList())

    suspend fun allowAllApprovals(): List<String> {
        syncTarget()
        val target = currentTarget
        val snaps = items.value.filterIsInstance<PendingItem.Approval>()
            .map { it.sessionId to it.approvalId }
        val failures = mutableListOf<String>()
        for ((sid, aid) in snaps) {
            if (target != targetKey()) break
            try { allow(sid, aid) } catch (e: CancellationException) { throw e } catch (e: Exception) { failures += e.message ?: "unknown" }
        }
        return failures
    }

    suspend fun rejectAllApprovals(): List<String> {
        syncTarget()
        val target = currentTarget
        val snaps = items.value.filterIsInstance<PendingItem.Approval>()
            .map { it.sessionId to it.approvalId }
        val failures = mutableListOf<String>()
        for ((sid, aid) in snaps) {
            if (target != targetKey()) break
            try { reject(sid, aid) } catch (e: CancellationException) { throw e } catch (e: Exception) { failures += e.message ?: "unknown" }
        }
        return failures
    }

    suspend fun skipAllQuestions(): List<String> {
        syncTarget()
        val target = currentTarget
        val sessions = items.value.filterIsInstance<PendingItem.Question>().map { it.sessionId }
        val failures = mutableListOf<String>()
        for (sid in sessions) {
            if (target != targetKey()) break
            try { skipQuestions(sid) } catch (e: CancellationException) { throw e } catch (e: Exception) { failures += e.message ?: "unknown" }
        }
        return failures
    }
}

/**
 * 墓碑超限裁剪：保留最近 capacity 条（按加入顺序），丢弃最旧——与全清相比不丢近期已解决项。
 * 约定输入为插入有序集合（mutableSetOf 即 LinkedHashSet）；capacity <= 0 时清空。
 */
internal fun trimTombstones(current: Set<String>, capacity: Int = 200): Set<String> {
    if (capacity <= 0) return emptySet()
    if (current.size <= capacity) return current
    return LinkedHashSet(current).drop(current.size - capacity).toSet()
}

/** 别处处理掉审批时给用户的说明；outcome 来自插件：allowed-once / rejected / cancelled */
internal fun resolvedElsewhereNotice(toolName: String, outcome: String): String = when (outcome) {
    "allowed-once", "allowed", "allowed-always" -> "「$toolName」已在别处允许"
    "rejected" -> "「$toolName」已在别处拒绝"
    else -> "「$toolName」的审批已在别处处理或已取消"
}

/** 在会话最近的历史里找某次工具调用的参数（审批事件本身不带参数） */
suspend fun DshConnection.toolCallArguments(sessionId: String, callId: String): String? {
    var before: Long? = null
    repeat(2) {
        val page = history(sessionId, beforeSeq = before, maxMessages = 3)
        page.events.lastOrNull { e ->
            e.event.type == DshEventTypes.TOOL_CALL &&
                ((e.event.data as? kotlinx.serialization.json.JsonObject)?.get("callId") as? kotlinx.serialization.json.JsonPrimitive)
                    ?.content == callId
        }?.let { e ->
            return ((e.event.data as kotlinx.serialization.json.JsonObject)["arguments"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        }
        if (!page.hasMore) return null
        before = page.events.firstOrNull()?.event?.seq ?: return null
    }
    return null
}
