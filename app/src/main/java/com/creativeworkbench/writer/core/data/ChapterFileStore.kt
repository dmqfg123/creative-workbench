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

/**
 * journal 记录（NDJSON，一行一条）：
 * - kind="layout"：这一批的段落顺序（order 是 id 列表）。删除/插入/换位只有它能表达。
 * - kind="block"：单段文本。
 * 旧版本只写 block 记录，缺 kind/order 时按 block 解析，仍能读。
 */
@Serializable
data class JournalRecord(
    val seq: Long,
    val kind: String = KIND_BLOCK,
    val order: List<String> = emptyList(),
    val blockId: String = "",
    val text: String = "",
    val crc32: Long = 0L,
    val ts: Long = 0L
) {
    companion object {
        const val KIND_BLOCK = "block"
        const val KIND_LAYOUT = "layout"
    }
}

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

    /** 读一份快照文件（恢复历史用）。 */
    fun readSnapshotFile(path: String): ChapterContent? = runCatching {
        val file = File(path)
        if (!file.exists()) return null
        json.decodeFromString<ChapterContent>(file.readText(Charsets.UTF_8))
    }.getOrNull()

    fun deleteSnapshot(path: String) {
        runCatching { File(path).delete() }
    }

    fun deleteSnapshots(chapterId: String) {
        runCatching { File(snapshotsDir, chapterId).deleteRecursively() }
    }

    /** 清空全部快照（clearAll 用）：整棵目录删掉，别留空壳。 */
    fun deleteAllSnapshots() {
        runCatching { snapshotsDir.deleteRecursively() }
        runCatching { snapshotsDir.mkdirs() }
    }
    fun journalFile(chapterId: String): File = File(chaptersDir, chapterId + ".journal")
    fun backupFile(chapterId: String): File = File(chaptersDir, chapterId + ".json.bak")

    fun read(chapterId: String): ChapterContent? {
        val file = contentFile(chapterId)
        if (!file.exists()) return null
        // 解析失败必须抛出，不能当成空章节覆盖原文件。
        return json.decodeFromString<ChapterContent>(file.readText(Charsets.UTF_8))
    }

    sealed interface Salvage {
        data class Found(val content: ChapterContent, val source: String) : Salvage
        data class Unreadable(val source: String, val detail: String) : Salvage
    }

    /**
     * 正式文件不在时，按「导入暂存 → 上一版备份」的顺序找回来。
     * 以前这里直接造一个空章节并落盘，等于把一次可救的丢文件固化成「这一章就是空的」。
     */
    fun salvage(chapterId: String): Salvage? {
        val candidates = listOf(
            File(chaptersDir, chapterId + ".json.import") to ".import 暂存",
            File(chaptersDir, chapterId + ".json.bak") to ".bak 上一版"
        )
        var lastError: Salvage.Unreadable? = null
        candidates.forEach { (file, label) ->
            if (!file.exists()) return@forEach
            runCatching { json.decodeFromString<ChapterContent>(file.readText(Charsets.UTF_8)) }
                .onSuccess { content -> return Salvage.Found(content, label) }
                .onFailure { error ->
                    lastError = Salvage.Unreadable(label, error.message ?: "解析失败")
                }
        }
        return lastError
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
        fsyncDir(chaptersDir)
    }

    /** 导入用：先写 .import 暂存文件，数据库换完再 commitImport 换名。 */
    fun writeImport(content: ChapterContent) {
        val tmp = File(chaptersDir, content.chapterId + ".json.import")
        val bytes = json.encodeToString(content).toByteArray(Charsets.UTF_8)
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
    }

    /** 预检：这份暂存文件存在且能解析。导入前必须全过，否则不能动数据库。 */
    fun hasReadableImport(chapterId: String): Boolean {
        val file = File(chaptersDir, chapterId + ".json.import")
        if (!file.exists()) return false
        return runCatching {
            json.decodeFromString<ChapterContent>(file.readText(Charsets.UTF_8))
        }.isSuccess
    }

    /** 导入失败时清掉本次留下的暂存文件。 */
    fun discardImports(chapterIds: Collection<String>) {
        chapterIds.forEach { runCatching { File(chaptersDir, it + ".json.import").delete() } }
    }

    /** 把 .import 原子换成正式章节文件；没有暂存文件时忽略。 */
    fun commitImport(chapterId: String) {
        val tmp = File(chaptersDir, chapterId + ".json.import")
        if (!tmp.exists()) return
        val target = contentFile(chapterId)
        // 换名之前先把旧正文留成 .bak：DB 事务失败时能整章回滚
        if (target.exists()) runCatching { target.renameTo(backupFile(chapterId)) }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        fsyncDir(chaptersDir)
    }

    /** 导入的 DB 事务失败时：把 .bak 换回正式文件（没有 .bak 说明是新增章，删掉即可）。 */
    fun rollbackImport(chapterIds: Collection<String>) {
        chapterIds.forEach { chapterId ->
            runCatching {
                val target = contentFile(chapterId)
                val backup = backupFile(chapterId)
                if (backup.exists()) {
                    target.delete()
                    backup.renameTo(target)
                } else {
                    target.delete()
                }
            }
        }
        fsyncDir(chaptersDir)
    }

    /** 删掉不属于本次导入的章节文件与残留暂存文件。 */
    fun deleteOtherThan(keep: Set<String>) {
        chaptersDir.listFiles()?.forEach { file ->
            val name = file.name
            val chapterId = name.substringBefore('.')
            if (name.endsWith(".json.import") || name.endsWith(".journal") || name.endsWith(".json") || name.endsWith(".json.bak")) {
                if (chapterId !in keep) runCatching { file.delete() }
            }
        }
    }

    /** 目录项也 fsync，避免掉电时 rename 只落一半。 */
    private fun fsyncDir(dir: File) {
        runCatching {
            java.nio.channels.FileChannel.open(dir.toPath(), java.nio.file.StandardOpenOption.READ)
                .use { it.force(true) }
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
