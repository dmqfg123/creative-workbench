package com.creativeworkbench.writer.core.ai

import com.creativeworkbench.writer.core.data.Block
import com.creativeworkbench.writer.core.data.ChapterEntity
import com.creativeworkbench.writer.core.data.ChapterRepository
import com.creativeworkbench.writer.core.data.ForeshadowingRepository
import com.creativeworkbench.writer.core.data.NoteEntity
import com.creativeworkbench.writer.core.data.NoteKind
import com.creativeworkbench.writer.core.data.NoteRepository
import com.creativeworkbench.writer.core.data.OutlineNodeEntity
import com.creativeworkbench.writer.core.data.OutlineRepository
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
    val lengthChars: Int = 600,
    /** 本次实际带了哪些来源，面板上直接显示，不用猜。 */
    val sources: List<String> = emptyList()
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
 * 上下文来源开关：用户要能自己决定「参考正文 / 参考大纲 / 参考笔记」。
 * 三者都不勾时只剩任务描述——面板会拦下这种组合并给出提示。
 */
data class ContextOptions(
    val lengthChars: Int = 600,
    val excludedNoteIds: Set<String> = emptySet(),
    val includeNoteSources: Boolean = true,
    val includeOutline: Boolean = true,
    val includeBody: Boolean = true,
    val bodyRange: BodyRange = BodyRange.Whole,
    /** 「按大纲推进」：把本章边界与目标字数写成硬约束（默认关，开了才发）。 */
    val followOutline: Boolean = false,
    /**
     * 设定怎么进上下文：自动（AI 按剧情挑）/ 全部 / 手动 / 关。
     * 默认值保持 Manual 是为了不改变既有调用方的行为；界面默认走 Auto。
     */
    val noteMode: NoteContextMode = NoteContextMode.Manual,
    /** Auto 模式下挑中的条目 id，顺序即轻重（注入时按这个顺序装，不再重排）。 */
    val autoNoteIds: List<String> = emptyList()
)

/**
 * 上下文装配只在这里做：平台负责决定读什么，AI 无权挑选。
 * 预算 = min(模型窗口, 26 万 tokens) × 60% − 本次输出上限；
 * 分槽 设定 35% / 正文 35% / 大纲 10% / 摘要 10% / 素材 10%。
 */
