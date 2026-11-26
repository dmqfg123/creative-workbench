package com.creativeworkbench.writer.core.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

/**
 * 每日备份写到公共目录：Download/雨刮模拟器备份/
 * 用 MediaStore，不需要选文件夹、不需要存储权限；文件管理器里一眼能看到。
 * 这个目录不随卸载消失（和 App 私有目录不同）。
 */
object PublicBackupStore {
    const val FOLDER = "雨刮模拟器备份"

    fun write(context: Context, name: String, bytes: ByteArray): Result<Uri> = runCatching {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER + "/"

        // 先记下同名的旧文件，等新的写成功再删：先删后写的话，第二步失败当天那份就没了。
        var previousId = -1L
        runCatching {
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                arrayOf(name, relativePath),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) previousId = cursor.getLong(0)
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/gzip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("无法创建备份文件")
        runCatching {
            resolver.openOutputStream(uri)?.use { out -> out.write(bytes) } ?: error("无法写入备份文件")
        }.onFailure { error ->
            // 写失败就把这条半成品删掉，别留一个 0 字节的「今日备份」
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        // 新的已经落盘，再清掉同名的旧行，避免 Download 里出现 (1)(2)
        if (previousId > 0) {
            runCatching {
                resolver.delete(android.content.ContentUris.withAppendedId(collection, previousId), null, null)
            }
        }
        uri
    }
}
