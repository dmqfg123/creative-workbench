package com.creativeworkbench.writer.core.ai

import kotlin.math.ceil

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
    val source: ActionSource = ActionSource.Builtin
)

data class AiConfig(
    val baseUrl: String = "",
    val model: String = "",
    val temperature: Double = 0.8,
    val maxTokens: Int = 2048,
    val timeoutSeconds: Int = 60,
    val contextWindowTokens: Int = 32768,
    val reservedOutputTokens: Int = 2048,
    val allowCleartextHttp: Boolean = false
)

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
