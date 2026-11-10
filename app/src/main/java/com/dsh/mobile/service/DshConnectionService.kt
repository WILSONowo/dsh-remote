package com.dsh.mobile.service

import kotlinx.coroutines.flow.combine
import com.dsh.mobile.data.shortSessionId
import com.dsh.mobile.data.SessionEventWire
import com.dsh.mobile.data.TurnReport
import com.dsh.mobile.data.sessionTitleOf
import com.dsh.mobile.data.shouldNotify
import com.dsh.mobile.data.turnReportBody
import com.dsh.mobile.data.turnReportHeadline
import com.dsh.mobile.data.turnReportOf
import com.dsh.mobile.data.CommandRisk
import com.dsh.mobile.data.RiskLevel
import com.dsh.mobile.data.toolCallArguments
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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
import com.dsh.mobile.data.connectionUrl
import com.dsh.mobile.ConnectionOwner
import kotlinx.coroutines.CoroutineStart
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
 * 前台服务：监听应用当前连接的事件。
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
    private var notificationSeq = 0

    /** 会话活跃状态（运行中→空闲迁移触发完成通知） */
    private val sessionActive = mutableMapOf<String, Boolean>()
    private val completionJobs = mutableMapOf<String, Job>()
    private val pending = mutableMapOf<Pair<String, String?>, Interaction>()
    private val processingNonces = mutableSetOf<String>()
    private val pendingTag = "dsh-interaction"
    private val runningTag = "dsh-running"

    /**
     * 断线时还没处理的待办：通知先留着。重连后网关会把仍在等待的请求重发一遍，
     * 重发到的原地换上新的 rpc、不再响铃；重连 5 秒后仍没重发的，说明已在别处处理，才撤掉。
     */
    private val staleKeys = mutableSetOf<Pair<String, String?>>()
    private var staleJob: Job? = null

    /** 断线期间点下的审批决定：同一审批重连后重发时自动提交（审批 ID 跨连接不变），过期则提示去待办处理 */
    private data class Decision(val outcome: String, val expiresAt: Long)
    private val decisions = mutableMapOf<Pair<String, String>, Decision>()
    private val decisionJobs = mutableMapOf<Pair<String, String>, Job>()

    private fun removeInteraction(key: Pair<String, String?>) {
        staleKeys.remove(key)
        val item = pending.remove(key) ?: return
        notificationManager().cancel("$pendingTag/${item.nonce}", 0)
        WidgetState.pushPending(this, pending.size)
    }

    private fun clearInteractions() {
        staleJob?.cancel()
        staleJob = null
        pending.keys.toList().forEach(::removeInteraction)
        decisionJobs.values.forEach { it.cancel() }
        decisionJobs.clear()
        decisions.clear()
    }

    private fun markStale() {
        if (pending.isEmpty()) return
        staleKeys += pending.keys
        if (staleJob?.isActive == true) return
        val connection = watcher ?: return clearInteractions()
        staleJob = scope.launch {
            withTimeoutOrNull(STALE_RECONNECT_MS) { connection.state.first { it is DshConnection.State.Connected } }
            delay(STALE_GRACE_MS)
            staleKeys.toList().forEach(::removeInteraction)
            staleKeys.clear()
        }
    }

    /** 记下审批决定，等重连后重发时提交；超时没等到就提醒用户去待办处理 */
    private fun deferDecision(item: Interaction, outcome: String, expiresAt: Long = System.currentTimeMillis() + DECISION_TTL_MS) {
        val approval = item.approval ?: return
        val key = item.session to approval
        removeInteraction(item.key)
        decisions[key] = Decision(outcome, expiresAt)
        decisionJobs.remove(key)?.cancel()
        decisionJobs[key] = scope.launch {
            delay((expiresAt - System.currentTimeMillis()).coerceAtLeast(0))
            decisionJobs.remove(key)
            if (decisions.remove(key) != null) {
                postAlert("审批没有送达", "连接一直没有恢复，请打开 App 在待办里重新处理", item.session)
            }
        }
    }

    private suspend fun submitDecision(connection: DshConnection, item: Interaction, decision: Decision) {
        val approval = item.approval ?: return
        val key = item.session to approval
        decisions.remove(key)
        decisionJobs.remove(key)?.cancel()
        removeInteraction(item.key)
        try {
            connection.answerApproval(item.session, approval, decision.outcome, item.rpc)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            if (decision.expiresAt > System.currentTimeMillis()) deferDecision(item, decision.outcome, decision.expiresAt)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        DshApplication.ensureNotificationChannels(this)
        val manager = notificationManager()
        manager.activeNotifications.filter { it.tag?.startsWith("$pendingTag/") == true || it.tag?.startsWith("$runningTag/") == true }
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
        if (intent?.action == STOP_SESSION) {
            val sessionId = intent.data?.lastPathSegment
            val connection = watcher
            if (sessionId == null || connection == null) {
                if (watchJob == null) stopSelf(startId)
                return START_NOT_STICKY
            }
            scope.launch { stopSession(connection, sessionId) }
            return START_STICKY
        }
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
            val connection = (application as ConnectionOwner).connection
            combine(settings.activeProfileId, settings.profiles) { activeId, profiles ->
                profiles.firstOrNull { it.id == activeId && it.paired }
            }.distinctUntilChanged { old, new -> old?.id == new?.id }.collect { profile ->
                eventsJob?.cancel()
                stateJob?.cancel()
                watcher = null
                clearInteractions()
                sessionActive.keys.toList().forEach(::cancelRunning)
                sessionActive.clear()
                completionJobs.values.forEach { it.cancel() }
                completionJobs.clear()

                if (profile == null || runCatching { profile.connectionUrl() }.isFailure) {
                    updateForegroundText("未选择活跃主机")
                    return@collect
                }
                watcher = connection
                eventsJob = launch(start = CoroutineStart.UNDISPATCHED) {
                    connection.events.collect {
                        if (connection.currentProfile()?.id == profile.id) handle(it)
                    }
                }
                stateJob = launch {
                    connection.state.collect { st ->
                        if (st !is DshConnection.State.Connected) markStale()
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
                // A sticky service may be the first component after process death.
                if (connection.state.value == DshConnection.State.Disconnected &&
                    connection.currentProfile() == null) connection.connect(profile) { info ->
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
            is DshConnection.Event.ApprovalResolved -> {
                val key = event.sessionId to event.approvalId
                decisions.remove(key)
                decisionJobs.remove(key)?.cancel()
                removeInteraction(key)
            }
            is DshConnection.Event.QuestionResolved -> {
                val key = event.sessionId to null
                if (event.questionRpcId.isEmpty() || pending[key]?.rpc == event.questionRpcId) removeInteraction(key)
            }
            DshConnection.Event.InteractionsInvalidated -> markStale()
            is DshConnection.Event.SessionRemoved -> pending.keys.filter { it.first == event.sessionId }.forEach(::removeInteraction)
            is DshConnection.Event.ApprovalRequested, is DshConnection.Event.QuestionRequested -> {
                val item = Interaction.from(event) ?: return
                // 断线期间已经点过的审批：重发到了就直接提交
                if (event is DshConnection.Event.ApprovalRequested) {
                    val decision = decisions[event.sessionId to event.approvalId]
                    val connection = watcher
                    if (decision != null && connection != null) {
                        submitDecision(connection, item, decision)
                        return
                    }
                }
                val existing = pending[item.key]
                if (existing?.rpc == item.rpc) return
                if (existing != null && item.key in staleKeys && existing.questions == item.questions) {
                    // 重连后重发的同一请求：换上新 rpc，原通知原地更新，不再响铃；问答保留已答的进度
                    staleKeys.remove(item.key)
                    val refreshed = existing.copy(rpc = item.rpc)
                    pending[item.key] = refreshed
                    val settings = SettingsStore(this)
                    postAlert(interactionTitle(refreshed), refreshed.text, refreshed.session, interaction = refreshed,
                        actionsEnabled = !settings.biometricLockEnabled.first(), silent = true)
                    return
                }
                removeInteraction(item.key)
                val settings = SettingsStore(this)
                if (!shouldNotifyApproval(settings.backgroundNotify.first(), settings.notifyApprovals.first(),
                        DshApplication.isAppInForeground)) return
                if (event is DshConnection.Event.ApprovalRequested) {
                    // 审批事件不带命令：到最近的历史里查对应工具调用的参数，评估风险并显示要执行什么。
                    // 查询限时，超时就按工具名评估；期间到达的「已处理」事件会在随后撤掉这条通知
                    val arguments = event.callId?.let { callId ->
                        watcher?.let { c ->
                            runCatching { withTimeoutOrNull(1_500) { c.toolCallArguments(event.sessionId, callId) } }.getOrNull()
                        }
                    }
                    val risk = CommandRisk.assess(event.toolName, event.reason, arguments)
                    val text = buildString {
                        append("工具「").append(event.toolName).append("」请求执行权限")
                        event.reason?.let { append("：").append(it) }
                        risk.subject?.let { append("\n").append(it) }
                        if (risk.findings.isNotEmpty()) append("\n⚠ ").append(risk.findings.joinToString(" · "))
                    }
                    postInteraction(item.copy(highRisk = risk.level == RiskLevel.HIGH), text, !settings.biometricLockEnabled.first())
                } else {
                    postInteraction(item, item.question?.question.orEmpty(), !settings.biometricLockEnabled.first())
                }
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

    private fun interactionTitle(item: Interaction) =
        if (item.approval != null) "DeepSeek Harness 需要你的审批" else "DeepSeek Harness 需要你的确认"

    private fun postInteraction(item: Interaction, text: String, actionsEnabled: Boolean) {
        val stored = item.copy(text = text)
        pending[stored.key] = stored
        postAlert(interactionTitle(stored), text, stored.session, interaction = stored, actionsEnabled = actionsEnabled)
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
        if (!enabled) {
            removeInteraction(item.key)
            return
        }
        if (getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked) return
        val operation = path[1]
        val disconnected = connection.state.value !is DshConnection.State.Connected || item.key in staleKeys
        if (disconnected) {
            // 断线中：审批先记下，重连后自动提交；问答需要逐题作答，等连上再答
            if (item.approval != null && (operation == "reject" || (operation == "allow" && !item.highRisk))) {
                deferDecision(item, if (operation == "allow") "allowed-once" else "rejected")
                android.widget.Toast.makeText(this, "已记下你的选择，重新连接后自动提交", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(this, "连接已断开，重新连接后再回答", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }
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
            if (item.approval != null) {
                // 可能是连接刚好断了：记下决定，重连后同一审批重发时自动提交；
                // 若其实已送达，桌面端会发来「已处理」，记下的决定随之作废
                deferDecision(item, if (operation == "allow") "allowed-once" else "rejected")
                android.widget.Toast.makeText(this, "暂时没送达，重新连接后自动提交", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                postAlert("回答没有送达", "请打开 App 在待办里重新回答", item.session)
            }
        }
    }

    /**
     * 运行态跟踪：进入运行时清掉完成防抖，并在后台显示「正在运行」常驻通知（带停止按钮）；
     * 转空闲后 8 秒确认（避免任务间空隙误报），再读本轮历史发出结果报告。
     */
    private fun updateActivity(sessionId: String, active: Boolean) {
        val prev = sessionActive[sessionId] ?: false
        sessionActive[sessionId] = active
        if (active) {
            completionJobs.remove(sessionId)?.cancel()
            if (!prev) scope.launch { postRunning(sessionId) }
        } else if (prev) {
            val job = scope.launch {
                delay(8_000)
                if (sessionActive[sessionId] == true) return@launch
                cancelRunning(sessionId)
                // 分渠道开关 + 前台判定在防抖到期瞬间读取（期间用户可能已打开 App）
                if (!completionAllowed()) return@launch
                val report = watcher?.let { fetchTurnReport(it, sessionId) }
                if (sessionActive[sessionId] == true || !completionAllowed()) return@launch
                if (report != null && !report.shouldNotify) return@launch
                val name = report?.title ?: cachedTitle(sessionId) ?: ("会话 " + shortSessionId(sessionId))
                val headline = report?.let(::turnReportHeadline) ?: "任务完成"
                postAlert(
                    "$headline · $name",
                    report?.let(::turnReportBody)?.ifBlank { null } ?: "执行已结束",
                    sessionId,
                    channelId = DshApplication.CHANNEL_COMPLETION,
                    publicText = headline,
                )
            }
            completionJobs[sessionId] = job
        }
    }

    private suspend fun completionAllowed(): Boolean {
        val settings = SettingsStore(this)
        return shouldNotifyCompletion(
            settings.backgroundNotify.first(),
            settings.notifyCompletion.first(),
            DshApplication.isAppInForeground,
        )
    }

    private fun cachedTitle(sessionId: String): String? =
        watcher?.directory?.sessions?.state?.value?.value
            ?.firstOrNull { it.sessionId == sessionId }
            ?.let(::sessionTitleOf)

    /** 读最近几页历史，直到找到本轮的 turn/start（最多 4 页）；失败返回 null，退回简单提醒 */
    private suspend fun fetchTurnReport(connection: DshConnection, sessionId: String): TurnReport? = try {
        withTimeout(15_000) {
            val events = ArrayList<SessionEventWire>()
            var before: Long? = null
            var report: TurnReport? = null
            for (page in 0 until 4) {
                val history = connection.history(sessionId, beforeSeq = before, maxMessages = 15)
                events.addAll(0, history.events.map { it.event })
                report = turnReportOf(events)
                if (report == null || report.complete || !history.hasMore) break
                before = history.events.firstOrNull()?.event?.seq ?: break
            }
            report
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        if (e is kotlinx.coroutines.TimeoutCancellationException) null else throw e
    } catch (_: Exception) {
        null
    }

    /** 后台运行中的会话：常驻、静默、带计时和「停止」按钮；锁屏只显示「正在运行」 */
    private suspend fun postRunning(sessionId: String) {
        if (!completionAllowed() || sessionActive[sessionId] != true) return
        val name = cachedTitle(sessionId) ?: ("会话 " + shortSessionId(sessionId))
        val stop = Intent(this, DshConnectionService::class.java).setAction(STOP_SESSION)
            .setData(android.net.Uri.Builder().scheme("dsh-notification").authority("stop")
                .appendPath(sessionId).build())
        val stopAction = Notification.Action.Builder(
            null, "停止",
            PendingIntent.getService(this, ("stop/$sessionId").hashCode(), stop, PendingIntent.FLAG_IMMUTABLE),
        ).apply { if (android.os.Build.VERSION.SDK_INT >= 31) setAuthenticationRequired(true) }.build()
        val publicVersion = Notification.Builder(this, DshApplication.CHANNEL_ID)
            .setContentTitle("DeepSeek Harness 正在运行")
            .setSmallIcon(com.dsh.mobile.R.drawable.ic_notification_dsh)
            .build()
        val notification = Notification.Builder(this, DshApplication.CHANNEL_ID)
            .setContentTitle("正在运行 · $name")
            .setContentText("可在这里停止本轮")
            .setSmallIcon(com.dsh.mobile.R.drawable.ic_notification_dsh)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setUsesChronometer(true)
            .setProgress(0, 0, true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(openSessionIntent(sessionId, null))
            .addAction(stopAction)
            .build()
        notificationManager().notify("$runningTag/$sessionId", 0, notification)
    }

    private fun cancelRunning(sessionId: String) {
        notificationManager().cancel("$runningTag/$sessionId", 0)
    }

    private suspend fun stopSession(connection: DshConnection, sessionId: String) {
        if (getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked) return
        try {
            connection.cancel(sessionId)
            android.widget.Toast.makeText(this, "已请求停止，等待本轮结束", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "停止失败：" + (e.message ?: "连接异常"), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    /** 点通知打开对应会话（有待办时直达待办） */
    private fun openSessionIntent(sessionId: String?, interaction: Interaction?): PendingIntent {
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
        return PendingIntent.getActivity(
            this, (watcher?.currentProfile().computerScope() + sessionId).hashCode() and Int.MAX_VALUE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun postAlert(
        title: String,
        text: String,
        sessionId: String? = null,
        channelId: String = DshApplication.CHANNEL_APPROVALS,
        interaction: Interaction? = null,
        actionsEnabled: Boolean = false,
        /** 锁屏上代替正文显示的文字；null 表示正文可以在锁屏显示 */
        publicText: String? = null,
        /** 原地更新已有通知时不再响铃 */
        silent: Boolean = false,
    ) {
        val manager = notificationManager()
        val notification = Notification.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(com.dsh.mobile.R.drawable.ic_notification_dsh)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_HIGH)
            .setOnlyAlertOnce(silent)
            .setContentIntent(openSessionIntent(sessionId, interaction))
            .apply {
                if (actionsEnabled) interaction?.actions(this@DshConnectionService)?.forEach { addAction(it) }
                if (publicText != null) {
                    setVisibility(Notification.VISIBILITY_PRIVATE)
                    setPublicVersion(
                        Notification.Builder(this@DshConnectionService, channelId)
                            .setContentTitle(publicText)
                            .setSmallIcon(com.dsh.mobile.R.drawable.ic_notification_dsh)
                            .build(),
                    )
                }
            }
            .build()
        if (interaction != null) manager.notify("$pendingTag/${interaction.nonce}", 0, notification)
        else manager.notify(1000 + notificationSeq++, notification)
    }

    override fun onDestroy() {
        eventsJob?.cancel()
        stateJob?.cancel()
        watcher = null
        watchJob?.cancel()
        watchJob = null
        clearInteractions()
        sessionActive.keys.toList().forEach(::cancelRunning)
        scope.cancel()
        super.onDestroy()
    }
}

/** 断线后最多等多久重新连上；超过就把留着的待办通知撤掉 */
private const val STALE_RECONNECT_MS = 120_000L
/** 重连后等网关重发仍在等待的请求的时间 */
private const val STALE_GRACE_MS = 5_000L
/** 断线期间点下的审批决定保留多久 */
private const val DECISION_TTL_MS = 5 * 60_000L
