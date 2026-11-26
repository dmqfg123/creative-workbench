package com.creativeworkbench.writer.core.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * 导入 SillyTavern（酒馆）的聊天补全预设。
 *
 * 只取对我们有意义的四块，其余（世界书、角色卡、正则、代理设置）不导入：
 * - 系统提示：main / system_prompt / jailbreak / nsfw（按 prompt_order 排序，没有就按固定顺序）
 * - 后期指令：post_history_instructions（放在最后一条 user 后面，酒馆就是这么用的）
 * - 助手预填：assistant_prefill（最后一条 assistant，模型的续写起点）
 *
 * 兼容两种 JSON：完整的 ST 预设（含 prompts 数组）和导出的 settings.json（含 openai_settings 数组）。
 * 解析失败不抛异常，返回带 error 的结果，界面照常可用。
 */
data class StPreset(
    val system: String = "",
    val postHistory: String = "",
    val prefill: String = "",
    val imported: List<String> = emptyList(),
    val error: String? = null
) {
    val isEmpty: Boolean get() = system.isBlank() && postHistory.isBlank() && prefill.isBlank()
}

object StPresetParser {

    /** 系统侧按这个顺序拼（酒馆默认的顺序；prompt_order 存在时以它为准）。 */
    private val SYSTEM_ORDER = listOf(
        "main", "system_prompt", "jailbreak", "nsfw",
        "worldInfoBefore", "worldInfoAfter"
    )

    private const val MAX_SYSTEM = 12_000
    private const val MAX_POST = 4_000
    private const val MAX_PREFILL = 400

    fun parse(raw: String): StPreset {
        val root = runCatching { JSONObject(raw) }.getOrNull()
            ?: return StPreset(error = "不是合法 JSON")
        // settings.json 里是 openai_settings: [preset, ...]，取第一个
        val preset = root.optJSONArray("openai_settings")
            ?.optJSONObject(0)
            ?: root
        val prompts = root.optJSONArray("prompts")
            ?: preset.optJSONArray("prompts")
        val byId = linkedMapOf<String, String>()
        if (prompts != null) {
            for (i in 0 until prompts.length()) {
                val item = prompts.optJSONObject(i) ?: continue
                val id = item.optString("identifier")
                val content = item.optString("content")
                if (id.isNotBlank() && content.isNotBlank()) byId[id] = content.trim()
            }
        }
        // 旧版预设是平铺字段
        root.optString("main_prompt").takeIf { it.isNotBlank() }?.let { byId.putIfAbsent("main", it) }
        root.optString("jailbreak_prompt").takeIf { it.isNotBlank() }?.let { byId.putIfAbsent("jailbreak", it) }
        root.optString("nsfw_prompt").takeIf { it.isNotBlank() }?.let { byId.putIfAbsent("nsfw", it) }

        val order = readOrder(root, preset).ifEmpty { SYSTEM_ORDER }
        val used = mutableListOf<String>()
        val system = if (order.isNotEmpty()) {
            order.mapNotNull { id ->
                byId[id]?.takeIf { it.isNotBlank() }?.also { used.add(id) }
            }.joinToString("\n\n")
        } else {
            ""
        }

        val post = (root.optString("post_history_instructions").ifBlank { byId["post_history_instructions"].orEmpty() })
            .trim()
            .take(MAX_POST)
        val prefill = (root.optString("assistant_prefill").ifBlank { preset.optString("assistant_prefill") })
            .trim()
            .take(MAX_PREFILL)

        val result = StPreset(
            system = system.take(MAX_SYSTEM),
            postHistory = post,
            prefill = prefill,
            imported = used + listOfNotNull(
                "post_history_instructions".takeIf { post.isNotBlank() },
                "assistant_prefill".takeIf { prefill.isNotBlank() }
            )
        )
        return if (result.isEmpty) result.copy(error = "这个 JSON 里没找到可用的提示词（不认识这个格式？）") else result
    }

    /** prompt_order 有三种写法：数组、[{order:[...]}]、[{character_id, order:[{identifier}]}]。 */
    private fun readOrder(root: JSONObject, preset: JSONObject): List<String> {
        val raw = root.optJSONArray("prompt_order") ?: preset.optJSONArray("prompt_order") ?: return emptyList()
        if (raw.length() == 0) return emptyList()
        val first = raw.opt(0)
        val order = when (first) {
            is JSONArray -> first
            is JSONObject -> first.optJSONArray("order")
            else -> null
        } ?: return emptyList()
        val ids = mutableListOf<String>()
        for (i in 0 until order.length()) {
            val item = order.opt(i)
            val id = when (item) {
                is JSONObject -> item.optString("identifier")
                is String -> item
                else -> ""
            }
            if (id.isNotBlank()) ids.add(id)
        }
        return ids
    }
}
