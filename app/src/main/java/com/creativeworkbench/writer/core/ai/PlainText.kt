package com.creativeworkbench.writer.core.ai

/**
 * 对话窗口不给人设、不下格式指令——模型返回什么就是什么。
 * 但聊天气泡和笔记里显示 ### / ** / 表格会很脏，所以**只在显示与存笔记时**做一层纯文本化，
 * 不改模型收到的提示词。这样「不干预内容」和「界面不难看」两件事不冲突。
 */
object PlainText {
    private val heading = Regex("^\\s{0,3}#{1,6}\\s*", RegexOption.MULTILINE)
    private val bold = Regex("\\*\\*|__")
    private val fence = Regex("^\\s*```[a-zA-Z0-9]*\\s*$", RegexOption.MULTILINE)
    private val tablePipe = Regex("^\\s*\\|", RegexOption.MULTILINE)

    fun of(raw: String): String {
        var text = raw.replace("\r\n", "\n")
        text = fence.replace(text, "")
        text = heading.replace(text, "")
        text = bold.replace(text, "")
        // 表格行（| a | b |）：保留文字、去掉竖线，免得在窄气泡里挤成一团
        if (tablePipe.containsMatchIn(text)) {
            text = text.lines().joinToString("\n") { line ->
                if (line.trimStart().startsWith("|")) {
                    line.split('|').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("  ")
                } else {
                    line
                }
            }
        }
        text = text.replace(Regex("\n{3,}"), "\n\n")
        return text.trim()
    }
}
