package com.creativeworkbench.writer.core.data

import java.util.UUID

/**
 * journal 回放。抽成纯函数是为了能单测：
 * 这里错了，崩溃恢复会把已删段落复活、把新段甩到章末——而且只有崩溃时才看得出来。
 */
object JournalReplay {

    /**
     * 先用最后一条 layout 还原顺序（删除的段自然不在里面），再用 block 记录覆盖文本。
     * 旧格式（只有 block 记录、没有 layout）按老办法追加到末尾。
     */
    fun apply(base: List<Block>, records: List<JournalRecord>): List<Block> {
        if (records.isEmpty()) return base
        val texts = LinkedHashMap<String, String>()
        base.forEach { texts[it.id] = it.text }
        var order: List<String> = base.map { it.id }
        var sawLayout = false
        records.sortedBy { it.seq }.forEach { record ->
            when (record.kind) {
                JournalRecord.KIND_LAYOUT -> {
                    order = record.order
                    sawLayout = true
                }
                else -> if (record.blockId.isNotEmpty()) texts[record.blockId] = record.text
            }
        }
        val rebuilt = order.mapNotNull { id -> texts[id]?.let { Block(id, it) } }.toMutableList()
        if (!sawLayout) {
            texts.forEach { (id, text) ->
                if (rebuilt.none { it.id == id }) rebuilt.add(Block(id, text))
            }
        }
        if (rebuilt.isEmpty()) rebuilt.add(Block(UUID.randomUUID().toString(), ""))
        return rebuilt
    }
}
