package com.creativeworkbench.writer.core.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志用的轻量面包屑：当前页面 + 最近 20 条操作。
 * 只在内存里，不落盘、不联网；异常发生时由 CrashLogger 一次性写进日志。
 */
object Breadcrumbs {
    private const val MAX = 20
    private val items = ArrayDeque<String>()

    @Volatile
    var currentPage: String = "未知"
        private set

    fun page(name: String) {
        currentPage = name
        push("页面：" + name)
    }

    fun record(text: String) {
        push(text)
    }

    private fun push(text: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())
        synchronized(items) {
            items.addLast(stamp + " " + text)
            while (items.size > MAX) items.removeFirst()
        }
    }

    fun snapshot(): List<String> = synchronized(items) { items.toList() }
}