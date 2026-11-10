package com.dsh.mobile.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatProcessTest {

    private fun user(k: String) = ChatItem.User(k, "问题")
    private fun tool(k: String, name: String, args: String = "{}", status: String = "done", isError: Boolean = false) =
        ChatItem.Tool(k, callId = k, name = name, args = args, status = status, isError = isError)
    private fun reply(k: String, text: String = "回答", thinking: String = "", streaming: Boolean = false) =
        ChatItem.Assistant(k, text, streaming = streaming, thinkingText = thinking)

    @Test
    fun consecutiveThinkingAndToolsFoldIntoOneGroup() {
        val rows = groupChatItems(
            listOf(
                user("u1"),
                reply("a1", text = "", thinking = "先看看文件"),
                tool("t1", "read"),
                tool("t2", "bash"),
                reply("a2", text = "改好了", thinking = "确认一下"),
            ),
            sessionRunning = false,
        )
        assertEquals(listOf("u1", "process_a1", "a2"), rows.map { it.key })
        val group = rows[1] as ChatRow.Process
        assertEquals(listOf("a1", "t1", "t2", "a2"), group.members.map { it.key })
        assertTrue(group.closed)
        // 回复的思考已收进分组，卡片里不再重复
        assertTrue((rows[2] as ChatRow.Single).hideThinking)
    }

    @Test
    fun replyWithoutThinkingDoesNotHideAnything() {
        val rows = groupChatItems(listOf(user("u1"), reply("a1")), sessionRunning = false)
        assertEquals(listOf("u1", "a1"), rows.map { it.key })
        assertFalse((rows[1] as ChatRow.Single).hideThinking)
    }

    @Test
    fun userMessagesNoticesAndContextBreakGroups() {
        val rows = groupChatItems(
            listOf(
                tool("t1", "read"),
                ChatItem.Notice("n1", "出错了", isError = true),
                tool("t2", "grep"),
                ChatItem.Context("c1", "技能：pdf", ""),
                tool("t3", "edit"),
            ),
            sessionRunning = false,
        )
        assertEquals(listOf("process_t1", "n1", "process_t2", "c1", "process_t3"), rows.map { it.key })
        assertTrue(rows.filterIsInstance<ChatRow.Process>().all { it.closed })
    }

    @Test
    fun trailingGroupStaysOpenWhileRunning() {
        val items = listOf(user("u1"), tool("t1", "bash", """{"command":"npm test"}""", status = "running"))
        assertFalse((groupChatItems(items, sessionRunning = true).last() as ChatRow.Process).closed)
        assertTrue((groupChatItems(items, sessionRunning = false).last() as ChatRow.Process).closed)
        // 流式中的思考也算进行中，即使会话状态还没同步过来
        val thinking = listOf(user("u1"), reply("a1", text = "", streaming = true))
        val row = groupChatItems(thinking, sessionRunning = false).last() as ChatRow.Process
        assertFalse(row.closed)
        assertEquals(PROCESS_THINKING, processTitleOf(row))
    }

    @Test
    fun streamingReplyClosesTheGroupAbove() {
        val rows = groupChatItems(
            listOf(user("u1"), tool("t1", "read"), reply("a1", text = "正在写", streaming = true)),
            sessionRunning = true,
        )
        assertEquals(listOf("u1", "process_t1", "a1"), rows.map { it.key })
        assertTrue((rows[1] as ChatRow.Process).closed)
    }

    @Test
    fun runningTitleShowsCurrentActionAndDetail() {
        val group = ChatRow.Process(
            "g",
            listOf(tool("t1", "read"), tool("t2", "bash", """{"command":"npm   test\n --watch"}""", status = "running")),
            closed = false,
        )
        assertEquals("正在运行命令 · npm test --watch", processTitleOf(group))
    }

    @Test
    fun runningThinkingShowsLatestParagraph() {
        val group = ChatRow.Process(
            "g",
            listOf(reply("a1", text = "", thinking = "第一段\n\n**第二段** 在这里\n\n  ", streaming = true)),
            closed = false,
        )
        assertEquals("正在分析请求 · 第二段 在这里", processTitleOf(group))
    }

    @Test
    fun doneTitleMatchesDesktopWording() {
        fun titleOf(vararg names: String) =
            processTitleOf(ChatRow.Process("g", names.mapIndexed { i, n -> tool("t$i", n) }, closed = true))
        assertEquals(PROCESS_THINKING_DONE, processTitleOf(ChatRow.Process("g", listOf(reply("a", "", "想")), closed = true)))
        assertEquals("已读取文件", titleOf("read", "read"))
        // 两类：共同的「已」只保留一次
        assertEquals("已读取文件并搜索代码", titleOf("read", "grep"))
        assertEquals("执行了命令并已读取文件", titleOf("bash", "bash", "read"))
        // 三类按次数排序，同数按出现顺序
        assertEquals("修改了文件，已读取文件，执行了命令", titleOf("read", "edit", "edit", "bash"))
        assertEquals("已读取文件，已搜索代码，执行了命令等", titleOf("read", "glob", "bash", "web_fetch"))
    }

    @Test
    fun toolActivitiesFollowDesktopCategories() {
        assertEquals(ToolActivity.Commands, toolActivityOf("terminal_run"))
        assertEquals(ToolActivity.Search, toolActivityOf("code_inspect"))
        assertEquals(ToolActivity.Edit, toolActivityOf("apply_patch"))
        assertEquals(ToolActivity.Plan, toolActivityOf("todo_write"))
        assertEquals(ToolActivity.Subagents, toolActivityOf("subagent_spawn"))
        assertEquals(ToolActivity.Questions, toolActivityOf("ask_user_question"))
        assertEquals(ToolActivity.Tools, toolActivityOf("mcp__github__search"))
    }

    @Test
    fun toolDetailPicksFirstUsefulArgument() {
        assertEquals("src/main.kt", toolDetailOf("""{"file_path":"src/main.kt","limit":20}"""))
        // 字段优先级按桌面版顺序：description 先于 command
        assertEquals("跑测试", toolDetailOf("""{"command":"npm test","description":"跑测试"}"""))
        assertEquals("a, b", toolDetailOf("""{"queries":["a","b"]}"""))
        assertEquals("选哪个？", toolDetailOf("""{"questions":[{"id":"1"},{"question":"选哪个？"}]}"""))
        assertEquals("", toolDetailOf("""{"limit":20}"""))
        assertEquals("", toolDetailOf("not json"))
        assertEquals("", toolDetailOf(""))
        val long = toolDetailOf("""{"command":"${"x".repeat(400)}"}""")
        assertEquals(TOOL_DETAIL_MAX_CHARS, long.length)
        assertTrue(long.endsWith("…"))
    }

    @Test
    fun failedToolsAreCountedInSummary() {
        val group = ChatRow.Process("g", listOf(tool("t1", "bash", isError = true, status = "error")), closed = true)
        val summary = processSummaryOf(group)
        assertEquals(listOf(ToolActivity.Commands to 1), summary.counts)
        assertEquals(ToolActivity.Commands, processIconActivity(group, summary))
    }
}
