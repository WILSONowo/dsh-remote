package com.dsh.mobile.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 协议契约：用插件生成的线上样例（dsh-remote-access/test/fixtures/protocol 的副本）检验 App 的解析。
 * 插件那边的契约测试保证它真的这样发；这边保证 App 按同样的形状读。
 * 线上格式变了：在插件仓库重新生成样例，拷贝到 app/src/test/resources/protocol，再让两边测试都通过。
 */
class ProtocolContractTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("protocol/$name")) { "missing fixture $name" }.readText()

    private val frames: List<MuxFrame> by lazy {
        (json.parseToJsonElement(fixture("mux-frames.json")) as JsonArray).map { json.decodeFromJsonElement(MuxFrame.serializer(), it) }
    }
    private val history: HistoryValue by lazy { json.decodeFromString(HistoryValue.serializer(), fixture("history-page.json")) }

    @Test
    fun everyFrameTypeIsOneTheAppHandles() {
        val handled = setOf(
            DshEventTypes.FRAME_SESSION_EVENT, DshEventTypes.FRAME_SUBSCRIBED, DshEventTypes.FRAME_PROJECTION,
            DshEventTypes.FRAME_APPROVAL_REQUESTED, DshEventTypes.FRAME_APPROVAL_RESOLVED,
            DshEventTypes.FRAME_QUESTION_REQUESTED, DshEventTypes.FRAME_QUESTION_RESOLVED, DshEventTypes.FRAME_STREAM_ERROR,
            DshEventTypes.HOST_SESSION_ADDED, DshEventTypes.HOST_SESSION_REMOVED, DshEventTypes.HOST_SESSION_STATUS,
            DshEventTypes.HOST_WORKSPACE_CHANGED,
        )
        for (f in frames) assertTrue("unhandled frame ${f.type}", f.type in handled)
    }

    @Test
    fun interactionFramesCarryWhatTheAppNeeds() {
        val approval = frames.first { it.type == DshEventTypes.FRAME_APPROVAL_REQUESTED }
        assertEquals("s1", approval.sessionId)
        assertEquals("ap-2", approval.approvalId)
        assertEquals("bash", approval.toolName)
        assertEquals("call-2", approval.callId)
        assertEquals("allowed-once", frames.first { it.type == DshEventTypes.FRAME_APPROVAL_RESOLVED }.outcome)

        val question = frames.first { it.type == DshEventTypes.FRAME_QUESTION_REQUESTED }
        assertEquals("选哪个？", question.questions.single().question)
        assertEquals(listOf("A", "B"), question.questions.single().options.map { it.label })
        // 网关取消（别处已回答）时插件发 outcome = cancelled
        assertEquals("cancelled", frames.first { it.type == DshEventTypes.FRAME_QUESTION_RESOLVED }.outcome)

        assertEquals("running", frames.first { it.type == DshEventTypes.HOST_SESSION_STATUS }.status)
        assertEquals("s2", frames.first { it.type == DshEventTypes.HOST_SESSION_ADDED }.sessionId)
        assertEquals("额度不足", frames.first { it.type == DshEventTypes.FRAME_STREAM_ERROR }.error?.message)
    }

    @Test
    fun liveSessionFramesDecode() {
        val events = frames.filter { it.type == DshEventTypes.FRAME_SESSION_EVENT }.mapNotNull { it.event }
        assertTrue(events.any { it.type == "assistant/reset" })
        val chunk = events.first { it.type == DshEventTypes.ASSISTANT_CHUNK }
        assertEquals("继续", ((chunk.data as JsonObject)["chunk"] as JsonObject)["text"]?.jsonPrimitive?.contentOrNull)
        assertEquals(11L, frames.first { it.type == DshEventTypes.FRAME_SUBSCRIBED }.lastSeq)
        assertEquals("修复登录", frames.first { it.type == DshEventTypes.FRAME_PROJECTION && it.key == "title" }.value?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun historyPageFeedsTurnReportsChatAndPending() {
        val events = history.events.map { it.event }
        assertTrue(history.hasMore)

        val report = turnReportOf(events)!!
        assertEquals(TurnOutcome.COMPLETED, report.outcome)
        assertEquals(192_000L, report.durationMs)
        assertEquals(1, report.toolCalls)
        assertEquals(0, report.failedTools)
        assertEquals(1, report.todoDone)
        assertEquals(2, report.todoTotal)
        assertEquals("改好了", report.reply)
        assertEquals("修复登录", report.title)

        val inputs = events.filter { it.type == DshEventTypes.USER_MESSAGE }.map { classifyUserMessage(it) }
        assertEquals(InputMessageKind.User, inputs[0])
        assertEquals(InputMessageKind.Context("技能：pdf"), inputs[1])

        // 审批已经 decided：不应恢复成待办
        assertTrue(scanHistoryEvents("s1", history.events).none { it is PendingItem.Approval })

        val call = events.first { it.type == DshEventTypes.TOOL_CALL }
        val args = (call.data as JsonObject)["arguments"]?.jsonPrimitive?.contentOrNull
        val risk = CommandRisk.assess("bash", null, args)
        assertEquals(RiskLevel.HIGH, risk.level)
        assertTrue(risk.findings.contains("递归强制删除"))
    }
}
