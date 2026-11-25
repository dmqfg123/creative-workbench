package com.creativeworkbench.writer.core.ai

import kotlin.math.ceil
import kotlin.math.min

enum class ContextSlot { Chapter, PrevChapterTail, ChapterSummaries, SettingNotes, MaterialNotes, Selection }
enum class ResultKind { Text, Diff, IssueList }
enum class WriteTarget { BodyAppend, BodyReplaceSelection, None }
enum class ActionWhen { Always, HasSelection, NoSelection, ChapterNotEmpty }
enum class ActionSource { Builtin, Imported }

/**
 * 单个动作的请求策略。字段为 null 时跟随全局（AiConfig.deepThinking / ModelProfile）。
 * 有了它，「续写」可以只给 4K 输出、「检查」的 JSON 给 6K，而不是所有动作共用一刀切的上限。
 */
data class ActionTuning(
    val maxTokens: Int? = null,
    val thinking: Boolean? = null,
    val temperature: Double? = null
)

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
    val packId: String? = null,
    val tuning: ActionTuning = ActionTuning()
)

/**
 * AI 配置只剩三件事：接口地址、模型名、密钥。
 * 上下文窗口、输出上限、温度、超时一律由 [ModelCatalog] 按模型自动给，
 * 不给用户塞一堆 API 参数——填错只会把服务端打出 400。
 */
data class AiConfig(
    val baseUrl: String = ModelCatalog.DEEPSEEK_BASE_URL,
    val model: String = ModelCatalog.DEFAULT_MODEL,
    /** DeepSeek 深度思考：更慢更贵、推理更细；关闭时按创作温度 1.2 直接写。 */
    val deepThinking: Boolean = false,
    val allowCleartextHttp: Boolean = false
) {
    val profile: ModelProfile get() = ModelCatalog.profileFor(baseUrl, model)
    val isDeepSeek: Boolean get() = profile.supportsThinking

    /** 界面展示用：这次实际会发出去的参数。 */
    val effectiveParams: RequestParams get() = profile.request(ActionTuning(), deepThinking)
}

/** 一个模型的硬参数 + 本 App 实际要发的东西，全部自动推导。 */
data class ModelProfile(
    val label: String,
    val contextWindowTokens: Int,
    /** 模型官方上限，只用来展示。 */
    val maxOutputTokens: Int,
    /** 非思考模式下的输出上限：够写完一段话，又不至于跑飞烧钱。 */
    val requestMaxTokens: Int,
    /** 思考模式的输出上限：思维链要占额度，得单独给大一点。 */
    val thinkingMaxTokens: Int,
    /** 创作温度；思考模式下不会发出去（模型不收）。 */
    val temperature: Double,
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

    /**
     * 单次请求最多带多少上下文（tokens）。
     * DeepSeek 窗口 1M，这里只取 1/4：够装下 40 万字长篇的全文（0.6 token/汉字），
     * 又不至于第一次未命中缓存就烧掉几毛钱。真要全库检索式长上下文，再把这个数往上调。
     */
    const val CONTEXT_BUDGET_TOKEN_CAP = 262_144

    val presets = listOf(
        ModelPreset("deepseek-flash", "deepseek-flash", "100 万字上下文，写作默认"),
        ModelPreset("deepseek-v4-pro", "deepseek-v4-pro", "推理更强，价格约 4 倍")
    )

    /**
     * 模型参数表。思考与否不写进 profile（它是每次请求的选择），
     * 只在 request() 里合并，避免「开着思考取 profile，再关掉思考」时温度永远拿不回来。
     */
    fun profileFor(baseUrl: String, model: String): ModelProfile {
        val name = model.trim().lowercase()
        val base = baseUrl.trim().lowercase()
        val deepSeek = name.startsWith("deepseek") || base.contains("deepseek")
        if (!deepSeek) {
            return ModelProfile(
                label = "通用 OpenAI 兼容",
                contextWindowTokens = 32_768,
                maxOutputTokens = 4_096,
                requestMaxTokens = 2_048,
                thinkingMaxTokens = 4_096,
                temperature = 0.8,
                timeoutSeconds = 60,
                supportsThinking = false
            )
        }
        return ModelProfile(
            label = "DeepSeek",
            contextWindowTokens = 1_048_576,
            maxOutputTokens = 393_216,
            requestMaxTokens = 4_096,
            thinkingMaxTokens = 16_384,
            // 官方建议创作写作 1.5，但真机实测 1.5 会跑飞（2500+ 字、结构错乱，甚至吐 XML 片段）；
            // 1.2 在“有文采”和“收得住”之间稳定得多。思考模式不认 temperature，请求时不发。
            temperature = 1.2,
            timeoutSeconds = 180,
            supportsThinking = true
        )
    }
}

/** 一次请求最终要发的参数：动作策略覆盖模型默认，思考模式下不发 temperature。 */
data class RequestParams(
    val maxTokens: Int,
    val thinking: Boolean,
    val temperature: Double?,
    val timeoutSeconds: Int
)

fun ModelProfile.request(tuning: ActionTuning, globalThinking: Boolean): RequestParams {
    val thinking = if (supportsThinking) (tuning.thinking ?: globalThinking) else false
    val defaultMax = if (thinking) thinkingMaxTokens else requestMaxTokens
    return RequestParams(
        maxTokens = (tuning.maxTokens ?: defaultMax).coerceIn(256, maxOutputTokens),
        thinking = thinking,
        // DeepSeek 思考模式不认 temperature，发了也只是被忽略，不如不发
        temperature = if (thinking) null else (tuning.temperature ?: temperature),
        timeoutSeconds = if (thinking) maxOf(timeoutSeconds, 300) else timeoutSeconds
    )
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

/**
 * token 估算：按 DeepSeek 官方口径（1 个汉字 ≈ 0.6 token、1 个英文字符 ≈ 0.3 token），
 * 再留 10% 余量。旧实现按「1 汉字 = 1.1 token」，把中文当成 1.8 倍来算，
 * 预算永远是虚的——明明能装 10 万字的窗口，被自己按 5 万字裁掉。
 * 真实 token 以接口返回的 usage 为准，这里只用于「装不装得下」的判断。
 */
object TokenEstimator {
    private const val ASCII_COST = 0.3
    private const val CJK_COST = 0.6
    private const val OTHER_COST = 0.6
    private const val SAFETY = 1.10

    fun estimate(text: String): Int {
        var tokens = 0.0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            tokens += when {
                codePoint <= 0x7F -> ASCII_COST
                isCjk(codePoint) -> CJK_COST
                else -> OTHER_COST
            }
            index += Character.charCount(codePoint)
        }
        return ceil(tokens * SAFETY).toInt()
    }

    fun fits(text: String, cap: Int): Boolean = estimate(text) <= cap

    /** 汉字、中文标点、日文假名、全角符号都按 CJK 计价。 */
    private fun isCjk(codePoint: Int): Boolean =
        codePoint in 0x2000..0x206F || // 通用标点（含中文引号、省略号）
            codePoint in 0x3000..0x303F || // 中文标点
            codePoint in 0x3400..0x4DBF || // 扩展 A
            codePoint in 0x4E00..0x9FFF || // 基本汉字
            codePoint in 0xF900..0xFAFF || // 兼容汉字
            codePoint in 0xFF00..0xFFEF || // 全角
            codePoint in 0x3040..0x30FF || // 假名
            codePoint in 0xAC00..0xD7AF    // 韩文
}
