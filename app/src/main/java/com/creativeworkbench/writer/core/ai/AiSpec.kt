package com.creativeworkbench.writer.core.ai

import kotlin.math.ceil
import kotlin.math.min

enum class ContextSlot { Chapter, PrevChapterTail, ChapterSummaries, SettingNotes, MaterialNotes, Selection }
enum class ResultKind { Text, Diff, IssueList }
enum class WriteTarget { BodyAppend, BodyReplaceSelection, None }
enum class ActionWhen { Always, HasSelection, NoSelection, ChapterNotEmpty }
enum class ActionSource { Builtin, Imported }

data class AiActionSpec(
    val id: String,
    val name: String,
    val context: List<ContextSlot>,
    val promptFile: String,
    val result: ResultKind,
    val write: WriteTarget,
    val whenCondition: ActionWhen,
    val priority: Int,
    val source: ActionSource = ActionSource.Builtin,
    val packId: String? = null
)

/**
 * AI 配置只剩三件事：接口地址、模型名、密钥。
 * 上下文窗口、输出上限、温度、超时一律由 [ModelCatalog] 按模型自动给，
 * 不给用户塞一堆 API 参数——填错只会把服务端打出 400。
 */
data class AiConfig(
    val baseUrl: String = ModelCatalog.DEEPSEEK_BASE_URL,
    val model: String = ModelCatalog.DEFAULT_MODEL,
    /** DeepSeek 深度思考：更慢更贵、推理更细；关闭时按创作温度 1.5 直接写。 */
    val deepThinking: Boolean = false,
    val allowCleartextHttp: Boolean = false
) {
    val profile: ModelProfile get() = ModelCatalog.profileFor(baseUrl, model, deepThinking)
    val isDeepSeek: Boolean get() = profile.supportsThinking
}

/** 一个模型的硬参数 + 本 App 实际要发的东西，全部自动推导。 */
data class ModelProfile(
    val label: String,
    val contextWindowTokens: Int,
    /** 模型官方上限，只用来展示。 */
    val maxOutputTokens: Int,
    /** 本 App 单次请求的 max_tokens：够写完一段话，又不至于跑飞烧钱。 */
    val requestMaxTokens: Int,
    /** null = 不发 temperature（DeepSeek 思考模式下该参数无效）。 */
    val temperature: Double?,
    val timeoutSeconds: Int,
    val supportsThinking: Boolean
) {
    /** 预算窗口：模型有 100 万字上下文也不一次塞满。 */
    val budgetWindowTokens: Int get() = min(contextWindowTokens, ModelCatalog.CONTEXT_BUDGET_TOKEN_CAP)
}

data class ModelPreset(val model: String, val label: String, val note: String)

/**
 * 模型参数表。数值来自 DeepSeek 官方文档与开源权重 config
 * （deepseek-ai/DeepSeek-V3 config.json 的 max_position_embeddings 已不是当前 API 口径，
 * 当前 API：上下文 1M、单次输出上限 384K、思考模式默认开启且忽略 temperature）。
 */
object ModelCatalog {
    const val DEEPSEEK_BASE_URL = "https://api.deepseek.com"
    const val DEFAULT_MODEL = "deepseek-flash"

    /** 单次请求最多带多少上下文（tokens）。再多也不会更懂，只会更慢更贵。 */
    const val CONTEXT_BUDGET_TOKEN_CAP = 131_072

    val presets = listOf(
        ModelPreset("deepseek-flash", "deepseek-flash", "100 万字上下文，写作默认"),
        ModelPreset("deepseek-v4-pro", "deepseek-v4-pro", "推理更强，价格约 4 倍")
    )

    fun profileFor(baseUrl: String, model: String, deepThinking: Boolean): ModelProfile {
        val name = model.trim().lowercase()
        val base = baseUrl.trim().lowercase()
        val deepSeek = name.startsWith("deepseek") || base.contains("deepseek")
        if (!deepSeek) {
            return ModelProfile(
                label = "通用 OpenAI 兼容",
                contextWindowTokens = 32_768,
                maxOutputTokens = 4_096,
                requestMaxTokens = 2_048,
                temperature = 0.8,
                timeoutSeconds = 60,
                supportsThinking = false
            )
        }
        return ModelProfile(
            label = if (deepThinking) "DeepSeek · 深度思考" else "DeepSeek · 快速",
            contextWindowTokens = 1_048_576,
            maxOutputTokens = 393_216,
            requestMaxTokens = if (deepThinking) 16_384 else 4_096,
            // 官方参数建议：创作/诗歌 1.5。思考模式不收 temperature，就别发。
            temperature = if (deepThinking) null else 1.5,
            timeoutSeconds = if (deepThinking) 300 else 180,
            supportsThinking = true
        )
    }
}

data class ContextBudget(
    val windowTokens: Int,
    val ratio: Double = 0.6,
    val reservedOutputTokens: Int = 2048
) {
    val budgetTokens: Int get() = (windowTokens * ratio).toInt() - reservedOutputTokens
    val settingCap: Int get() = (budgetTokens * 0.40).toInt()
    val chapterCap: Int get() = (budgetTokens * 0.40).toInt()
    val summaryCap: Int get() = (budgetTokens * 0.10).toInt()
    val materialCap: Int get() = (budgetTokens * 0.10).toInt()

    companion object {
        fun from(profile: ModelProfile): ContextBudget = ContextBudget(
            windowTokens = profile.budgetWindowTokens,
            reservedOutputTokens = profile.requestMaxTokens
        )
    }
}

object TokenEstimator {
    fun estimate(text: String): Int {
        var units = 0.0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            units += if (codePoint <= 0x7F) 0.25 else 1.0
            index += Character.charCount(codePoint)
        }
        return ceil(units * 1.10).toInt()
    }

    fun fits(text: String, cap: Int): Boolean = estimate(text) <= cap
}
