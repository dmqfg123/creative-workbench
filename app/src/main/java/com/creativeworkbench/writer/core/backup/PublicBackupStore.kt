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

        // 同一天的旧文件先删掉，避免出现 (1)(2)
        runCatching {
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                arrayOf(name, relativePath),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    resolver.delete(
                        android.content.ContentUris.withAppendedId(collection, id),
                        null,
                        null
                    )
                }
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/gzip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("无法创建备份文件")
        resolver.openOutputStream(uri)?.use { out -> out.write(bytes) } ?: error("无法写入备份文件")
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        uri
    }
}
