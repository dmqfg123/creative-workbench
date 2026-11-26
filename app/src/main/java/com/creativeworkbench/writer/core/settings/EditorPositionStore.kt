package com.creativeworkbench.writer.core.settings

import android.content.Context

/**
 * 编辑位置：上次写到哪一章、光标在第几个字、滚动到全文的百分之几。
 *
 * 为什么单独存一份而不是塞进 Room：这是「界面状态」，丢了最多回到段首，
 * 不值得为它加表、加迁移、加备份（也不该被备份覆盖——换了设备位置本来就不该跟过去）。
 * 参考 Slima 的 Resume：离开编辑器时记「哪本书 / 哪个文件 / 光标 / 滚动」，回来直接落回原处。
 *
 * 存**比例**而不是像素：系统字号、屏幕宽度、行距都会改变像素偏移，
 * 像素值在换字号后会把用户丢到别处；比例至少能落在同一段附近。
 */
data class EditorPosition(
    val chapterId: String,
    val caret: Int,
    /** 0f–1f，滚动比例（备用/展示）。 */
    val scrollRatio: Float,
    /**
     * 视口顶部那一行的**首字符偏移**——主锚点。
     * 抄自阅读器（anx-reader 的 CurrentReadingState: cfi + percentage 双存）：
     * 字符偏移在换字号、换屏宽后仍然指向同一句话，而像素/比例会漂。
     */
    val topChar: Int = 0,
    val updatedAtMs: Long
)

class EditorPositionStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("editor_positions", Context.MODE_PRIVATE)

    fun save(chapterId: String, caret: Int, scrollRatio: Float, topChar: Int = 0) {
        android.util.Log.i("WB_POS", "save " + chapterId.take(8) + " caret=" + caret +
            " ratio=" + String.format(java.util.Locale.US, "%.3f", scrollRatio) + " top=" + topChar)
        prefs.edit()
            .putInt(chapterId + ":caret", caret.coerceAtLeast(0))
            .putFloat(chapterId + ":ratio", scrollRatio.coerceIn(0f, 1f))
            .putInt(chapterId + ":top", topChar.coerceAtLeast(0))
            .putLong(chapterId + ":at", System.currentTimeMillis())
            .apply()
    }

    fun load(chapterId: String): EditorPosition? {
        val at = prefs.getLong(chapterId + ":at", 0L)
        if (at <= 0L) {
            android.util.Log.i("WB_POS", "load " + chapterId.take(8) + " -> none")
            return null
        }
        return EditorPosition(
            chapterId = chapterId,
            caret = prefs.getInt(chapterId + ":caret", 0),
            scrollRatio = prefs.getFloat(chapterId + ":ratio", 0f),
            topChar = prefs.getInt(chapterId + ":top", 0),
            updatedAtMs = at
        ).also {
            android.util.Log.i(
                "WB_POS",
                "load " + chapterId.take(8) + " -> caret=" + it.caret + " ratio=" +
                    String.format(java.util.Locale.US, "%.3f", it.scrollRatio) + " top=" + it.topChar
            )
        }
    }

    /** 这本书上次编辑的是哪一章（书架点进来要用它，而不是「最近保存的那一章」）。 */
    fun setLastChapter(workId: String, chapterId: String) {
        prefs.edit().putString("work:" + workId + ":chapter", chapterId).apply()
    }

    fun lastChapter(workId: String): String? =
        prefs.getString("work:" + workId + ":chapter", null)?.takeIf { it.isNotBlank() }

    fun forget(chapterId: String) {
        prefs.edit()
            .remove(chapterId + ":caret")
            .remove(chapterId + ":ratio")
            .remove(chapterId + ":top")
            .remove(chapterId + ":at")
            .apply()
    }
}
