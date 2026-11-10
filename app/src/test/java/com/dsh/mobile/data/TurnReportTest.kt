package com.dsh.mobile.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnReportTest {

    private val json = Json { ignoreUnknownKeys = true }
    private var seq = 0L

    private fun ev(type: String, time: Long, data: String = "{}") =
        SessionEventWire(type = type, seq = seq++, time = time, data = json.parseToJsonElement(data))

    private fun turn(n: Int, start: Long, reason: String, vararg middle: SessionEventWire) = listOf(
        ev("turn/start", start, """{"turn":$n}"""),
        *middle,
        ev("turn/end", start + 192_000, """{"turn":$n,"reason":$reason}"""),
    )

    private fun reply(text: String, time: Long = 1) = ev(
        "assistant/message", time,
        """{"turn":1,"step":1,"message":{"role":"assistant","content":[""" +
            """{"type":"reasoning","text":"内心独白"},{"type":"text","text":"$text"}]},"stream":[]}""",
    )

    private fun call(name: String) = ev("tool/call", 1, """{"callId":"c$seq","name":"$name","arguments":"{}"}""")
    private fun result(isError: Boolean) = ev(
        "tool/result", 1,
        """{"message":{"content":[{"type":"tool-result","toolCallId":"c","isError":$isError,"content":"x"}]}}""",
    )

    @Test
    fun completedTurnReportsDurationToolsTodosAndReply() {
        val events = listOf(ev("session/title", 0, """{"title":"修复登录"}""")) + turn(
            1, 1000, """{"kind":"completed"}""",
            call("read"), result(false), call("bash"), result(true),
            ev("todo/write", 1, """{"todos":[{"content":"a","status":"completed"},{"content":"b","status":"in_progress"},{"content":"c","status":"pending"}]}"""),
            reply("全部改好了"),
        )
        val r = turnReportOf(events)!!
        assertEquals(TurnOutcome.COMPLETED, r.outcome)
        assertEquals(192_000L, r.durationMs)
        assertEquals(2, r.toolCalls)
        assertEquals(1, r.failedTools)
        assertTrue(r.complete)
        assertEquals(1, r.todoDone)
        assertEquals(3, r.todoTotal)
        // 思考块不进正文
        assertEquals("全部改好了", r.reply)
        assertEquals("修复登录", r.title)
        assertTrue(r.shouldNotify)
        assertEquals("任务完成", turnReportHeadline(r))
        assertEquals("用时 3 分 12 秒 · 调用 2 次工具（1 次失败） · 计划 1/3\n全部改好了", turnReportBody(r))
    }

    @Test
    fun onlyTheLastTurnIsCounted() {
        val events = turn(1, 0, """{"kind":"completed"}""", call("read"), call("read")) +
            turn(2, 500_000, """{"kind":"completed"}""", call("grep"))
        val r = turnReportOf(events)!!
        assertEquals(1, r.toolCalls)
        assertEquals(192_000L, r.durationMs)
    }

    @Test
    fun missingTurnStartMeansPartialCounts() {
        val events = listOf(call("read"), call("bash"), ev("turn/end", 9, """{"turn":4,"reason":{"kind":"completed"}}"""))
        val r = turnReportOf(events)!!
        assertNull(r.durationMs)
        assertFalse(r.complete)
        assertEquals("至少调用 2 次工具", turnReportStats(r))
    }

    @Test
    fun userStopIsSilentButFailuresNotify() {
        val stopped = turnReportOf(turn(1, 0, """{"kind":"aborted","reason":{"kind":"user"}}"""))!!
        assertTrue(stopped.stoppedByUser)
        assertFalse(stopped.shouldNotify)

        val hook = turnReportOf(turn(1, 0, """{"kind":"aborted","reason":{"kind":"hook","reason":"x"}}"""))!!
        assertTrue(hook.shouldNotify)
        assertEquals("任务已停止", turnReportHeadline(hook))

        val interrupted = turnReportOf(turn(1, 0, """{"kind":"interrupted"}"""))!!
        assertTrue(interrupted.shouldNotify)
        assertEquals("DSH 意外退出，任务中断", turnReportHeadline(interrupted))

        val error = turnReportOf(turn(1, 0, """{"kind":"error","error":{"message":"额度不足"}}""", reply("半截")))!!
        assertEquals("任务出错", turnReportHeadline(error))
        assertTrue(turnReportBody(error).endsWith("额度不足"))

        assertEquals("输出达到长度上限", turnReportHeadline(turnReportOf(turn(1, 0, """{"kind":"max-tokens"}"""))!!))
        assertFalse(turnReportOf(turn(1, 0, """{"kind":"forked"}"""))!!.shouldNotify)
    }

    @Test
    fun longReplyIsMarkedAsTruncated() {
        val r = turnReportOf(turn(1, 0, """{"kind":"completed"}""", reply("字".repeat(1000))))!!
        val body = turnReportBody(r)
        assertTrue(body.endsWith("…（未完）"))
        assertTrue(body.length < 700)
    }

    @Test
    fun noTurnEndMeansNoReport() {
        assertNull(turnReportOf(listOf(ev("turn/start", 0, """{"turn":1}"""), call("read"))))
    }

    @Test
    fun durationFormatting() {
        assertEquals("45 秒", formatDuration(45_000))
        assertEquals("3 分 12 秒", formatDuration(192_000))
        assertEquals("1 小时 5 分", formatDuration(3_900_000))
    }
}
