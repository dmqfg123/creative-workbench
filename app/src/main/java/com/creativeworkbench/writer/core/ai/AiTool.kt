package com.creativeworkbench.writer.core.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一次工具调用（模型决定，平台执行）。
 * arguments 是模型给的 JSON 字符串——故意不预先解析：参数不合法时要能在执行处给出可读错误，
 * 而不是在解析时炸掉整轮对话。
 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

/**
 * 工具声明。mutating = 会改项目数据：这类工具**不直接执行**，
 * 只生成一张「待确认」卡片，用户点「应用」才落库（红线：写入必须二次确认）。
 */
data class AiToolSpec(
    val name: String,
    val description: String,
    val parameters: JSONObject,
    val mutating: Boolean = false,
    /** 界面分组：读取 / 笔记 / 大纲 / 正文 / 伏笔。 */
    val category: String = "读取"
) {
    fun toJson(): JSONObject = JSONObject().put("type", "function").put(
        "function",
        JSONObject()
            .put("name", name)
            .put("description", description)
            .put("parameters", parameters)
    )
}

/** 工具执行结果：读操作直接给数据，写操作只给提案。 */
sealed interface ToolOutcome {
    /** 回给模型的内容（读结果/写提案的回执）。 */
    data class Data(val text: String) : ToolOutcome
    data class Proposal(val proposal: ToolProposal) : ToolOutcome
    data class Failure(val message: String) : ToolOutcome
}

/**
 * 一条待确认的写入提案。用 Map 存参数：13 个工具不值得 13 个 sealed 子类，
 * 而且提案要能跟着对话状态走、将来还要能序列化到历史里。
 */
data class ToolProposal(
    val id: String,
    val toolName: String,
    /** 动作名：新建笔记 / 写入正文… */
    val label: String,
    /** 一句话标题：笔记《林小满》 */
    val title: String,
    /** 详情预览：正文前 400 字。 */
    val preview: String,
    /** 目标板块：笔记 / 大纲 / 正文 / 伏笔。 */
    val target: String,
    val args: Map<String, String>,
    var applied: Boolean = false,
    var applyError: String? = null
)

/** JSON Schema 小工具：只为把工具参数写清楚，不做通用 schema 库。 */
object ToolSchema {

    fun obj(required: List<String>, vararg props: Pair<String, JSONObject>): JSONObject {
        val properties = JSONObject()
        props.forEach { (key, value) -> properties.put(key, value) }
        return JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("required", JSONArray(required))
            .put("additionalProperties", false)
    }

    fun str(desc: String, vararg enumValues: String): JSONObject {
        val json = JSONObject().put("type", "string").put("description", desc)
        if (enumValues.isNotEmpty()) {
            json.put("enum", JSONArray().apply { enumValues.forEach { put(it) } })
        }
        return json
    }

    fun int(desc: String): JSONObject =
        JSONObject().put("type", "integer").put("description", desc)

    /** 全部工具声明 → 请求体里的 tools 数组。 */
    fun arrayOf(specs: List<AiToolSpec>): JSONArray = JSONArray().apply {
        specs.forEach { put(it.toJson()) }
    }
}
