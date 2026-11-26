package com.creativeworkbench.writer.core.backup

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 整机迁移包（.zip）：manifest.json + backup.json + plugins/…。
 * 只做纯字节与 zip 逻辑，不依赖 Android，方便 JVM 单测。
 * 读取时按 PackArchive 同样的规则防路径穿越、限单文件与总量。
 */
object BackupZip {
    const val MANIFEST = "manifest.json"
    const val BACKUP = "backup.json"
    const val PLUGIN_PREFIX = "plugins/"
    private const val MAX_ENTRY_BYTES = 32L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 128L * 1024 * 1024
    private const val MAX_ENTRIES = 2000

    data class Content(
        val backupJson: String?,
        val plugins: Map<String, ByteArray>,
        val error: String? = null
    )

    fun write(
        out: java.io.OutputStream,
        backupJson: String,
        plugins: Map<String, ByteArray>,
        manifest: Map<String, String>
    ) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(JSONObject(manifest as Map<*, *>).toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(BACKUP))
            zip.write(backupJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            plugins.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(PLUGIN_PREFIX + name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    fun read(input: java.io.InputStream): Content {
        val plugins = mutableMapOf<String, ByteArray>()
        var backup: String? = null
        var total = 0L
        var count = 0
        val errors = mutableListOf<String>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                count++
                if (count > MAX_ENTRIES) {
                    errors.add("条目超过 " + MAX_ENTRIES + " 个")
                    break
                }
                val name = entry.name
                if (entry.isDirectory) continue
                if (unsafe(name)) {
                    errors.add("非法路径：" + name)
                    continue
                }
                val bytes = readCapped(zip) ?: run {
                    errors.add("单文件超过 32 MB：" + name)
                    null
                } ?: continue
                total += bytes.size
                if (total > MAX_TOTAL_BYTES) {
                    errors.add("解压后总量超过 128 MB")
                    break
                }
                when {
                    name == BACKUP -> backup = bytes.toString(Charsets.UTF_8)
                    name.startsWith(PLUGIN_PREFIX) -> plugins[name.removePrefix(PLUGIN_PREFIX)] = bytes
                }
            }
        }
        val error = when {
            errors.isNotEmpty() -> errors.joinToString("；")
            backup.isNullOrBlank() -> "压缩包里没有 backup.json"
            else -> null
        }
        return Content(backup, plugins, error)
    }

    fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            bytes[2] == 3.toByte() && bytes[3] == 4.toByte()

    private fun unsafe(name: String): Boolean =
        name.isBlank() || name.startsWith("/") || name.contains("..") ||
            name.contains('\\') || name.contains(':')

    private fun readCapped(zip: ZipInputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = zip.read(buffer)
            if (read <= 0) break
            out.write(buffer, 0, read)
            if (out.size() > MAX_ENTRY_BYTES) return null
        }
        return out.toByteArray()
    }
}
