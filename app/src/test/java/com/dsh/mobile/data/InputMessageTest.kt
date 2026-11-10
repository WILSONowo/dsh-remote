package com.dsh.mobile.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Test

class InputMessageTest {
    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private fun classify(surfaceOp: String?, data: String) =
        classifyUserMessage(surfaceOp?.let(::json), json(data))

    private val typed = """{"id":"m1","content":[{"type":"text","text":"hi"}],"source":{"kind":"user"}}"""

    @Test
    fun typedMessageIsUser() {
        assertEquals(InputMessageKind.User, classify("\"append\"", typed))
    }

    @Test
    fun replacementCopiesAreHidden() {
        assertEquals(InputMessageKind.Hidden, classify("""{"startSeq":3,"endSeq":9}""", typed))
        assertEquals(
            InputMessageKind.Hidden,
            classify("""{"startSeq":3,"endSeq":9}""", """{"content":[],"source":{"kind":"compact-checkpoint"}}"""),
        )
        assertEquals(InputMessageKind.Hidden, classify("\"replace\"", typed))
    }

    @Test
    fun legacyEventsStayVisible() {
        assertEquals(InputMessageKind.User, classify(null, """{"content":[{"type":"text","text":"hi"}]}"""))
        assertEquals(InputMessageKind.User, classify("null", """{"content":[]}"""))
    }

    @Test
    fun injectedContextIsLabelled() {
        assertEquals(
            InputMessageKind.Context("技能：pdf"),
            classify("\"append\"", """{"content":[],"source":{"kind":"skill-invocation","name":"pdf"}}"""),
        )
        assertEquals(
            InputMessageKind.Context("项目说明：AGENTS.md、docs/AGENTS.md"),
            classify(
                "\"append\"",
                """{"content":[],"source":{"kind":"agent-instructions","changes":[{"path":"AGENTS.md"},{"path":"docs/AGENTS.md"},{"path":"AGENTS.md"}]}}""",
            ),
        )
        assertEquals(
            InputMessageKind.Context("引用会话：调研"),
            classify("\"append\"", """{"content":[],"source":{"kind":"session-reference","references":[{"label":"调研"}]}}"""),
        )
        assertEquals(
            InputMessageKind.Context("技能"),
            classify("\"append\"", """{"content":[],"source":{"kind":"skill-invocation"}}"""),
        )
        assertEquals(
            InputMessageKind.Context("系统上下文 · time-context"),
            classify("\"append\"", """{"content":[],"source":{"kind":"time-context","form":"notice"}}"""),
        )
        assertEquals(InputMessageKind.Context("系统上下文"), classify("\"append\"", """{"content":[],"source":{}}"""))
        assertEquals(InputMessageKind.Context("系统上下文"), classify("\"append\"", """{"content":[],"source":null}"""))
    }
}
