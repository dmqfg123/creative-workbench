package com.creativeworkbench.writer.ui.common

/**
 * 列表滚动位置记忆（进程内）。
 *
 * 为什么不用 rememberSaveable：Compose 的 SaveableStateHolder 只覆盖配置变更/同一返回栈，
 * 从列表页进详情再返回时 composable 已销毁，位置就丢了。列表位置是「体感」问题，
 * 一个进程内的 map 就够（杀进程后回到顶部也能接受）。
 */
object ScrollMemory {
    private class Pos(var index: Int = 0, var offset: Int = 0)
    private val map = HashMap<String, Pos>()

    fun get(key: String): Pair<Int, Int> {
        val pos = map[key] ?: return 0 to 0
        return pos.index to pos.offset
    }

    fun save(key: String, index: Int, offset: Int) {
        map[key] = Pos(index, offset)
    }
}
