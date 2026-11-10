package com.dsh.mobile.data

/**
 * S4 T1 通知渠道精细化：分渠道放行策略（纯函数，供单测与服务接线复用）。
 *
 * 语义：总开关 && 分渠道开关 && 不在前台 → 放行，其余一律不放行。
 */

/** 审批/问答横幅是否放行：总开关 && 分渠道开关 && 不在前台 */
internal fun shouldNotifyApproval(
    backgroundNotify: Boolean,
    notifyApprovals: Boolean,
    appInForeground: Boolean,
): Boolean = backgroundNotify && notifyApprovals && !appInForeground

/** 完成提醒是否放行：总开关 && 分渠道开关 && 不在前台 */
internal fun shouldNotifyCompletion(
    backgroundNotify: Boolean,
    notifyCompletion: Boolean,
    appInForeground: Boolean,
): Boolean = backgroundNotify && notifyCompletion && !appInForeground

// ──────────────────────────── 通知渠道诊断 ────────────────────────────
//
// 国产 ROM 常把渠道改成静音/不弹横幅，用户只会觉得「收不到提醒」。这里读系统里渠道的实际设置，
// 指出具体是哪一项、该去哪里改。重要程度沿用 NotificationManager 的取值：0 关闭、2 低、3 默认、4 高。

/** 系统里一个通知渠道的实际设置 */
data class ChannelState(
    val id: String,
    val label: String,
    /** null：渠道还没创建（通常是还没授予通知权限） */
    val importance: Int?,
    val hasSound: Boolean,
    /** 需要的最低重要程度：审批要弹横幅（4），完成提醒要响铃（3） */
    val requiredImportance: Int,
)

/** 一条问题和去哪里修：channelId 为 null 时打开应用的通知设置 */
data class NotificationProblem(val message: String, val channelId: String?)

private const val IMPORTANCE_NONE = 0
private const val IMPORTANCE_DEFAULT = 3

fun notificationProblems(
    permissionGranted: Boolean,
    appNotificationsEnabled: Boolean,
    channels: List<ChannelState>,
): List<NotificationProblem> {
    if (!permissionGranted) return listOf(NotificationProblem("没有通知权限，审批、确认和完成都不会提醒", null))
    if (!appNotificationsEnabled) return listOf(NotificationProblem("系统关闭了本应用的通知", null))
    return channels.mapNotNull { c ->
        val importance = c.importance
        when {
            importance == null -> NotificationProblem("「${c.label}」渠道还没建立，重新打开 App 后再看", null)
            importance == IMPORTANCE_NONE -> NotificationProblem("「${c.label}」已被关闭", c.id)
            importance < c.requiredImportance ->
                NotificationProblem(
                    if (c.requiredImportance > IMPORTANCE_DEFAULT) "「${c.label}」不会弹出横幅，需要把重要程度调到最高"
                    else "「${c.label}」不会响铃，需要把重要程度调到默认或更高",
                    c.id,
                )
            !c.hasSound -> NotificationProblem("「${c.label}」没有提示音", c.id)
            else -> null
        }
    }
}
