package com.creativeworkbench.writer.core.ai

import org.json.JSONObject

data class AiIssue(
    val blockId: String,
    val excerpt: String,
    val severity: String,
    val message: String,
    /** 建议改成的文字；空 = 只能自己改（例如逻辑矛盾类问题） */
    val suggestion: String = ""
)

object AiIssueParser {
    /** 模型经常带代码围栏、尾逗号或前后解释：先做容错提取，再解析。 */
    fun parse(raw: String): List<AiIssue>? {
        val cleaned = JsonSalvage.extractObject(raw) ?: return null
        return runCatching {
            val root = JSONObject(cleaned)
            val array = root.optJSONArray("issues") ?: return emptyList()
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                AiIssue(
                    blockId = item.optString("blockId"),
                    excerpt = item.optString("excerpt"),
                    severity = item.optString("severity", "low"),
                    message = item.optString("message"),
                    suggestion = item.optString("suggestion")
                )
            }
        }.getOrNull()
    }
}
