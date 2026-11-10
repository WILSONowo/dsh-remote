package com.dsh.mobile.ui.screens

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// ──────────────────────────── 聊天条目模型 ────────────────────────────

/** 用户消息里的图片内容块（mediaType + base64 data，DSH 协议 image 块） */
data class UserImage(val mediaType: String, val base64: String)

sealed interface ChatItem {
    val key: String
    /** 服务端消息序号：缓存/网络首屏合并去重、排序用 */
    val seq: Long?

    data class User(
        override val key: String,
        val text: String,
        override val seq: Long? = null,
        val images: List<UserImage> = emptyList(),
        /** T6 插话乐观标记：本条消息以 steer 模式发送（纯乐观，服务端无回执，不落缓存） */
        val steerSent: Boolean = false,
    ) : ChatItem
    data class Assistant(
        override val key: String,
        val text: String,
        val streaming: Boolean = false,
        val thinkSeconds: Long? = null,
        /** S5：思考链文本——reasoning-delta 流式累积；assistant/message 的 reasoning 块兜底（不落磁盘缓存） */
        val thinkingText: String = "",
        override val seq: Long? = null,
        /** T6 插话乐观标记：本条回复由插话触发（纯乐观，不落缓存） */
        val steerSent: Boolean = false,
    ) : ChatItem
    data class Tool(
        override val key: String,
        val callId: String,
        val name: String,
        val args: String,
        val status: String = "running",
        val result: String? = null,
        val isError: Boolean = false,
        override val seq: Long? = null,
        /** S5：工具耗时 ms——tool/call ↔ tool/result 的 time 差值（实时配对，历史重放为 null） */
        val elapsedMs: Long? = null,
    ) : ChatItem

    data class Notice(override val key: String, val text: String, val isError: Boolean = false, override val seq: Long? = null) : ChatItem

    /** 宿主注入的上下文（技能、项目说明、引用会话等）：与桌面版一致折叠成一行，不当作用户气泡 */
    data class Context(override val key: String, val label: String, val text: String, override val seq: Long? = null) : ChatItem
}

// ──────────────────────────── 过程分组（对齐桌面版 Chat 的 process-groups） ────────────────────────────
//
// 桌面版把一轮里连续的「过程」——思考、工具调用——收进一个默认折叠的分组，标题只说做了哪几类事
// （如「已读取文件，搜索代码，执行了命令」）；助手的正文回复单独显示，会结束当前分组；
// 用户消息、注入上下文、错误提示等不属于过程，也会把分组断开。进行中的分组标题显示当前动作和
// 一行细节（如「正在运行命令 · npm test」）。

/** 工具类别：与桌面版 activity() 的划分和中文文案一致 */
enum class ToolActivity(val running: String, val preparing: String, val done: String) {
    Read("正在读取文件", "准备读取文件", "已读取文件"),
    ReadImage("正在读取图片", "准备读取图片", "已读取图片"),
    Search("正在搜索代码", "准备搜索代码", "已搜索代码"),
    Write("正在写入文件", "准备写入文件", "已写入文件"),
    Edit("正在编辑文件", "准备编辑文件", "修改了文件"),
    Commands("正在运行命令", "准备运行命令", "执行了命令"),
    Code("正在运行代码", "准备运行代码", "运行了代码"),
    WebSearch("正在搜索网页", "准备搜索网页", "已搜索网页"),
    WebFetch("正在访问网页", "准备访问网页", "已访问网页"),
    Subagents("正在协调子智能体", "准备协调子智能体", "已协调子智能体"),
    Plan("正在更新计划", "准备更新计划", "更新了计划"),
    Questions("等待你的操作", "准备提问", "向用户提出了问题"),
    Tools("正在调用工具", "准备调用工具", "已调用工具"),
}

/** 分组里只有思考、没有工具时的文案 */
const val PROCESS_THINKING = "正在分析请求"
const val PROCESS_THINKING_DONE = "已完成分析"

