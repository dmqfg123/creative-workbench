package com.creativeworkbench.writer.core.ai

import org.json.JSONTokener

/**
 * 模型输出里捞 JSON：字符串感知的括号配平 + 常见修复。
 * 参考 dsh-Go 直接移植 jsonrepair 的做法，但只做我们实际需要的一小步：
 * 取第一个完整对象、去尾逗号、把当引号用的智能引号换成 ASCII 引号。
 * 修不好就返回 null，调用方仍走「原文展示、不写入」的降级。
 */
object JsonSalvage {

    fun extractObject(raw: String): String? {
        val cleaned = raw
            .replace(Regex("^\\s*```(json|JSON|javascript)?\\s*"), "")
            .replace(Regex("\\s*```\\s*\\z"), "")
            .trim()
        val start = cleaned.indexOf('{')
        if (start < 0) return null
        val balanced = balancedSlice(cleaned, start) ?: return null
        val repaired = repair(balanced)
        return runCatching {
            JSONTokener(repaired).nextValue()
            repaired
        }.getOrNull()
    }

    /** 从 start（必须是 '{'）起，按字符串/转义状态找配对的 '}'。 */
    fun balancedSlice(text: String, start: Int): String? {
        if (start < 0 || start >= text.length || text[start] != '{') return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val ch = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
            } else {
                when (ch) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return text.substring(start, index + 1)
                    }
                }
            }
        }
        return null
    }

    /** 去尾逗号 + 智能引号规范化（只在没有 ASCII 双引号时替换，避免弄坏正文里的引号）。 */
    fun repair(text: String): String {
        var value = text
        if (!value.contains('"')) {
            value = value
                .replace('\u201c', '"')
                .replace('\u201d', '"')
        }
        val out = StringBuilder(value.length)
        var inString = false
        var escaped = false
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            if (inString) {
                out.append(ch)
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
            } else {
                when {
                    ch == '"' -> {
                        inString = true
                        out.append(ch)
                    }
                    ch == ',' -> {
                        var next = index + 1
                        while (next < value.length && value[next].isWhitespace()) next++
                        if (next < value.length && (value[next] == '}' || value[next] == ']')) {
                            // 尾逗号：丢掉
                        } else {
                            out.append(ch)
                        }
                    }
                    else -> out.append(ch)
                }
            }
            index++
        }
        return out.toString()
    }
}
