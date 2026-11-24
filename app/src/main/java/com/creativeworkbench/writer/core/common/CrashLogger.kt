package com.creativeworkbench.writer.core.common

import android.content.Context
import com.creativeworkbench.writer.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志只写本地：类型 + 堆栈 + 时间 + 版本。
 * 不联网、不上报；授权字段在这里统一脱敏（只写异常信息，不写请求体）。
 */
object CrashLogger {
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun write(context: Context, thread: Thread, throwable: Throwable): File? {
        val dir = File(context.filesDir, "workbench/logs").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(Date())
        val file = File(dir, "crash-" + stamp + ".txt")
        val text = buildString {
            appendLine("time: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date()))
            appendLine("version: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")
            appendLine("thread: " + thread.name)
            appendLine("type: " + throwable.javaClass.name)
            appendLine("message: " + (throwable.message ?: ""))
            appendLine("stack:")
            appendLine(throwable.stackTraceToString())
        }
        dir.setReadable(true)
        return runCatching { file.writeText(text); file }.getOrNull()
    }

    fun list(context: Context): List<File> {
        val dir = File(context.filesDir, "workbench/logs")
        return dir.listFiles()?.filter { it.name.startsWith("crash-") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    fun triggerDebugCrash() {
        throw RuntimeException("调试用崩溃：从设置页手动触发")
    }
}
