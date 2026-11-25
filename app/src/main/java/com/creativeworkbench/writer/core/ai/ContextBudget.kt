package com.creativeworkbench.writer.core.ai

import com.creativeworkbench.writer.core.data.Block

enum class ExclusionReason { BeyondBudget, Empty }

/** 没进上下文的条目：种类 + 原因 + 可读标签 + 估算 token。 */
data class ContextExclusion(
    val kind: String,
    val reason: ExclusionReason,
    val label: String,
    val tokens: Int
)

/** 装进预算的结果：整条/整段淘汰，绝不切半句。 */
data class FitResult(
    val text: String,
    val tokens: Int,
    val kept: Int,
    val dropped: List<ContextExclusion>
)

/**
 * 预算装载：照着 dsh-Go / SillyTavern 的「整组淘汰」做法，只淘汰完整段落/完整条目，
 * 不做字符截断。淘汰的每一条都带原因，进预览给用户看。
 */
object PromptBudget {

    /**
     * 正文装预算：优先砍离锚点最远的一头（锚点之前从最早删，之后从最晚删），锚点段永远保留。
     */
    fun fitBlocks(
        blocks: List<Block>,
        indices: List<Int>,
        cap: Int,
        anchorIndex: Int?,
        kind: String = "正文"
    ): FitResult {
        if (indices.isEmpty()) return FitResult("", 0, 0, emptyList())
        // 正文渲染必须稳定：DeepSeek 的上下文缓存按「完全一致的公共前缀」命中，
        // 在正文里插「← 从这里续写」这类随光标变的标记，会把这一段之后的缓存全废掉。
        // 续写位置改成正文之后的一行说明（见 AiContextBuilder）。
        fun render(index: Int): String = "[[b" + index + "]] " + blocks[index].text
        val kept = indices.toMutableList()
        val dropped = mutableListOf<ContextExclusion>()
        var tokens = kept.sumOf { TokenEstimator.estimate(render(it)) }
        while (tokens > cap && kept.size > 1) {
            val anchorPos = anchorIndex?.let { kept.indexOf(it) } ?: -1
            val victim = when {
                anchorPos < 0 -> kept.first()
                anchorPos == 0 -> kept.last()
                anchorPos == kept.lastIndex -> kept.first()
                anchorPos >= kept.lastIndex - anchorPos -> kept.first()
                else -> kept.last()
            }
            if (anchorIndex != null && victim == anchorIndex) break
            kept.remove(victim)
            val cost = TokenEstimator.estimate(render(victim))
            dropped.add(
                ContextExclusion(kind, ExclusionReason.BeyondBudget, "第 " + (victim + 1) + " 段", cost)
            )
            tokens -= cost
        }
        return FitResult(kept.joinToString("\n") { render(it) }, tokens, kept.size, dropped)
    }

    /** 摘要/素材装预算：整条省略；keepTail=true 时先砍最早的。 */
    fun fitEntries(
        entries: List<Pair<String, String>>,
        cap: Int,
        kind: String,
        keepTail: Boolean
    ): FitResult {
        if (entries.isEmpty()) return FitResult("", 0, 0, emptyList())
        val kept = entries.toMutableList()
        val dropped = mutableListOf<ContextExclusion>()
        var tokens = kept.sumOf { TokenEstimator.estimate(it.second) }
        while (tokens > cap && kept.size > 1) {
            val victim = if (keepTail) kept.first() else kept.last()
            kept.remove(victim)
            val cost = TokenEstimator.estimate(victim.second)
            dropped.add(ContextExclusion(kind, ExclusionReason.BeyondBudget, victim.first, cost))
            tokens -= cost
        }
        return FitResult(kept.joinToString("\n") { it.second }, tokens, kept.size, dropped)
    }
}