package com.dsh.mobile.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * 一轮结束时的报告：从 session.history 末尾的事件推出结果、用时、工具次数和计划进度，
 * 用于后台的「任务完成」通知。
 *
 * 事件形状来自 DSH 的 SessionEventMap：
 * - `turn/start` `{turn}`、`turn/end` `{turn, reason}`，reason.kind 为
 *   completed / aborted / blocked / error / max-tokens / interrupted / forked；
 * - `tool/call` `{callId, name, arguments}`，`tool/result` 的 tool-result 块带 isError，或 data.error；
 * - `todo/write` `{todos: [{content, status: pending|in_progress|completed}]}`。
 */
enum class TurnOutcome { COMPLETED, ABORTED, BLOCKED, ERROR, MAX_TOKENS, INTERRUPTED, FORKED, UNKNOWN }

data class TurnReport(
    val outcome: TurnOutcome,
    /** 用户主动停止（aborted 且原因是 user）：不提醒 */
    val stoppedByUser: Boolean,
    /** null：这一页历史里找不到对应的 turn/start */
    val durationMs: Long?,
    val toolCalls: Int,
    val failedTools: Int,
    /** 找到了 turn/start：次数是完整的；否则只是这页里看到的部分 */
    val complete: Boolean,
    val todoDone: Int?,
    val todoTotal: Int?,
    /** 本轮最后一段助手正文 */
    val reply: String,
    val errorMessage: String?,
    val title: String?,
)

/** 是否应该提醒：用户自己停止、分叉出去的轮次不提醒，其余都提醒（包括出错和 DSH 意外退出） */
val TurnReport.shouldNotify: Boolean
    get() = !stoppedByUser && outcome != TurnOutcome.FORKED

private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun outcomeOf(kind: String?): TurnOutcome = when (kind) {
    "completed" -> TurnOutcome.COMPLETED
    "aborted" -> TurnOutcome.ABORTED
    "blocked" -> TurnOutcome.BLOCKED
    "error" -> TurnOutcome.ERROR
    "max-tokens" -> TurnOutcome.MAX_TOKENS
    "interrupted" -> TurnOutcome.INTERRUPTED
    "forked" -> TurnOutcome.FORKED
    else -> TurnOutcome.UNKNOWN
}

/** assistant/message 的可见正文：只取 type == "text" 的块（reasoning 块同样有 text 字段，要排除） */
internal fun assistantVisibleText(data: JsonElement): String {
    val message = data.obj()?.get("message").obj() ?: return ""
    val content = message["content"] as? JsonArray ?: return ""
    return content.mapNotNull { block ->
        val o = block.obj() ?: return@mapNotNull null
        if (o["type"].str() == "text") o["text"].str() else null
    }.filter { it.isNotBlank() }.joinToString("\n")
}

private fun toolResultFailed(data: JsonElement): Boolean {
    val d = data.obj() ?: return false
    if (d["error"] is JsonObject) return true
    val content = d["message"].obj()?.get("content") as? JsonArray ?: return false
    return content.any { block ->
        val o = block.obj()
        o?.get("type").str() == "tool-result" && (o?.get("isError") as? JsonPrimitive)?.booleanOrNull == true
    }
}

/**
 * 取最后一个 turn/end 所在的轮次生成报告；没有 turn/end 返回 null。
 * events 需按 seq 升序（session.history 的顺序）。
 */
