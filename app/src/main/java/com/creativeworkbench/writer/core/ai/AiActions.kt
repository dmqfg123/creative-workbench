package com.creativeworkbench.writer.core.ai

/**
 * 第一版三个内置动作。导入的动作包走同一套 AiActionSpec 字段。
 * 提示词写死在这里：内置动作不依赖外部文件，导入包才读 prompts 目录下的 md。
 */
object AiActions {
    const val CONTINUE = "builtin.continue"
    const val REWRITE = "builtin.rewrite"
    const val EXPAND = "builtin.expand"
    const val CHECK = "builtin.check"
    /** 把这一章写成摘要，回填 chapter.summary —— 下一章续写时就是靠它知道前面发生了什么。 */
    const val SUMMARY = "builtin.summary"

    val builtin: List<AiActionSpec> = listOf(
        AiActionSpec(
            id = CONTINUE,
            name = "续写",
            context = listOf(
                ContextSlot.Chapter,
                ContextSlot.PrevChapterTail,
                ContextSlot.ChapterSummaries,
                ContextSlot.SettingNotes,
                ContextSlot.MaterialNotes
            ),
            promptFile = "builtin/continue",
            result = ResultKind.Text,
            write = WriteTarget.BodyAppend,
            whenCondition = ActionWhen.NoSelection,
            priority = 30,
            // 续写是高频动作：输出给 4K token（约 6800 字）足够，别用 384K 上限等它跑飞
            tuning = ActionTuning(maxTokens = 4096)
        ),
        AiActionSpec(
            id = REWRITE,
            name = "改写润色",
            context = listOf(
                ContextSlot.Selection,
                ContextSlot.Chapter,
                ContextSlot.SettingNotes,
                ContextSlot.MaterialNotes
            ),
            promptFile = "builtin/rewrite",
            result = ResultKind.Diff,
            write = WriteTarget.BodyReplaceSelection,
            whenCondition = ActionWhen.HasSelection,
            priority = 20,
            tuning = ActionTuning(maxTokens = 4096)
        ),
        AiActionSpec(
            id = EXPAND,
            name = "扩写",
            context = listOf(
                ContextSlot.Selection,
                ContextSlot.Chapter,
                ContextSlot.SettingNotes,
                ContextSlot.MaterialNotes
            ),
            promptFile = "builtin/expand",
            result = ResultKind.Diff,
            write = WriteTarget.BodyReplaceSelection,
            whenCondition = ActionWhen.HasSelection,
            priority = 15,
            // 扩写输出一定比原文长，给足额度；撞上限会自动续写拼接
            tuning = ActionTuning(maxTokens = 4096)
        ),
        AiActionSpec(
            id = SUMMARY,
            name = "本章摘要",
            context = listOf(
                ContextSlot.Chapter,
                ContextSlot.ChapterSummaries,
                ContextSlot.SettingNotes
            ),
            promptFile = "builtin/summary",
            result = ResultKind.Text,
            write = WriteTarget.None,
            whenCondition = ActionWhen.ChapterNotEmpty,
            priority = 5,
            // 100–200 字摘要，1K tokens 足够；撞上限会自动续写
            tuning = ActionTuning(maxTokens = 1024)
        ),
        AiActionSpec(
            id = CHECK,
            name = "检查",
            context = listOf(
                ContextSlot.Chapter,
                ContextSlot.ChapterSummaries,
                ContextSlot.SettingNotes
            ),
            promptFile = "builtin/check",
            result = ResultKind.IssueList,
            write = WriteTarget.None,
            whenCondition = ActionWhen.ChapterNotEmpty,
            priority = 10,
            // 检查要输出 JSON：条数多时 4K 会截断，给 6K
            tuning = ActionTuning(maxTokens = 6144)
        )
    )

    fun byId(id: String): AiActionSpec? = builtin.firstOrNull { it.id == id }

    fun systemPrompt(action: AiActionSpec): String = when (action.id) {
        CONTINUE -> "你是中文小说写作助手。保持已有的人称、时态、语气和文风，自然衔接。" +
            "只输出续写出来的正文本身：不要段落标记、编号、标题、引子或任何解释。"
        REWRITE -> "你是中文小说编辑。在不改变原意和情节的前提下润色文字：去掉冗余、调整节奏、让人物语气一致。" +
            "只输出改写后的正文本身，不要任何标记、编号或解释。"
        EXPAND -> "你是中文小说作者。把给定的一段扩写得更饱满：补细节、动作、感官、心理，" +
            "不要新增情节事件，不要改变人物关系和走向。只输出扩写后的正文，不要任何标记或解释。"
        SUMMARY -> "你是中文小说编辑。为给定的一章写一段剧情摘要，供后续章节写作参考。" +
            "只写已经发生的事实，不评价、不猜测、不润色，只输出摘要正文。"
        CHECK -> "你是中文小说校对与情节检查助手。只报告确定的问题：前后矛盾、人物设定冲突、明显错别字、时间线错误。不要改写正文。"
        else -> "你是中文写作助手。"
    }

    fun instruction(action: AiActionSpec, selection: String?): String = when (action.id) {
        CONTINUE -> "请从【当前章】最后一个段落之后继续写 300-600 字，紧接上文，不要重复已有句子。"
        REWRITE -> "请改写下面这段文字，保持情节与信息不变，控制在原文 0.8-1.2 倍长度：\n" + (selection ?: "")
        EXPAND -> "请把下面这段扩写到约 2 倍长度，补细节不补情节：\n" + (selection ?: "")
        SUMMARY -> "用 100–200 字总结【当前章】：谁做了什么、结果如何、结尾留下了什么。只输出摘要正文。"
        CHECK -> "请检查【当前章】正文，输出严格 JSON（不要代码围栏）：{\"issues\":[{\"blockId\":\"段落 id\",\"excerpt\":\"原文片段\",\"severity\":\"low|medium|high\",\"message\":\"问题说明\"}]}。没有问题就输出 {\"issues\":[]}。"
        else -> ""
    }
}
