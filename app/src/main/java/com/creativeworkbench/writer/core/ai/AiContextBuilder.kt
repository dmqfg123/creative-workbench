package com.creativeworkbench.writer.core.ai

import com.creativeworkbench.writer.core.data.Block
import com.creativeworkbench.writer.core.data.ChapterRepository
import com.creativeworkbench.writer.core.data.NoteEntity
import com.creativeworkbench.writer.core.data.NoteKind
import com.creativeworkbench.writer.core.data.NoteRepository
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

data class SelectionInfo(
    val blockId: String,
    val start: Int,
    val end: Int,
    val text: String
)

data class ContextPreview(
    val summary: String,
    val notes: List<String>,
    /** 每块实际 token 与上限，给预览做明细。 */
    val blocks: List<ContextBlock> = emptyList(),
    /** 没进上下文的条目与原因，逐条可解释。 */
    val excluded: List<ContextExclusion> = emptyList(),
    val budget: Int = 0,
    val used: Int = 0,
    val lengthChars: Int = 600
)

data class ContextBlock(val label: String, val tokens: Int, val cap: Int)

data class SettingOverflow(val message: String, val omitted: Int)

data class PreparedContext(
    val system: String,
    val user: String,
    val preview: ContextPreview? = null,
    val overflow: SettingOverflow? = null,
    /** 本次续写目标字数（生成时按它算 max_tokens）。 */
    val lengthChars: Int = 600
)

/**
 * 上下文装配只在这里做：平台负责决定读什么，AI 无权挑选。
 * 预算 = min(模型窗口, 13 万 tokens) × 60% − 本次输出上限；分槽 设定 40% / 正文 40% / 摘要 10% / 素材 10%。
 */
