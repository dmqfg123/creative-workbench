package com.creativeworkbench.writer.core.data

/**
 * 大纲模板。三套公开的结构化写作范式（不是自创）：
 * - 三幕剧（Syd Field 的 Paradigm）
 * - 救猫咪 15 拍（Blake Snyder, Save the Cat）
 * - 英雄之旅 12 阶段（Campbell / Vogler）
 *
 * 格式用「标题 | 要点」的纯文本约定，而不是写死的字段枚举——
 * 抄 Novelcrafter「Create from Outline」的思路：可解析的纯文本比表单扩展性强。
 * 一行 = 一个 beat（约 500–800 字正文，Novelcrafter 口径）。
 */
object OutlineTemplates {

    data class Template(val id: String, val name: String, val note: String, val beats: List<Pair<String, String>>)

    private fun parse(raw: String): List<Pair<String, String>> = raw.trim().lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { line ->
            val parts = line.split('|', limit = 2)
            parts[0].trim() to parts.getOrElse(1) { "" }.trim()
        }

    val threeActs = Template(
        id = "three_acts",
        name = "三幕剧",
        note = "6 拍 · 建置 / 对抗 / 结局",
        beats = parse(
            """
            第一幕·建置 | 交代世界与主角的日常，让读者先在乎他
            第一幕·激励事件 | 打破平衡的那件事，主角不得不动
            第一幕·转折 | 主角做出选择，进入无法回头的处境
            第二幕·对抗升级 | 阻力一层层加码，主角的旧办法失效
            第二幕·中点 | 真相或代价揭开一半，目标被迫改写
            第三幕·高潮 | 正面对决，主角用新的方式解决
            第三幕·结局 | 收拾残局，交代变化后的世界
            """.trimIndent()
        )
    )

    val saveTheCat = Template(
        id = "save_the_cat",
        name = "救猫咪 15 拍",
        note = "Blake Snyder · 商业类型片常用",
        beats = parse(
            """
            开场画面 | 用一个画面定调世界与主角状态
            主题呈现 | 有人随口说出全书的主题句
            铺垫 | 展示主角的缺陷与六件要做的事
            催化剂 | 外部事件打破日常
            争论 | 主角犹豫、抗拒，直到无路可退
            进入第二幕 | 主动做出选择，进入新世界
            B 故事 | 情感线/副线登场，承载主题
            游戏时间 | 兑现类型承诺：好看的部分
            中点 | 假胜利或假失败，赌注抬高
            坏人逼近 | 外部压力和内部裂缝同时收紧
            一无所有 | 失去最重要的东西，出现"死亡气息"
            灵魂黑夜 | 主角沉到底，想明白自己错在哪
            进入第三幕 | A 线与 B 线合流，拿到新办法
            结局 | 用行动证明改变，解决所有线
            终场画面 | 与开场呼应，展示变化
            """.trimIndent()
        )
    )

    val heroJourney = Template(
        id = "hero_journey",
        name = "英雄之旅",
        note = "12 阶段 · 成长型故事",
        beats = parse(
            """
            平凡世界 | 主角的日常与局限
            冒险召唤 | 让他离开日常的事件
            拒斥召唤 | 害怕、找理由不去
            遇见导师 | 给他工具、经验或一句关键的话
            跨越第一道门槛 | 主动走进未知
            试炼·盟友·敌人 | 新世界的规则与关系
            接近最深的洞穴 | 逼近核心危险
            磨难 | 与死亡擦身，旧我死去
            报酬 | 拿到宝物或真相
            返回之路 | 带着代价往回走，被追
            复活 | 最后一次考验，用新我通过
            带着灵药归来 | 回到平凡世界，带来改变
            """.trimIndent()
        )
    )

    val all = listOf(threeActs, saveTheCat, heroJourney)
}