fun turnReportOf(events: List<SessionEventWire>): TurnReport? {
    val endIndex = events.indexOfLast { it.type == DshEventTypes.TURN_END }
    if (endIndex < 0) return null
    val end = events[endIndex]
    val endData = end.data.obj()
    val turn = (endData?.get("turn") as? JsonPrimitive)?.longOrNull
    val reason = endData?.get("reason").obj()
    val outcome = outcomeOf(reason?.get("kind").str())
    val stoppedByUser = outcome == TurnOutcome.ABORTED &&
        reason?.get("reason").obj()?.get("kind").str().let { it == null || it == "user" }

    val startIndex = (endIndex - 1 downTo 0).firstOrNull { i ->
        val e = events[i]
        e.type == DshEventTypes.TURN_START &&
            (turn == null || (e.data.obj()?.get("turn") as? JsonPrimitive)?.longOrNull == turn)
    }
    // 另一轮的 turn/end 之后的事件不属于本轮
    val previousEnd = (endIndex - 1 downTo 0).firstOrNull { events[it].type == DshEventTypes.TURN_END } ?: -1
    val from = startIndex ?: (previousEnd + 1)
    val range = events.subList(from, endIndex + 1)

    var toolCalls = 0
    var failed = 0
    var todos: JsonArray? = null
    var reply = ""
    for (e in range) {
        when (e.type) {
            DshEventTypes.TOOL_CALL -> toolCalls++
            DshEventTypes.TOOL_RESULT -> if (toolResultFailed(e.data)) failed++
            DshEventTypes.TODO_WRITE -> todos = e.data.obj()?.get("todos") as? JsonArray ?: todos
            DshEventTypes.ASSISTANT_MESSAGE -> assistantVisibleText(e.data).takeIf { it.isNotBlank() }?.let { reply = it }
        }
    }
    val statuses = todos?.mapNotNull { it.obj()?.get("status").str() }
    val title = events.lastOrNull { it.type == DshEventTypes.SESSION_TITLE }?.data?.obj()?.get("title").str()
        ?.takeIf { it.isNotBlank() }
    val errorMessage = if (outcome == TurnOutcome.ERROR) {
        reason?.get("error").obj()?.get("message").str()
    } else null

    return TurnReport(
        outcome = outcome,
        stoppedByUser = stoppedByUser,
        durationMs = startIndex?.let { (end.time - events[it].time).takeIf { d -> d >= 0 } },
        toolCalls = toolCalls,
        failedTools = failed,
        complete = startIndex != null,
        todoDone = statuses?.count { it == "completed" },
        todoTotal = statuses?.size,
        reply = reply,
        errorMessage = errorMessage,
        title = title,
    )
}

/** 「3 分 12 秒」「45 秒」「1 小时 5 分」 */
fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return when {
        h > 0 -> "$h 小时 $m 分"
        m > 0 -> "$m 分 $s 秒"
        else -> "$s 秒"
    }
}

/** 通知标题：说明结果 */
fun turnReportHeadline(report: TurnReport): String = when (report.outcome) {
    TurnOutcome.COMPLETED, TurnOutcome.UNKNOWN -> "任务完成"
    TurnOutcome.MAX_TOKENS -> "输出达到长度上限"
    TurnOutcome.ERROR -> "任务出错"
    TurnOutcome.BLOCKED -> "任务被拦截"
    TurnOutcome.INTERRUPTED -> "DSH 意外退出，任务中断"
    TurnOutcome.ABORTED -> "任务已停止"
    TurnOutcome.FORKED -> "任务已分叉"
}

/** 通知里的统计行：用时 · 工具次数（失败数）· 计划进度 */
fun turnReportStats(report: TurnReport): String = buildList {
    report.durationMs?.let { add("用时 " + formatDuration(it)) }
    if (report.toolCalls > 0) {
        val count = (if (report.complete) "" else "至少") + "调用 ${report.toolCalls} 次工具"
        add(if (report.failedTools > 0) "$count（${report.failedTools} 次失败）" else count)
    }
    val total = report.todoTotal
    if (total != null && total > 0) add("计划 ${report.todoDone ?: 0}/$total")
}.joinToString(" · ")

/** 通知正文上限：系统展开态能显示的长度有限，超出明确标出截断 */
const val TURN_REPORT_REPLY_CHARS = 600

/** 通知正文：统计行 + 错误信息或回复开头 */
fun turnReportBody(report: TurnReport): String {
    val detail = report.errorMessage?.takeIf { it.isNotBlank() } ?: report.reply.trim()
    val clipped = if (detail.length > TURN_REPORT_REPLY_CHARS) detail.take(TURN_REPORT_REPLY_CHARS).trimEnd() + "…（未完）" else detail
    return listOf(turnReportStats(report), clipped).filter { it.isNotBlank() }.joinToString("\n")
}