fun toolActivityOf(name: String): ToolActivity = when {
    name == "read" -> ToolActivity.Read
    name == "read_image" -> ToolActivity.ReadImage
    name == "grep" || name == "glob" || name.endsWith("_inspect") -> ToolActivity.Search
    name == "write" -> ToolActivity.Write
    name == "edit" || name == "apply_patch" -> ToolActivity.Edit
    name in setOf("bash", "pwsh", "exec_command", "write_stdin") || name.startsWith("terminal_") -> ToolActivity.Commands
    name == "run_code" -> ToolActivity.Code
    name == "web_search" -> ToolActivity.WebSearch
    name == "web_fetch" -> ToolActivity.WebFetch
    name == "subagent" || name.startsWith("subagent_") -> ToolActivity.Subagents
    name in setOf("todo_write", "create_goal", "update_goal", "get_goal") -> ToolActivity.Plan
    name == "ask_user_question" || name == "request_user_input" -> ToolActivity.Questions
    else -> ToolActivity.Tools
}

/** 一行细节的最大字数（与桌面版一致） */
const val TOOL_DETAIL_MAX_CHARS = 160

/** 取细节时依次查看的参数名（与桌面版 LIVE_TOOL_DETAIL_KEYS 同序） */
private val TOOL_DETAIL_KEYS = listOf(
    "title", "description", "objective", "task", "task_name", "name", "question", "questions",
    "prompt", "message", "command", "cmd", "queries", "query", "pattern", "url", "uri",
    "file_path", "path", "target", "action", "status",
)

private val detailJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** 压缩空白；超长截断并以「…」结尾 */
fun normalizeDetail(text: String, max: Int = TOOL_DETAIL_MAX_CHARS): String {
    val normalized = text.replace(Regex("\\s+"), " ").trim()
    if (normalized.codePointCount(0, normalized.length) <= max) return normalized
    val end = normalized.offsetByCodePoints(0, max - 1)
    return normalized.substring(0, end).trimEnd() + "…"
}

private fun detailOfValue(value: JsonElement?): String = when {
    value is JsonPrimitive && value.isString -> normalizeDetail(value.content)
    value is JsonArray && value.isNotEmpty() && value.all { it is JsonPrimitive && it.isString } ->
        normalizeDetail(value.joinToString(", ") { (it as JsonPrimitive).content })
    else -> ""
}

private fun questionDetail(value: JsonElement?): String {
    val arr = value as? JsonArray ?: return ""
    for (item in arr) {
        val detail = detailOfValue((item as? JsonObject)?.get("question"))
        if (detail.isNotEmpty()) return detail
    }
    return ""
}

/**
 * 工具调用的一行摘要：参数 JSON 里第一个有文字的常用字段（命令、路径、查询……）。
 * 参数不是 JSON 对象或没有这些字段时返回空串，由调用方决定退回显示什么。
 */
fun toolDetailOf(args: String): String {
    if (args.isBlank()) return ""
    val obj = try {
        detailJson.parseToJsonElement(args) as? JsonObject
    } catch (_: Exception) {
        null
    } ?: return ""
    for (key in TOOL_DETAIL_KEYS) {
        if (!obj.containsKey(key)) continue
        val detail = if (key == "questions") questionDetail(obj[key]) else detailOfValue(obj[key])
        if (detail.isNotEmpty()) return detail
    }
    return ""
}

/** 思考进行中的细节：最新一段非空思考（去掉 Markdown 粗体标记） */
fun reasoningDetailOf(thinking: String): String {
    val paragraphs = thinking.split(Regex("\\r?\\n[\\t ]*\\r?\\n"))
    for (i in paragraphs.indices.reversed()) {
        val detail = normalizeDetail(paragraphs[i].replace("**", ""))
        if (detail.isNotEmpty()) return detail
    }
    return ""
}

/** 列表里的一行：单条消息，或一组折叠的过程 */
sealed interface ChatRow {
    val key: String

    /** @param hideThinking 这条助手回复的思考已经收进上方的过程分组，卡片里不再重复显示 */
    data class Single(val item: ChatItem, val hideThinking: Boolean = false) : ChatRow {
        override val key: String get() = item.key
    }

    /** @param closed 分组已结束（后面有回复/新消息，或会话已空闲）：标题改为「做过什么」的总结 */
    data class Process(override val key: String, val members: List<ChatItem>, val closed: Boolean) : ChatRow
}

private fun ChatItem.Assistant.hasReply() = text.isNotBlank()

/** 思考部分算作过程：有思考文本，或刚开始流式、正文还没出来（显示「正在分析请求」） */
private fun ChatItem.Assistant.hasReasoning() = thinkingText.isNotBlank() || (streaming && !hasReply())

