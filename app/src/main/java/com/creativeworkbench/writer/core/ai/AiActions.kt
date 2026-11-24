package com.creativeworkbench.writer.core.ai

/**
 * 第一版三个内置动作。导入的动作包走同一套 AiActionSpec 字段。
 * 提示词写死在这里：内置动作不依赖外部文件，导入包才读 prompts 目录下的 md。
 */
object AiActions {
    const val CONTINUE = "builtin.continue"
    const val REWRITE = "builtin.rewrite"
    const val CHECK = "builtin.check"

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
            priority = 30
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
            priority = 20
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
            priority = 10
        )
    )

    fun byId(id: String): AiActionSpec? = builtin.firstOrNull { it.id == id }

    fun systemPrompt(action: AiActionSpec): String = when (action.id) {
        CONTINUE -> "你是中文小说写作助手。保持已有的人称、时态、语气和文风，自然衔接，不要总结前文，不要解释，不要加标题。只输出续写正文。"
        REWRITE -> "你是中文小说编辑。在不改变原意和情节的前提下润色文字：去掉冗余、调整节奏、让人物语气一致。只输出改写后的文字，不要解释。"
        CHECK -> "你是中文小说校对与情节检查助手。只报告确定的问题：前后矛盾、人物设定冲突、明显错别字、时间线错误。不要改写正文。"
        else -> "你是中文写作助手。"
    }

    fun instruction(action: AiActionSpec, selection: String?): String = when (action.id) {
        CONTINUE -> "请从【当前章】最后一个段落之后继续写 300-600 字，紧接上文，不要重复已有句子。"
        REWRITE -> "请改写下面这段文字，保持情节与信息不变，控制在原文 0.8-1.2 倍长度：\n" + (selection ?: "")
        CHECK -> "请检查【当前章】正文，输出严格 JSON（不要代码围栏）：{\"issues\":[{\"blockId\":\"段落 id\",\"excerpt\":\"原文片段\",\"severity\":\"low|medium|high\",\"message\":\"问题说明\"}]}。没有问题就输出 {\"issues\":[]}。"
        else -> ""
    }
}
