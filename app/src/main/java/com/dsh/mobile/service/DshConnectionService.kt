package com.dsh.mobile.service

import kotlinx.coroutines.flow.combine
import com.dsh.mobile.data.shortSessionId
import com.dsh.mobile.data.computerScope

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.dsh.mobile.DshApplication
import com.dsh.mobile.MainActivity
import com.dsh.mobile.data.DshConnection
import com.dsh.mobile.data.HostProfile
import com.dsh.mobile.data.connectionUrl
import com.dsh.mobile.data.PairingCoordinator
import com.dsh.mobile.data.SettingsStore
import com.dsh.mobile.data.TokenUsageWatcher
import com.dsh.mobile.data.shouldNotifyApproval
import com.dsh.mobile.data.shouldNotifyCompletion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 前台服务：持有一条只读连接监听事件。
 * - 审批/问答请求 → 高优先级横幅通知（去重，点击直达对应会话）
 * - 任务完成（会话从运行态转空闲，8 秒防抖确认）→ 普通通知
 * App 在前台时不重复通知（会话页已有横幅）。
 */
class DshConnectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: DshConnection? = null
    private var watchJob: Job? = null
    private var eventsJob: Job? = null
    private var stateJob: Job? = null
    private var pairingJob: Job? = null
    private var notificationSeq = 0

    /** 会话活跃状态（运行中→空闲迁移触发完成通知） */
    private val sessionActive = mutableMapOf<String, Boolean>()
    private val completionJobs = mutableMapOf<String, Job>()
    private val pending = mutableMapOf<Pair<String, String?>, Interaction>()
    private val processingNonces = mutableSetOf<String>()
    private val pendingTag = "dsh-interaction"

    private fun removeInteraction(key: Pair<String, String?>) {
        val item = pending.remove(key) ?: return
        notificationManager().cancel("$pendingTag/${item.nonce}", 0)
        WidgetState.pushPending(this, pending.size)
    }

    private fun clearInteractions() {
        pending.keys.toList().forEach(::removeInteraction)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = notificationManager()
        manager.activeNotifications.filter { it.tag?.startsWith("$pendingTag/") == true }
            .forEach { manager.cancel(it.tag, it.id) }
        startAsForeground()
    }

    private fun startAsForeground() {
        val notification = Notification.Builder(this, DshApplication.CHANNEL_ID)
            .setContentTitle("DSH Remote")
            .setContentText("后台连接已开启，审批/确认/完成会横幅提醒")
            .setSmallIcon(com.dsh.mobile.R.drawable.ic_notification_dsh)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()
        startForeground(1, notification)
    }

    /** 常驻通知同步当前连接状态（已连接 / 连接中 / 错误并自动重连） */
    private fun updateForegroundText(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val notification = Notification.Builder(this, DshApplication.CHANNEL_ID)
            .setContentTitle("DSH Remote")
            .setContentText(text)
            .setSmallIcon(com.dsh.mobile.R.drawable.ic_notification_dsh)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()
        manager.notify(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == INTERACT) {
            if (watchJob == null) {
                stopSelf(startId)
                return START_NOT_STICKY
            }
            val nonce = intent.data?.pathSegments?.firstOrNull() ?: return START_STICKY
            if (processingNonces.add(nonce)) {
                scope.launch {
                    try {
                        processAction(intent)
                    } finally {
                        processingNonces.remove(nonce)
                    }
                }
            }
            return START_STICKY
        }
        startWatching()
        return START_STICKY
    }

    private fun startWatching() {
        if (watchJob != null) return
        watchJob = scope.launch {
            val settings = SettingsStore(this@DshConnectionService)
            settings.ensureMigrated()
            if (!settings.backgroundNotify.first()) {
                stopSelf()
                return@launch
            }
            // 跟随活跃主机：切换即重启 watcher（单活跃语义）
            combine(settings.activeProfileId, settings.profiles) { activeId, profiles ->
                profiles.firstOrNull { it.id == activeId }
            }.distinctUntilChanged { old, new ->
                fun comparable(p: com.dsh.mobile.data.HostProfile?) = p?.copy(lastUsedAt = 0,
                    lastErrorCode = null, remark = "", deviceModel = "", autoConnect = false, lastHostVersion = null)
                comparable(old) == comparable(new)
            }.collect { profile ->
                    eventsJob?.cancel()
                    stateJob?.cancel()
                    pairingJob?.cancel()
                    watcher?.disconnect()
                    watcher = null
                    clearInteractions()
                    sessionActive.clear()
                    completionJobs.values.forEach { it.cancel() }
                    completionJobs.clear()

                    if (profile == null || !profile.paired || runCatching { profile.connectionUrl() }.isFailure) {
                        updateForegroundText("未选择活跃主机")
                        return@collect
                    }
                    val connection = DshConnection(this@DshConnectionService)
                    watcher = connection
                    // 后台重连同样过配对/设备校验：IP 被回收或配对被撤销时断开，不再无限重连
                    pairingJob = scope.launch {
                        PairingCoordinator(connection, settings, this).start()
                    }
                    eventsJob = launch { connection.events.collect { if (watcher === connection) handle(it) } }
                    stateJob = launch {
                        connection.state.collect { st ->
                            if (st !is DshConnection.State.Connected) clearInteractions()
                            val text = when (st) {
                                is DshConnection.State.Connected -> "已连接 " + st.baseUrl
                                is DshConnection.State.Connecting -> "连接中…"
                                // st.message 已含「N 秒后自动重连」或「（已停止自动重连）」，直接透传
                                is DshConnection.State.Error -> st.message
                                else -> "后台连接已开启"
                            }
                            updateForegroundText(text)
                            WidgetState.push(this@DshConnectionService, text)
                        }
                    }
                    connection.connect(profile) { info ->
                        if (info.errorCode != null) {
                            scope.launch {
                                settings.markAttempt(info.profileId, info.errorCode)
                            }
                        }
                    }
                }
        }
    }

    private suspend fun handle(event: DshConnection.Event) {
        when (event) {
            is DshConnection.Event.ApprovalResolved -> removeInteraction(event.sessionId to event.approvalId)
            is DshConnection.Event.QuestionResolved -> {
                val key = event.sessionId to null
                if (event.questionRpcId.isEmpty() || pending[key]?.rpc == event.questionRpcId) removeInteraction(key)
            }
            DshConnection.Event.InteractionsInvalidated -> clearInteractions()
            is DshConnection.Event.SessionRemoved -> pending.keys.filter { it.first == event.sessionId }.forEach(::removeInteraction)
            is DshConnection.Event.ApprovalRequested, is DshConnection.Event.QuestionRequested -> {
                val item = Interaction.from(event) ?: return
                if (pending[item.key]?.rpc == item.rpc) return
                removeInteraction(item.key)
                val settings = SettingsStore(this)
                if (!shouldNotifyApproval(settings.backgroundNotify.first(), settings.notifyApprovals.first(),
                        DshApplication.isAppInForeground)) return
                val text = if (event is DshConnection.Event.ApprovalRequested)
                    "工具「" + event.toolName + "」请求执行权限" + (event.reason?.let { "：" + it } ?: "")
                    else item.question?.question.orEmpty()
                postInteraction(item, text, !settings.biometricLockEnabled.first())
            }
            is DshConnection.Event.SessionEvent -> if (!DshApplication.isAppInForeground)
                TokenUsageWatcher.onSessionEvent(event.sessionId, event.event)

            is DshConnection.Event.SessionStatus ->
                updateActivity(event.sessionId, event.status == "running")

            is DshConnection.Event.Jobs ->
                updateActivity(event.sessionId, event.jobs.any { it.status == "running" || it.status == "stopping" })

            else -> {}
        }
    }

    private fun postInteraction(item: Interaction, text: String, actionsEnabled: Boolean) {
        pending[item.key] = item
        postAlert(if (item.approval != null) "DeepSeek Harness 需要你的审批" else "DeepSeek Harness 需要你的确认",
            text, item.session, interaction = item, actionsEnabled = actionsEnabled)
        WidgetState.pushPending(this, pending.size)
    }

    private suspend fun processAction(intent: Intent) {
        val path = intent.data?.pathSegments ?: return
        if (path.size != 2) return
        val item = pending.values.firstOrNull { it.nonce == path[0] } ?: return
        val connection = watcher ?: return
        val profile = connection.currentProfile() ?: return
        val settings = SettingsStore(this)
        val enabled = settings.backgroundNotify.first() && settings.notifyApprovals.first() &&
            !settings.biometricLockEnabled.first() && settings.activeProfileId.first() == profile.id
        if (pending[item.key] !== item || watcher !== connection) return
        if (!enabled || connection.state.value !is DshConnection.State.Connected) {
            removeInteraction(item.key)
            return
        }
        if (getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked) return
        val operation = path[1]
        val answered = when (operation) {
            "allow" -> if (item.approval != null && !item.highRisk) null else return
            "reject" -> if (item.approval != null) null else return
            "confirm" -> item.finishSelection() ?: return
            "reply" -> item.withReply(android.app.RemoteInput.getResultsFromIntent(intent)
                ?.getCharSequence(REPLY_TEXT)?.toString() ?: return) ?: run {
                android.widget.Toast.makeText(this, "请输入有效回答", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            else -> return
        }
        removeInteraction(item.key)
        val next = answered?.question
        if (answered != null && next != null) {
            val text = (listOf(next.question) + answered.selected).joinToString("\n")
            postInteraction(answered, text, true)
            return
        }
        try {
            if (item.approval != null) connection.answerApproval(item.session, item.approval,
                if (operation == "allow") "allowed-once" else "rejected", item.rpc)
            else if (answered != null) connection.answerQuestions(item.session, answered.answers, item.rpc)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            android.widget.Toast.makeText(this, "无法确认处理结果，请查看待办", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /** 运行态跟踪：进入运行清掉完成防抖；转空闲后 8 秒确认（避免任务间空隙误报）再通知完成 */
    private fun updateActivity(sessionId: String, active: Boolean) {
        val prev = sessionActive[sessionId] ?: false
        sessionActive[sessionId] = active
        if (active) {
            completionJobs.remove(sessionId)?.cancel()
        } else if (prev) {
            val job = scope.launch {
                delay(8_000)
                if (sessionActive[sessionId] != true) {
                    // 分渠道开关 + 前台判定在防抖到期瞬间读取（期间用户可能已打开 App）
                    val settings = SettingsStore(this@DshConnectionService)
                    if (!shouldNotifyCompletion(
                            settings.backgroundNotify.first(),
                            settings.notifyCompletion.first(),
                            DshApplication.isAppInForeground,
                        )
                    ) return@launch
                    postAlert(
                        "DeepSeek Harness 任务完成",
                        "会话 " + shortSessionId(sessionId) + " 的执行已结束",
                        sessionId,
                        channelId = DshApplication.CHANNEL_COMPLETION,
                    )
                }
            }
            completionJobs[sessionId] = job
        }
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    private fun postAlert(
        title: String,
        text: String,
        sessionId: String? = null,
        channelId: String = DshApplication.CHANNEL_APPROVALS,
        interaction: Interaction? = null,
        actionsEnabled: Boolean = false,
    ) {
        val manager = notificationManager()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (sessionId != null) {
                putExtra("open_session", sessionId)
                putExtra("open_pending", interaction != null)
                if (interaction != null) data = android.net.Uri.parse("dsh-notification://open/" + interaction.nonce)
                putExtra("open_profile", watcher?.currentProfile()?.id)
                putExtra("open_computer", watcher?.currentProfile().computerScope())
            }
        }
        val notification = Notification.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(com.dsh.mobile.R.drawable.ic_notification_dsh)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_HIGH)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, (watcher?.currentProfile().computerScope() + sessionId).hashCode() and Int.MAX_VALUE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .apply { if (actionsEnabled) interaction?.actions(this@DshConnectionService)?.forEach { addAction(it) } }
            .build()
        if (interaction != null) manager.notify("$pendingTag/${interaction.nonce}", 0, notification)
        else manager.notify(1000 + notificationSeq++, notification)
    }

    override fun onDestroy() {
        eventsJob?.cancel()
        stateJob?.cancel()
        watcher?.disconnect()
        watcher = null
        watchJob?.cancel()
        watchJob = null
        clearInteractions()
        scope.cancel()
        super.onDestroy()
    }
}
