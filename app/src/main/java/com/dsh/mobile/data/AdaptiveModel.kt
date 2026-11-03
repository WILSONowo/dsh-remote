package com.dsh.mobile.data

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

/** 判定「复杂任务」的关键词（命中即用 Pro） */
private val COMPLEX_TASK_KEYWORDS = listOf(
    "分析", "审查", "评审", "优化", "重构", "架构", "方案", "设计", "实现",
    "排查", "修复", "翻译", "总结", "规划", "复杂", "深度", "代码", "算法",
    "论文", "报告", "长文", "测试", "对比", "评估", "调研", "梳理",
)

internal fun adaptiveSelection(info: SessionModelsValue, text: String): ModelSelection? {
    val cur = info.current
    if (text.isBlank()) return null
    val groups = info.groups as? JsonArray ?: return null
    val group = groups.filterIsInstance<JsonObject>().firstOrNull {
        (it["id"] as? JsonPrimitive)?.contentOrNull == cur.provider
    } ?: return null
    val models = group["models"] as? JsonArray ?: return null
    val ids = models.filterIsInstance<JsonObject>().mapNotNull { (it["id"] as? JsonPrimitive)?.contentOrNull }
    val complex = text.length > 200 || COMPLEX_TASK_KEYWORDS.any { text.contains(it) }
    val target = if (complex) {
        ids.firstOrNull { it.contains("pro", ignoreCase = true) } ?: return null
    } else {
        ids.firstOrNull { it.contains("flash", ignoreCase = true) } ?: return null
    }
    return if (target == cur.model) null else ModelSelection(cur.provider, target)
}

internal suspend fun DshConnection.applyAdaptiveModel(id: String, text: String, known: SessionModelsValue? = null): SessionModelsValue? {
    val info = known ?: sessionModels(id) ?: return null
    val next = adaptiveSelection(info, text) ?: return info
    return try {
        selectModel(id, next.provider, next.model)
        info.copy(current = next)
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        info
    }
}
