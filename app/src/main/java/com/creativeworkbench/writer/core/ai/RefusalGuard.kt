package com.creativeworkbench.writer.core.ai

/**
 * 识别「模型在拒绝」而不是在写。
 *
 * 背景：模型偶尔会用「平台社区规范 / 内容政策」这类说法拒写，甚至误报成某个具体平台的规则
 * （实测出现过一次「小红书规则」）。本 App 是作者本人在本机用的写作工具，没有平台、没有发布渠道，
 * 这类拒绝没有依据。识别出来后重试一次通常就能继续写。
 *
 * 只做保守判断：短回复 + 明确的拒绝词。长正文里出现「抱歉」不算拒绝。
 */
object RefusalGuard {

    private val markers = listOf(
        "我无法", "我不能", "无法协助", "不能协助", "无法提供", "不能提供",
        "抱歉", "对不起", "平台规则", "社区规范", "内容政策", "审核规则",
        "不符合相关", "换个话题", "无法满足"
    )

    /** 回复很短且带拒绝词 → 认为是在拒绝。 */
    fun looksLikeRefusal(text: String): Boolean {
        val value = text.trim()
        if (value.isEmpty()) return false
        // 正文里出现「对不起」很常见；只有「很短、且开头就是拒绝口吻」才算拒绝
        if (value.length > 300) return false
        val head = value.take(120)
        if (!markers.any { head.contains(it) }) return false
        // 有换行分段的大量正文说明是在写，不是拒绝
        if (value.count { it == '\n' } >= 3) return false
        return true
    }
}