class AiContextBuilder(
    private val chapters: ChapterRepository,
    private val noteRepository: NoteRepository,
    private val outlineRepository: OutlineRepository,
    private val foreshadowingRepository: ForeshadowingRepository
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
        options: ContextOptions = ContextOptions()
    ): PreparedContext {
        val chapterList = chapters.chapters(workId)
        val allNotes = noteRepository.observeAll().first()
        // 分槽比例只有 ContextBudget 里一份，别在这儿再算一遍——两处比例迟早会漂。
        val contextBudget = ContextBudget.from(config.profile)
        val budget = contextBudget.budgetTokens.coerceAtLeast(1024)
        val settingCap = contextBudget.settingCap.coerceAtLeast(1)
        val outlineCap = contextBudget.outlineCap.coerceAtLeast(1)
        val summaryCap = contextBudget.summaryCap.coerceAtLeast(1)
        val materialCap = contextBudget.materialCap.coerceAtLeast(1)
        val notes = mutableListOf<String>()
        // 「检查」必须看到正文，否则什么也检查不了：勾掉正文对它无效
        val withBody = options.includeBody || action.id == AiActions.CHECK
        val withNotes = options.includeNoteSources && options.noteMode != NoteContextMode.Off
        val autoOrder = if (options.noteMode == NoteContextMode.Auto) options.autoNoteIds else emptyList()
        val autoIds = autoOrder.toSet()
        val autoIndex = autoOrder.withIndex().associate { it.value to it.index }
        /**
         * 四种模式的准入规则（唯一的判定点，正文和素材共用）：
         * - 关：一条都不带；
         * - 自动：只带挑中的（挑中清单本身已经做过 chapter/kind 过滤）；
         * - 全部 / 手动：带全部，减去用户手动取消的。
         * 自动模式下仍然允许用户临时取消某条——那是覆盖，不是把整份清单丢掉。
         */
        fun admits(note: NoteEntity): Boolean = when (options.noteMode) {
            NoteContextMode.Off -> false
            NoteContextMode.Auto -> note.id in autoIds && note.id !in options.excludedNoteIds
            NoteContextMode.All, NoteContextMode.Manual -> note.id !in options.excludedNoteIds
        }

        // 设定生效章节（抄 Novelcrafter 的 Codex Progressions）：
        // 写在第三章才成立的设定，不该在第一章的上下文里出现，否则模型会提前埋错伏笔。
        val orderOfChapter = chapterList.withIndex().associate { it.value.id to it.index }
        val currentOrder = orderOfChapter[chapterId] ?: 0
        fun activeInThisChapter(note: NoteEntity): Boolean {
            val from = note.fromChapterId ?: return true
            val fromOrder = orderOfChapter[from] ?: return true
            return currentOrder >= fromOrder
        }

        // 前一章结尾 500 字：既进上下文，也用来判断设定命中。
        val currentIndex = chapterList.indexOfFirst { it.id == chapterId }
        val prevChapter = if (currentIndex > 0) chapterList[currentIndex - 1] else null
        val prevTail = prevChapter?.let { prev ->
            runCatching {
                chapters.openChapter(prev.id)?.content?.blocks?.joinToString("\n") { it.text }
            }.getOrNull()?.takeLast(500).orEmpty()
        }.orEmpty()

        // 设定类：当前作品的全部人物/设定，强制注入。
        val settingNotes = if (withNotes) {
            allNotes.filter {
                it.workId == workId && NoteKind.setting.contains(it.kind) &&
                    admits(it) && activeInThisChapter(it)
            }.let { list ->
                // 自动模式按 AI/本地的挑选顺序装；其它模式交给 SettingInjector 按命中重排（稳定序）。
                if (options.noteMode == NoteContextMode.Auto) {
                    list.sortedBy { autoIndex[it.id] ?: Int.MAX_VALUE }
                } else {
                    list
                }
            }
        } else {
            emptyList()
        }
        val scanText = blocks.joinToString("\n") { it.text } + "\n" + prevTail
        val injection = SettingInjector.inject(
            settingNotes,
            scanText,
            settingCap,
            preserveOrder = options.noteMode == NoteContextMode.Auto
        )
        val settingText = injection.keptText
        if (injection.omitted.isNotEmpty() && !allowSettingOverflow && withNotes) {
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
        val materialNotes = if (withNotes) {
            allNotes.filter {
                (it.workId == null || it.workId == workId) &&
                    NoteKind.material.contains(it.kind) && it.aiReference &&
                    admits(it) && activeInThisChapter(it)
            }.let { list ->
                if (options.noteMode == NoteContextMode.Auto) {
                    list.sortedBy { autoIndex[it.id] ?: Int.MAX_VALUE }
                } else {
                    list.sortedBy { it.id }
                }
            }
        } else {
            emptyList()
        }
        val materialFit = PromptBudget.fitEntries(
            entries = materialNotes.map { it.id to SettingInjector.render(it) },
            cap = materialCap,
            kind = "素材",
            keepTail = false
        )
        val materialText = materialFit.text

        // 大纲：本章 + 前后各一章 + 全书级节点。远处的章节只喂标题和要点，不喂正文。
        val outlineNodes = if (options.includeOutline) {
            runCatching { outlineRepository.list(workId) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        // 未回收伏笔：写正文时强制带上（抄 long-novel-writer「写前加载全部未回收条目」）
        val openForeshadow = if (options.includeOutline) {
            runCatching { foreshadowingRepository.unsettled(workId) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val foreshadowText = openForeshadow.take(12).joinToString("\n") { item ->
            val due = item.dueChapterId?.let { id ->
                val index = chapterList.indexOfFirst { it.id == id }
                if (index >= 0) "，计划第 " + (index + 1) + " 章回收" else ""
            } ?: ""
            "· " + item.title + if (item.detail.isNotBlank()) "：" + item.detail.take(60) else "" + due
        }
        val outlineEntries = renderOutline(outlineNodes, chapterList, chapterId, currentIndex)
        val outlineFit = PromptBudget.fitEntries(outlineEntries, outlineCap, "大纲", keepTail = true)
        val outlineText = outlineFit.text
        if (outlineNodes.isNotEmpty() && outlineEntries.isEmpty()) {
            notes.add("大纲不在本章范围")
        }

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
        val anchorForRange = anchorIndex ?: rewriteSelectionIndex ?: blocks.lastIndex
        // 空槽回流：设定/大纲/摘要/素材没用满的预算，全部让给正文。
        // 按死比例切（各 35%）会把「大纲只有 3 条」的剩余额度白白扔掉，
        // 而正文恰恰是最该多带的那一块。
        val usedByOthers = injection.usedTokens + outlineFit.tokens + summaryFit.tokens + materialFit.tokens
        val chapterCap = (budget - usedByOthers).coerceAtLeast((budget * 0.35).toInt())
        val chapterFit = when {
            !withBody -> PromptBudget.fitBlocks(emptyList(), emptyList(), chapterCap, null, markers = false)
            action.id == AiActions.CHECK -> PromptBudget.fitBlocks(
                blocks, checkIndices(blocks, anchorIndex), chapterCap, anchorIndex, markers = true
            )
            // 选中段单独作为【选中文字】发一次，正文窗口里要把它排除，否则同一段算两遍、白花 token
            // 摘要要覆盖整章，不受「正文范围」影响
            action.id == AiActions.SUMMARY -> PromptBudget.fitBlocks(
                blocks, blocks.indices.toList(), chapterCap, null, markers = false
            )
            action.id == AiActions.REWRITE || action.id == AiActions.EXPAND -> PromptBudget.fitBlocks(
                blocks,
                rangedIndices(blocks, rewriteIndices(blocks, selection), options.bodyRange, anchorForRange)
                    .filter { it != rewriteSelectionIndex },
                chapterCap,
                null,
                markers = false
            )
            else -> PromptBudget.fitBlocks(
                blocks,
                rangedIndices(blocks, blocks.indices.toList(), options.bodyRange, anchorForRange),
                chapterCap,
                anchorForRange,
                markers = withMarkers
            )
        }
        val chapterText = chapterFit.text

        val chapterChars = blocks.sumOf { it.text.length }
        if (action.id == AiActions.CHECK && chapterChars > 15000) {
            notes.add("只检查光标段 ±2")
        }
        if (!withBody) {
            notes.add("未带正文")
        } else if (options.bodyRange.radius != null && action.id != AiActions.CHECK) {
            notes.add("正文" + options.bodyRange.shortLabel)
        }
        if (options.noteMode == NoteContextMode.Auto) {
            notes.add("设定 " + injection.kept.size + " 条（自动挑选）")
        }
        if (injection.omitted.isNotEmpty()) {
            notes.add("设定 " + injection.kept.size + "/" + settingNotes.size + " 条（省 " + injection.omitted.size + "）")
        }
        if (injection.overflowed) {
            notes.add("设定超预算")
        }

        val user = buildString {
            appendLine("【作品】" + (chapters.work(workId)?.title ?: ""))
            if (outlineText.isNotBlank()) {
                appendLine("【大纲（本章及前后章）】")
                appendLine(outlineText)
            }
            if (foreshadowText.isNotBlank()) {
                appendLine("【未回收伏笔（共 " + openForeshadow.size + " 条，写的时候留意回收或强化）】")
                appendLine(foreshadowText)
            }
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
            if (withBody) {
                appendLine(if (withMarkers) "【当前章（每段前的 [[id]] 是段落标记）】" else "【当前章】")
                appendLine(chapterText)
            }
            if (anchorIndex != null && action.id == AiActions.CONTINUE) {
                // 续写位置写成独立一行：正文本身保持与上次调用逐字一致，缓存才命中
                appendLine("【续写位置】接在上面最后一段之后。")
            }
            if (selection != null) {
                appendLine("【选中文字】")
                appendLine(selection.text)
            }
            // 「按大纲推进」：把边界写成硬约束（片段来自 AI_NovelGenerator 的 chapter plan 提示词）
            if (options.followOutline && outlineText.isNotBlank() &&
                (action.id == AiActions.CONTINUE || action.id == AiActions.EXPAND)
            ) {
                appendLine("【本章边界】")
                appendLine("- 只写第 " + (currentIndex + 1) + " 章；后面章节的目标是后续边界，不要提前写完。")
                appendLine("- 本章要推进的点见【大纲】；用行动和结果完成，不能用「决定以后再查」代替。")
                appendLine("- 篇幅：约 " + options.lengthChars + " 字，允许 ±15%。")
            }
            appendLine("【任务】")
            append(
                instructionOverride?.takeIf { it.isNotBlank() }
                    ?: actionInstruction(action, selection, options.lengthChars)
            )
        }

        val settingChars = settingText.length
        val summaryLine = "正文 " + chapterText.length + " 字 · 大纲 " + outlineFit.kept +
            " 条 · 设定 " + injection.kept.size + " 条 " + settingChars + " 字 · 摘要 " +
            summaries.size + " 条 · 素材 " + materialNotes.size + " 条"
        val blocksBudget = listOf(
            ContextBlock("设定", injection.usedTokens, settingCap),
            ContextBlock("正文", chapterFit.tokens, chapterCap),
            ContextBlock("大纲", outlineFit.tokens, outlineCap),
            ContextBlock("摘要", summaryFit.tokens, summaryCap),
            ContextBlock("素材", materialFit.tokens, materialCap),
            ContextBlock("前一章尾", TokenEstimator.estimate(prevTail), TokenEstimator.estimate(prevTail))
        )
        val dropped = buildList {
            injection.omitted.forEach { hit ->
                add(ContextExclusion("设定", ExclusionReason.BeyondBudget, hit.note.title, hit.tokens))
            }
            addAll(chapterFit.dropped)
            addAll(outlineFit.dropped)
            addAll(summaryFit.dropped)
            addAll(materialFit.dropped)
        }
        if (chapterFit.dropped.isNotEmpty()) {
            notes.add("正文超预算，去掉 " + chapterFit.dropped.size + " 段")
        }
        if (outlineFit.dropped.isNotEmpty()) {
            notes.add("大纲超预算")
        }
        if (summaryFit.dropped.isNotEmpty()) {
            notes.add("摘要超预算，省略了 " + summaryFit.dropped.size + " 章：" +
                summaryFit.dropped.map { it.label }.take(4).joinToString("、"))
        }
        if (materialFit.dropped.isNotEmpty()) {
            notes.add("素材超预算")
        }

        val sources = buildList {
            if (withBody) add("正文")
            if (outlineText.isNotBlank()) add("大纲")
            if (foreshadowText.isNotBlank()) add("伏笔 " + openForeshadow.size)
            if (settingText.isNotBlank() || materialText.isNotBlank()) add("笔记")
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
                lengthChars = options.lengthChars,
                sources = sources
            )
        )
    }

    /**
     * 只喂「本章 + 前后各一章 + 全书级」节点：
     * 全量大纲会把 token 全烧在跟这章无关的远处剧情上；太少又接不上前后。
     */
    private fun renderOutline(
        nodes: List<OutlineNodeEntity>,
        chapterList: List<ChapterEntity>,
        chapterId: String,
        currentIndex: Int
    ): List<Pair<String, String>> {
        if (nodes.isEmpty()) return emptyList()
        val nearby = setOf(
            chapterList.getOrNull(currentIndex - 1)?.id,
            chapterId,
            chapterList.getOrNull(currentIndex + 1)?.id
        ).filterNotNull().toSet()
        val orderOf = chapterList.withIndex().associate { it.value.id to it.index }
        return nodes
            .filter { node -> node.chapterId == null || node.chapterId in nearby }
            .sortedWith(
                compareBy(
                    { it.chapterId?.let { id -> orderOf[id] ?: Int.MAX_VALUE } ?: -1 },
                    { it.orderIndex }
                )
            )
            .map { node ->
                val chapterLabel = node.chapterId?.let { id ->
                    val index = chapterList.indexOfFirst { it.id == id }
                    if (index >= 0) "第 " + (index + 1) + " 章 " else ""
                } ?: ""
                val head = node.title + if (node.content.isNotBlank()) "：" + node.content else ""
                // 目标字数进提示词：让模型知道这一点大概写多少，别一条要点灌 3000 字
                val target = node.targetWords?.takeIf { it > 0 }?.let { "（目标约 " + it + " 字）" } ?: ""
                ("大纲·" + node.title) to ("· " + chapterLabel + head + target)
            }
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
        AiActions.EXPAND ->
            "把【选中文字】扩写到约 " + lengthChars + " 字：补细节、动作、感官、心理，" +
                "不要新增情节事件，不要改变人物关系和走向。" +
                "只输出扩写后的正文，不要任何标记、编号、标题或说明。"
        AiActions.SUMMARY ->
            "用 100–200 字总结【当前章】：谁做了什么、结果如何、结尾留下什么钩子。" +
                "只输出摘要正文，不要标题、不要评价。"
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

    /** 用户在面板里选的正文范围：整章不动，其余按光标段前后各 N 段裁。 */
    private fun rangedIndices(
        blocks: List<Block>,
        base: List<Int>,
        range: BodyRange,
        anchorIndex: Int
    ): List<Int> {
        val radius = range.radius ?: return base
        val allowed = ((anchorIndex - radius).coerceAtLeast(0)..(anchorIndex + radius).coerceAtMost(blocks.lastIndex)).toSet()
        return base.filter { it in allowed }
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
