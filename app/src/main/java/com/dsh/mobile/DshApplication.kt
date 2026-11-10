package com.dsh.mobile

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.dsh.mobile.data.ApprovalCenter
import com.dsh.mobile.data.DshConnection
import com.dsh.mobile.data.HistoryCache
import com.dsh.mobile.data.PairingCoordinator
import com.dsh.mobile.data.ProTokenBank
import com.dsh.mobile.data.SecretCipher
import com.dsh.mobile.data.SettingsStore
import com.dsh.mobile.data.UpdateChecker
import kotlinx.coroutines.*

interface ConnectionOwner {
    val connection: DshConnection
}

class DshApplication : Application(), ConnectionOwner {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override val connection = DshConnection(this)
    val approvalCenter by lazy { ApprovalCenter(connection, appScope) }
    val pairingCoordinator by lazy { PairingCoordinator(connection, SettingsStore(this), appScope) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        // 启动时清理过期会话缓存（7 天前历史、1 天前会话列表），防磁盘膨胀
        appScope.launch(Dispatchers.IO) {
            HistoryCache(this@DshApplication).prune()
            val installedAt = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
            UpdateChecker.pruneDownloads(java.io.File(cacheDir, "update"), installedAt)
        }
        // 加密主密钥初始化（凭证密文存储，幂等）
        SecretCipher.init(this)
        // 假 Pro 订阅银行初始化（读持久化状态）
        ProTokenBank.init(this)
        // 更新镜像偏好初始化（记住上次成功的镜像，下次优先）
        UpdateChecker.init(this)
        // 配对握手协调器启动（Connected 后按活跃 profile 自动握手）
        pairingCoordinator.start()
        // 注：资源更新检查在 MainActivity 的 UpdatePromptOverlay 里做（有更新弹窗询问，无更新静默）
    }

    private fun createNotificationChannels() = ensureNotificationChannels(this)

    companion object {
        const val CHANNEL_ID = "dsh_task_updates"
        /**
         * 渠道一经创建，重要程度、铃声等就以系统里的为准，代码改不动；要换默认设置只能换 ID。
         * v2：显式设置提示音和振动（部分国产 ROM 不给默认值），并且只在有通知权限后创建。
         */
        const val CHANNEL_APPROVALS = "dsh_approval_alerts_v2"
        const val CHANNEL_COMPLETION = "dsh_completion_alerts_v2"
        private val RETIRED_CHANNELS = listOf("dsh_approval_alerts", "dsh_completion_alerts")

        /**
         * 建立通知渠道（幂等）。常驻连接渠道总是建立：前台服务启动必须有它。
         * 会响铃的渠道等拿到通知权限后再建：ColorOS 等会把没有权限时建立的渠道存成静音。
         * 授权回调和服务启动时都会再调用一次。
         */
        fun ensureNotificationChannels(context: android.content.Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = context.getString(R.string.channel_desc) },
            )
            val granted = android.os.Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
            RETIRED_CHANNELS.forEach { manager.deleteNotificationChannel(it) }
            val sound = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            val audio = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            fun NotificationChannel.audible(pattern: LongArray) = apply {
                setSound(sound, audio)
                enableVibration(true)
                vibrationPattern = pattern
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_APPROVALS, "审批与确认提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "桌面端需要审批或确认时的高优先级横幅提醒"
                }.audible(longArrayOf(0, 250, 150, 250)),
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_COMPLETION, "任务完成提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "会话任务结束时的提醒（独立渠道，可与审批提醒分别设置铃声/勿扰）"
                }.audible(longArrayOf(0, 200)),
            )
        }

        /** 读取两个会响铃渠道的实际设置，供设置页诊断 */
        fun channelStates(context: android.content.Context): List<com.dsh.mobile.data.ChannelState> {
            val manager = context.getSystemService(NotificationManager::class.java)
            return listOf(
                Triple(CHANNEL_APPROVALS, "审批与确认提醒", NotificationManager.IMPORTANCE_HIGH),
                Triple(CHANNEL_COMPLETION, "任务完成提醒", NotificationManager.IMPORTANCE_DEFAULT),
            ).map { (id, label, required) ->
                val channel = manager.getNotificationChannel(id)
                com.dsh.mobile.data.ChannelState(id, label, channel?.importance, channel?.sound != null, required)
            }
        }

        /** App 是否在前台（前台时审批横幅已可见，服务不再重复通知） */
        @Volatile
        var isAppInForeground = false

        /** 通知点击后要打开的会话（由 MainActivity/AppNavigation 消费） */
        data class SessionLink(val sessionId: String, val profileId: String?, val computer: String?, val openPending: Boolean = false)
        val pendingSession = kotlinx.coroutines.flow.MutableStateFlow<SessionLink?>(null)
    }
}
