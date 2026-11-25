package com.creativeworkbench.writer.core.ai

/**
 * 模型经常自作主张加「【续写】」「改写：」这类前缀，或者包一层代码围栏。
 * 写进正文前统一清掉；只清理开头的短标签，不动机器人正文里的方括号。
 */
object AiOutputSanitizer {
    private val fenceStart = Regex("^\\s*```[a-zA-Z]*\\s*")
    private val fenceEnd = Regex("\\s*```\\s*\\z")
    private val bracketLabel = Regex("^\\s*[【\\[]([^】\\]]{1,16})[】\\]]\\s*")
    private val colonLabel = Regex("^\\s*(续写|改写|润色|检查结果|结果|正文)\\s*[:：]\\s*")
    /** 模型会照抄上下文里的段落标记（[[b11]] / [b11]），写进正文就是脏数据。 */
    private val paragraphMarker = Regex("^\\s*\\[\\[?\\s*b\\d+\\s*\\]\\]?\\s*")
    private val labelHints = listOf("续写", "改写", "润色", "检查", "结果", "模拟", "正文", "AI")

    fun clean(raw: String): String {
        var text = raw.trim()
        text = fenceStart.replace(text, "")
        text = fenceEnd.replace(text, "")
        var changed = true
        var guard = 0
        while (changed && guard < 4) {
            changed = false
            val bracket = bracketLabel.find(text)
            if (bracket != null && labelHints.any { bracket.groupValues[1].contains(it) }) {
                text = text.removeRange(bracket.range).trim()
                changed = true
            }
            val colon = colonLabel.find(text)
            if (colon != null) {
                text = text.removeRange(colon.range).trim()
                changed = true
            }
            val marker = paragraphMarker.find(text)
            if (marker != null) {
                text = text.removeRange(marker.range).trim()
                changed = true
            }
            guard++
        }
        text = text.replace(Regex("\n{3,}"), "\n\n")
        return text.trim()
    }
}
