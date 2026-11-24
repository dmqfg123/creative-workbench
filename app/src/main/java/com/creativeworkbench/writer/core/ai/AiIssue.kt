package com.creativeworkbench.writer.core.ai

import org.json.JSONObject

data class AiIssue(
    val blockId: String,
    val excerpt: String,
    val severity: String,
    val message: String
)

object AiIssueParser {
    /** 模型经常带代码围栏或多余解释：先剥围栏，再只取第一个 JSON 对象。 */
    fun parse(raw: String): List<AiIssue>? {
        val cleaned = raw
            .replace(Regex("^\\s*```(json|JSON)?\\s*"), "")
            .replace(Regex("\\s*```\\s*\\z"), "")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            val root = JSONObject(cleaned.substring(start, end + 1))
            val array = root.optJSONArray("issues") ?: return emptyList()
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                AiIssue(
                    blockId = item.optString("blockId"),
                    excerpt = item.optString("excerpt"),
                    severity = item.optString("severity", "low"),
                    message = item.optString("message")
                )
            }
        }.getOrNull()
    }
}