class AiContextBuilder(
    private val chapters: ChapterRepository,
    private val noteRepository: NoteRepository
) {
    suspend fun prepare(
        action: AiActionSpec,
        config: AiConfig,
        workId: String,
        chapterId: String,
        blocks: List<Block>,
        activeBlockId: String?,
        selection: SelectionInfo?,
        allowSettingOverflow: Boolean,
        instructionOverride: String? = null,
        /** 续写目标字数；只影响提示词里的长度要求（输出上限在 AiViewModel 按它算）。 */
        lengthChars: Int = 600,
        /** 用户手动勾掉的设定 id（面板里可以取消引用某条人物/设定）。 */
        excludedNoteIds: Set<String> = emptySet()
    ): PreparedContext {
        val chapterList = chapters.chapters(workId)
        val allNotes = noteRepository.observeAll().first()
        // 分槽比例只有 ContextBudget 里一份，别在这儿再算一遍——两处比例迟早会漂。
        val contextBudget = ContextBudget.from(config.profile)
        val budget = contextBudget.budgetTokens.coerceAtLeast(1024)
        val settingCap = contextBudget.settingCap.coerceAtLeast(1)
        val chapterCap = contextBudget.chapterCap.coerceAtLeast(1)
        val summaryCap = contextBudget.summaryCap.coerceAtLeast(1)
        val materialCap = contextBudget.materialCap.coerceAtLeast(1)
        val notes = mutableListOf<String>()

        // 前一章结尾 500 字：既进上下文，也用来判断设定命中。
        val currentIndex = chapterList.indexOfFirst { it.id == chapterId }
        val prevChapter = if (currentIndex > 0) chapterList[currentIndex - 1] else null
        val prevTail = prevChapter?.let { prev ->
            runCatching {
                chapters.openChapter(prev.id)?.content?.blocks?.joinToString("\n") { it.text }
            }.getOrNull()?.takeLast(500).orEmpty()
        }.orEmpty()

        // 设定类：当前作品的全部人物/设定，强制注入。
        val settingNotes = allNotes.filter {
            it.workId == workId && NoteKind.setting.contains(it.kind) && it.id !in excludedNoteIds
        }
        val scanText = blocks.joinToString("\n") { it.text } + "\n" + prevTail
        val injection = SettingInjector.inject(settingNotes, scanText, settingCap)
        val settingText = injection.keptText
        if (injection.omitted.isNotEmpty() && !allowSettingOverflow) {
            val omittedTitles = injection.omitted.map { it.note.title }.take(6).joinToString("、")
            return PreparedContext(
                system = "",
                user = "",
                overflow = SettingOverflow(
                    message = "设定共 " + settingNotes.size + " 条，命中 " + injection.hitCount +
                        " 条；设定预算 " + settingCap + " tokens，需省略 " +
                        injection.omitted.size + " 条（" + omittedTitles + "…）。" +
                        "可以精简设定、拆分成长短两版，或选「仍然发送」。",
                    omitted = injection.omitted.size
                )
            )
        }

        // 素材类：只有显式勾选（aiReference）的才进；备忘不参与。
        val materialNotes = allNotes.filter {
            (it.workId == null || it.workId == workId) &&
                NoteKind.material.contains(it.kind) && it.aiReference && it.id !in excludedNoteIds
        }.sortedBy { it.id }
        val materialFit = PromptBudget.fitEntries(
            entries = materialNotes.map { it.id to SettingInjector.render(it) },
            cap = materialCap,
            kind = "素材",
            keepTail = false
        )
        val materialText = materialFit.text

        // 最近 10 章摘要：空摘要不占槽；超预算从最早的一章开始整条省略。
        val summaries = chapterList
            .filter { it.id != chapterId && !it.summary.isNullOrBlank() }
            .takeLast(10)
        val summaryEntries = summaries.map {
            ("第 " + (it.orderIndex + 1) + " 章 " + it.title) to
                ("第 " + (it.orderIndex + 1) + " 章 " + it.title + "：" + it.summary)
        }
        val summaryFit = PromptBudget.fitEntries(summaryEntries, summaryCap, "摘要", keepTail = true)
        val summaryText = summaryFit.text

        // 当前章：按动作决定窗口，超预算整段淘汰（不切半句）。
        val anchorIndex = blocks.indexOfFirst { it.id == activeBlockId }.takeIf { it >= 0 }
        val rewriteSelectionIndex = selection
            ?.let { selected -> blocks.indexOfFirst { it.id == selected.blockId } }
            ?.takeIf { it >= 0 }
        // 只有「检查」需要段落 id（结果要能跳回段落），续写/改写一律不带标记：
        // 真机实测带 [[bN]] 时模型会照抄标记，甚至吐出 XML 片段（温度 1.5 时更严重）。
        val withMarkers = action.id == AiActions.CHECK
        val chapterFit = when (action.id) {
            AiActions.CHECK -> PromptBudget.fitBlocks(
                blocks, checkIndices(blocks, anchorIndex), chapterCap, anchorIndex, markers = true
            )
            // 选中段单独作为【选中文字】发一次，正文窗口里要把它排除，否则同一段算两遍、白花 token
            AiActions.REWRITE -> PromptBudget.fitBlocks(
                blocks,
                rewriteIndices(blocks, selection).filter { it != rewriteSelectionIndex },
                chapterCap,
                null,
                markers = false
            )
            else -> PromptBudget.fitBlocks(
                blocks,
                blocks.indices.toList(),
                chapterCap,
                anchorIndex ?: blocks.lastIndex,
                markers = withMarkers
            )
        }
        val chapterText = chapterFit.text

        val chapterChars = blocks.sumOf { it.text.length }
        if (action.id == AiActions.CHECK && chapterChars > 15000) {
            notes.add("本次只检查当前段及前后各 2 段（约 " + chapterText.length + " 字），不是全书检查")
        }
        if (injection.omitted.isNotEmpty()) {
            notes.add(
                "设定共 " + settingNotes.size + " 条，已包含 " + injection.kept.size +
                    " 条（其中命中 " + injection.hitCount + " 条优先），" + injection.omitted.size +
                    " 条因预算省略：" + injection.omitted.map { it.note.title }.take(6).joinToString("、")
            )
        }
        if (injection.overflowed) {
            notes.add("有命中设定单条就超过设定预算，已整条保留，其它设定被挤掉")
        }

        val user = buildString {
            appendLine("【作品】" + (chapters.work(workId)?.title ?: ""))
            if (settingText.isNotBlank()) {
                appendLine("【设定（强制注入，共 " + injection.kept.size + " 条）】")
                appendLine(settingText)
            } else {
                appendLine("【设定】无")
            }
            if (materialText.isNotBlank()) {
                appendLine("【素材（勾选，共 " + materialNotes.size + " 条）】")
                appendLine(materialText)
            }
            if (summaryText.isNotBlank()) {
                appendLine("【最近章节摘要】")
                appendLine(summaryText)
            }
            if (prevTail.isNotBlank()) {
                appendLine("【前一章结尾】")
                appendLine(prevTail)
            }
            appendLine(if (withMarkers) "【当前章（每段前的 [[id]] 是段落标记）】" else "【当前章】")
            appendLine(chapterText)
            if (anchorIndex != null && action.id == AiActions.CONTINUE) {
                // 续写位置写成独立一行：正文本身保持与上次调用逐字一致，缓存才命中
                appendLine("【续写位置】接在上面最后一段之后。")
            }
            if (selection != null) {
                appendLine("【选中文字】")
                appendLine(selection.text)
            }
            appendLine("【任务】")
            append(instructionOverride?.takeIf { it.isNotBlank() } ?: actionInstruction(action, selection, lengthChars))
        }

        val settingChars = settingText.length
        val summaryLine = "正文 " + chapterText.length + " 字 · 设定 " +
            injection.kept.size + " 条 " + settingChars + " 字 · 摘要 " +
            summaries.size + " 条 · 素材 " + materialNotes.size + " 条"
        val blocksBudget = listOf(
            ContextBlock("设定", injection.usedTokens, settingCap),
            ContextBlock("正文", chapterFit.tokens, chapterCap),
            ContextBlock("摘要", summaryFit.tokens, summaryCap),
            ContextBlock("素材", materialFit.tokens, materialCap),
            ContextBlock("前一章尾", TokenEstimator.estimate(prevTail), TokenEstimator.estimate(prevTail))
        )
        val dropped = buildList {
            injection.omitted.forEach { hit ->
                add(ContextExclusion("设定", ExclusionReason.BeyondBudget, hit.note.title, hit.tokens))
            }
            addAll(chapterFit.dropped)
            addAll(summaryFit.dropped)
            addAll(materialFit.dropped)
        }
        if (chapterFit.dropped.isNotEmpty()) {
            notes.add("正文超预算，已去掉最早 " + chapterFit.dropped.size + " 段（整段省略，没有截半句）")
        }
        if (summaryFit.dropped.isNotEmpty()) {
            notes.add("摘要超预算，省略了 " + summaryFit.dropped.size + " 章：" +
                summaryFit.dropped.map { it.label }.take(4).joinToString("、"))
        }
        if (materialFit.dropped.isNotEmpty()) {
            notes.add("素材超预算，省略了 " + materialFit.dropped.size + " 条")
        }

        return PreparedContext(
            system = AiActions.systemPrompt(action),
            user = user,
            preview = ContextPreview(
                summary = summaryLine,
                notes = notes,
                blocks = blocksBudget,
                excluded = dropped,
                budget = budget,
                used = blocksBudget.sumOf { it.tokens },
                lengthChars = lengthChars
            )
        )
    }

    /** 段落标记名，和正文里的 [[bN]] 对齐。 */
    private fun markerOf(index: Int): String = "b" + index

    private fun actionInstruction(
        action: AiActionSpec,
        selection: SelectionInfo?,
        lengthChars: Int
    ): String = when (action.id) {
        AiActions.CONTINUE ->
            "从【续写位置】接着写约 " + lengthChars + " 字。只输出续写出来的正文：" +
                "不要任何段落标记、编号、标题、前后缀，不要重复已有句子，不要解释或总结。"
        AiActions.REWRITE ->
            "改写【选中文字】这一段，保持情节和信息不变，长度接近原文。" +
                "只输出改写后的正文，不要任何标记、编号、标题或说明。"
        AiActions.CHECK ->
            "检查【当前章】，输出严格 JSON（不要代码围栏）：" +
                "{\"issues\":[{\"blockId\":\"段落 id\",\"excerpt\":\"原文片段\",\"severity\":\"low|medium|high\",\"message\":\"问题说明\"}]}。" +
                "没有问题输出 {\"issues\":[]}。"
        else -> ""
    }

    /** 检查的范围：全章，或（超 1.5 万字时）光标段 ±2。 */
    private fun checkIndices(blocks: List<Block>, anchorIndex: Int?): List<Int> {
        val total = blocks.sumOf { it.text.length }
        if (total <= 15000) return blocks.indices.toList()
        val anchor = anchorIndex ?: 0
        return ((anchor - 2).coerceAtLeast(0)..(anchor + 2).coerceAtMost(blocks.lastIndex)).toList()
    }

    /** 改写的范围：选中段前后各 5 段（按 blockId 定位——按文本找会在重复段落上认错段）。 */
    private fun rewriteIndices(blocks: List<Block>, selection: SelectionInfo?): List<Int> {
        if (selection == null) return blocks.indices.toList()
        val index = blocks.indexOfFirst { it.id == selection.blockId }.takeIf { it >= 0 } ?: 0
        return ((index - 5).coerceAtLeast(0)..(index + 5).coerceAtMost(blocks.lastIndex)).toList()
    }

    fun contentHash(blocks: List<Block>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        blocks.forEach { block ->
            digest.update(block.id.toByteArray())
            digest.update(block.text.toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(32)
    }
}