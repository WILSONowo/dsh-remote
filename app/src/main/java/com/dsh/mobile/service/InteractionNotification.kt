package com.dsh.mobile.service

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.dsh.mobile.data.DshConnection
import com.dsh.mobile.data.QuestionItem
import com.dsh.mobile.data.RiskClassifier
import com.dsh.mobile.data.RiskLevel
import java.util.UUID

internal const val INTERACT = "com.dsh.mobile.NOTIFICATION_INTERACTION"
internal const val REPLY_TEXT = "reply"

internal data class Interaction(
    val session: String,
    val rpc: String,
    val approval: String? = null,
    val highRisk: Boolean = false,
    val questions: List<QuestionItem> = emptyList(),
    val answers: List<DshConnection.QuestionAnswer> = emptyList(),
    val selected: List<String> = emptyList(),
    val nonce: String = UUID.randomUUID().toString(),
) {
    val question get() = questions.getOrNull(answers.size)
    val key get() = session to approval

    fun withReply(text: String): Interaction? {
        val q = question ?: return null
        if (text.isBlank() || (q.options.isNotEmpty() && q.options.none { it.label == text })) return null
        if (q.multiSelect && q.options.isNotEmpty()) {
            val choices = if (text in selected) selected - text else selected + text
            return copy(selected = choices, nonce = UUID.randomUUID().toString())
        }
        val answer = if (q.options.isEmpty()) DshConnection.QuestionAnswer(q.id, emptyList(), custom = text)
            else DshConnection.QuestionAnswer(q.id, listOf(text))
        return advance(answer)
    }

    fun finishSelection(): Interaction? {
        val q = question ?: return null
        if (!q.multiSelect || selected.isEmpty()) return null
        return advance(DshConnection.QuestionAnswer(q.id, selected))
    }

    private fun advance(answer: DshConnection.QuestionAnswer) =
        copy(answers = answers + answer, selected = emptyList(), nonce = UUID.randomUUID().toString())

    fun actions(context: Context): List<Notification.Action> {
        val q = question
        return when {
            approval != null -> buildList {
                if (!highRisk) add(action(context, "allow", "允许一次"))
                add(action(context, "reject", "拒绝"))
            }
            q != null -> buildList {
                val input = RemoteInput.Builder(REPLY_TEXT).setLabel(q.question)
                    .apply { if (q.options.isNotEmpty()) setChoices(q.options.map { it.label }.toTypedArray()) }
                    .build()
                val label = if (q.multiSelect && q.options.isNotEmpty()) "选择选项" else "回复"
                add(action(context, "reply", label, input))
                if (selected.isNotEmpty()) add(action(context, "confirm", "提交选择"))
            }
            else -> emptyList()
        }
    }

    private fun action(context: Context, operation: String, label: String, input: RemoteInput? = null): Notification.Action {
        val intent = Intent(context, DshConnectionService::class.java).setAction(INTERACT)
            .setData(Uri.Builder().scheme("dsh-notification").authority("pending")
                .appendPath(nonce).appendPath(operation).build())
        val flags = when {
            input == null -> PendingIntent.FLAG_IMMUTABLE
            Build.VERSION.SDK_INT >= 31 -> PendingIntent.FLAG_MUTABLE
            else -> 0
        }
        return Notification.Action.Builder(null, label, PendingIntent.getService(context, 0, intent, flags)).apply {
            if (input != null) addRemoteInput(input)
            if (Build.VERSION.SDK_INT >= 31) setAuthenticationRequired(true)
        }.build()
    }

    companion object {
        fun from(event: DshConnection.Event): Interaction? = when (event) {
            is DshConnection.Event.ApprovalRequested -> Interaction(event.sessionId, event.rpcId, event.approvalId,
                RiskClassifier.level(event.toolName, event.reason) == RiskLevel.HIGH)
            is DshConnection.Event.QuestionRequested -> Interaction(event.sessionId, event.rpcId, questions = event.questions)
            else -> null
        }?.takeIf { it.rpc.isNotBlank() }
    }
}
