package com.dsh.mobile.data

/**
 * 从其他 App 分享进来的文字：标题和正文合成一段，放进首页「新任务」输入框。
 * 网页分享常把标题放 EXTRA_SUBJECT、链接放 EXTRA_TEXT；正文已含标题时不重复。
 * 思路参考 dshgo 的 ShareInbox（MIT）。
 */
const val SHARED_TEXT_MAX_CHARS = 20_000

fun sharedText(subject: String?, text: String?): String? {
    val body = text?.trim().orEmpty()
    val title = subject?.trim().orEmpty()
    val combined = when {
        body.isEmpty() -> title
        title.isEmpty() || body.contains(title) -> body
        else -> "$title\n$body"
    }
    return combined.take(SHARED_TEXT_MAX_CHARS).ifBlank { null }
}

/** 分享的文字接到已有草稿后面，中间空一行 */
fun appendToDraft(draft: String, shared: String): String =
    if (draft.isBlank()) shared else draft.trimEnd() + "\n\n" + shared
