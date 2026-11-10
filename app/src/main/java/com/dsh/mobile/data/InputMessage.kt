package com.dsh.mobile.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * How a `user/message` event is presented, following the desktop chat:
 * - only surface events appended at their own position belong to the transcript; replacement
 *   copies (compaction checkpoints and other rewrites) are model-only;
 * - `data.source.kind == "user"` is something the person typed; any other source is context the
 *   host injected (skills, agent instructions, session references, notices…) and is shown as a
 *   collapsed context row, never as the person's own bubble.
 */
sealed interface InputMessageKind {
    /** Not part of the human transcript. */
    data object Hidden : InputMessageKind

    /** Typed by the person. */
    data object User : InputMessageKind

    /** Injected by the host; [label] names what was injected. */
    data class Context(val label: String) : InputMessageKind
}

fun classifyUserMessage(event: SessionEventWire): InputMessageKind =
    classifyUserMessage(event.surfaceOp, event.data)

fun classifyUserMessage(surfaceOp: JsonElement?, data: JsonElement): InputMessageKind {
    // Hosts without the surface layer omit surfaceOp; treat those events as appended.
    if (surfaceOp != null && surfaceOp !is JsonNull) {
        val op = (surfaceOp as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (op != "append") return InputMessageKind.Hidden
    }
    val record = data as? JsonObject ?: return InputMessageKind.User
    // Hosts that predate message sources only logged what the person typed.
    if (!record.containsKey("source")) return InputMessageKind.User
    val source = record["source"] as? JsonObject
    val kind = source?.string("kind")
    if (kind == "user") return InputMessageKind.User
    return InputMessageKind.Context(contextLabel(kind, source))
}

private fun contextLabel(kind: String?, source: JsonObject?): String = when (kind) {
    "session-reference" -> "引用会话" + suffix(source?.collect("references", "label"))
    "agent-instructions" -> "项目说明" + suffix(source?.collect("changes", "path"))
    "skill-invocation" -> "技能" + suffix(listOfNotNull(source?.string("name")))
    "compact-checkpoint" -> "上下文摘要"
    null -> "系统上下文"
    else -> "系统上下文 · $kind"
}

private fun suffix(names: List<String>?): String =
    if (names.isNullOrEmpty()) "" else "：" + names.joinToString("、")

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }

/** Distinct non-blank `field` values of the objects in array `key`, in first-seen order. */
private fun JsonObject.collect(key: String, field: String): List<String> =
    (this[key] as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonObject)?.string(field) }
        .distinct()