/**
 * 把聊天条目按桌面版规则分组：连续的思考和工具调用收进一个过程分组，
 * 助手正文、用户消息、注入上下文、提示都单独成行并结束当前分组。
 * @param sessionRunning 会话仍在运行：最后一个分组保持「进行中」
 */
fun groupChatItems(items: List<ChatItem>, sessionRunning: Boolean): List<ChatRow> {
    val rows = ArrayList<ChatRow>(items.size)
    val pending = ArrayList<ChatItem>()
    fun flush(closed: Boolean) {
        if (pending.isEmpty()) return
        rows.add(ChatRow.Process("process_" + pending[0].key, pending.toList(), closed))
        pending.clear()
    }
    for (item in items) {
        when (item) {
            is ChatItem.Tool -> pending.add(item)
            is ChatItem.Assistant -> {
                val reasoning = item.hasReasoning()
                if (reasoning) pending.add(item)
                if (item.hasReply()) {
                    flush(closed = true)
                    rows.add(ChatRow.Single(item, hideThinking = item.thinkingText.isNotBlank()))
                }
            }
            else -> {
                flush(closed = true)
                rows.add(ChatRow.Single(item))
            }
        }
    }
    val live = sessionRunning || pending.any { it is ChatItem.Assistant && it.streaming }
    flush(closed = !live)
    return rows
}

/** 分组的工作统计：按工具类别计数（多者在前，同数按出现顺序），以及进行中的动作和细节 */
data class ProcessSummary(
    val counts: List<Pair<ToolActivity, Int>>,
    /** 正在进行的工具类别；null 表示正在思考或已结束 */
    val running: ToolActivity?,
    val detail: String,
)

fun processSummaryOf(group: ChatRow.Process): ProcessSummary {
    val counts = LinkedHashMap<ToolActivity, Int>()
    var running: ToolActivity? = null
    var detail = ""
    for (m in group.members) {
        if (m !is ChatItem.Tool) continue
        val kind = toolActivityOf(m.name)
        counts[kind] = (counts[kind] ?: 0) + 1
        if (!group.closed && m.status == "running") {
            running = kind
            detail = toolDetailOf(m.args).ifEmpty { normalizeDetail(m.name) }
        }
    }
    if (!group.closed && running == null) {
        val thinking = group.members.lastOrNull { it is ChatItem.Assistant && it.streaming } as? ChatItem.Assistant
        detail = thinking?.let { reasoningDetailOf(it.thinkingText) }.orEmpty()
    }
    // sortedByDescending 是稳定排序：同数保持首次出现的先后
    val ranked = counts.entries.map { it.key to it.value }.sortedByDescending { it.second }
    return ProcessSummary(ranked, running, if (group.closed) "" else detail)
}

/**
 * 已结束分组的标题：取前三类，不带次数（与桌面版 processTitle 一致）。
 * 两类合并共同的「已」前缀，如「已读取文件并搜索代码」；超过三类结尾加「等」。
 */
fun processDoneTitle(summary: ProcessSummary): String {
    val labels = summary.counts.take(3).map { it.first.done }
    val first = labels.firstOrNull() ?: return PROCESS_THINKING_DONE
    val second = labels.getOrNull(1) ?: return first
    if (labels.size == 2) {
        val prefix = "已"
        val tail = if (first.startsWith(prefix) && second.startsWith(prefix)) second.removePrefix(prefix) else second
        return "${first}并$tail"
    }
    val title = labels.joinToString("，")
    return if (summary.counts.size > 3) "${title}等" else title
}

/** 分组标题：结束后是总结，进行中是当前动作 + 一行细节 */
fun processTitleOf(group: ChatRow.Process, summary: ProcessSummary = processSummaryOf(group)): String {
    if (group.closed) return processDoneTitle(summary)
    val label = summary.running?.running ?: PROCESS_THINKING
    return if (summary.detail.isEmpty()) label else "$label · ${summary.detail}"
}

/** 分组图标用的类别：结束后取最多的一类，进行中取当前动作；null 表示思考 */
fun processIconActivity(group: ChatRow.Process, summary: ProcessSummary): ToolActivity? =
    if (group.closed) summary.counts.firstOrNull()?.first else summary.running
