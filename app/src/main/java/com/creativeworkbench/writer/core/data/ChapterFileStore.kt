package com.creativeworkbench.writer.core.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32

@Serializable
data class Block(val id: String, val text: String)

@Serializable
data class ChapterContent(
    val chapterId: String,
    val revision: Long,
    val blocks: List<Block>,
    val updatedAt: Long
)

@Serializable
data class JournalRecord(
    val seq: Long,
    val blockId: String,
    val text: String,
    val crc32: Long,
    val ts: Long
)

class ChapterFileStore(context: Context) {
    private val root = File(context.filesDir, "workbench")
    private val chaptersDir = File(root, "chapters")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        chaptersDir.mkdirs()
    }

    private val snapshotsDir = File(root, "snapshots")

    fun contentFile(chapterId: String): File = File(chaptersDir, chapterId + ".json")

    /** 自动快照：写入前兜底，v1 不做恢复 UI。 */
    fun writeSnapshot(chapterId: String, createdAtMs: Long, seq: Int, content: ChapterContent): File {
        val dir = File(snapshotsDir, chapterId).apply { mkdirs() }
        val file = File(dir, createdAtMs.toString() + "-" + seq + ".json")
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeText(json.encodeToString(content))
        if (file.exists()) file.delete()
        tmp.renameTo(file)
        return file
    }

    fun deleteSnapshot(path: String) {
        runCatching { File(path).delete() }
    }

    fun deleteSnapshots(chapterId: String) {
        runCatching { File(snapshotsDir, chapterId).deleteRecursively() }
    }
    fun journalFile(chapterId: String): File = File(chaptersDir, chapterId + ".journal")
    fun backupFile(chapterId: String): File = File(chaptersDir, chapterId + ".json.bak")

    fun read(chapterId: String): ChapterContent? {
        val file = contentFile(chapterId)
        if (!file.exists()) return null
        // 解析失败必须抛出，不能当成空章节覆盖原文件。
        return json.decodeFromString<ChapterContent>(file.readText(Charsets.UTF_8))
    }

    fun write(content: ChapterContent) {
        val target = contentFile(content.chapterId)
        val tmp = File(chaptersDir, content.chapterId + ".json.tmp")
        val bytes = json.encodeToString(content).toByteArray(Charsets.UTF_8)
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
        if (target.exists()) {
            runCatching { target.copyTo(backupFile(content.chapterId), overwrite = true) }
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    fun appendJournal(chapterId: String, record: JournalRecord) {
        appendJournalBatch(chapterId, listOf(record))
    }

    fun appendJournalBatch(chapterId: String, records: List<JournalRecord>) {
        if (records.isEmpty()) return
        val text = records.joinToString(separator = "") { json.encodeToString(it) + "\n" }
        val bytes = text.toByteArray(Charsets.UTF_8)
        FileOutputStream(journalFile(chapterId), true).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
    }

    fun readJournal(chapterId: String): List<JournalRecord> {
        val file = journalFile(chapterId)
        if (!file.exists()) return emptyList()
        val result = ArrayList<JournalRecord>()
        file.forEachLine(Charsets.UTF_8) { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) {
                val record = runCatching { json.decodeFromString<JournalRecord>(trimmed) }.getOrNull()
                if (record != null && record.crc32 == crc32(record.text)) {
                    result.add(record)
                } else {
                    return@forEachLine
                }
            }
        }
        return result
    }

    fun clearJournal(chapterId: String) {
        journalFile(chapterId).delete()
    }

    fun delete(chapterId: String) {
        contentFile(chapterId).delete()
        backupFile(chapterId).delete()
        journalFile(chapterId).delete()
    }

    fun crc32(text: String): Long {
        val crc = CRC32()
        crc.update(text.toByteArray(Charsets.UTF_8))
        return crc.value
    }
}
