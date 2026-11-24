package com.creativeworkbench.writer.core.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

enum class UpdateSource { UserInput, AiWrite, Restore, Import }

data class OpenedChapter(val chapter: ChapterEntity, val content: ChapterContent)

sealed interface SaveResult {
    data class Saved(val revision: Long, val wordCount: Long) : SaveResult
    data class Conflict(val currentRevision: Long) : SaveResult
    data object NotFound : SaveResult
}

class ChapterRepository(
    private val db: WorkbenchDatabase,
    private val files: ChapterFileStore
) {
    private val writeMutex = Mutex()
    private val journalSeq = AtomicLong(System.currentTimeMillis())

    fun observeWorks(): Flow<List<WorkEntity>> = db.workDao().observeAll()

    fun observeChapters(workId: String): Flow<List<ChapterEntity>> = db.chapterDao().observeByWork(workId)

    suspend fun chapters(workId: String): List<ChapterEntity> = db.chapterDao().listByWork(workId)

    suspend fun work(workId: String): WorkEntity? = db.workDao().byId(workId)

    suspend fun createWork(title: String, chapterTitle: String = "第一章"): WorkEntity =
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                db.withTransaction {
                    val now = System.currentTimeMillis()
                    val work = WorkEntity(
                        id = UUID.randomUUID().toString(),
                        title = title.trim().ifEmpty { "未命名作品" },
                        wordCount = 0L,
                        chapterCount = 1,
                        createdAt = now,
                        updatedAt = now,
                        lastOpenedAt = now
                    )
                    val chapter = ChapterEntity(
                        id = UUID.randomUUID().toString(),
                        workId = work.id,
                        title = chapterTitle,
                        orderIndex = 0,
                        revision = 0L,
                        updatedAt = now
                    )
                    db.workDao().insert(work)
                    db.chapterDao().insert(chapter)
                    files.write(
                        ChapterContent(
                            chapterId = chapter.id,
                            revision = 0L,
                            blocks = listOf(Block(UUID.randomUUID().toString(), "")),
                            updatedAt = now
                        )
                    )
                    work
                }
            }
        }

    suspend fun ensureFirstChapter(workId: String): ChapterEntity? = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val work = db.workDao().byId(workId) ?: return@withLock null
            if (db.chapterDao().countByWork(workId) > 0) {
                return@withLock db.chapterDao().listByWork(workId).firstOrNull()
            }
            val now = System.currentTimeMillis()
            val chapter = ChapterEntity(
                id = UUID.randomUUID().toString(),
                workId = workId,
                title = "第一章",
                orderIndex = 0,
                revision = 0L,
                updatedAt = now
            )
            db.chapterDao().insert(chapter)
            db.workDao().update(work.copy(chapterCount = 1, updatedAt = now))
            files.write(
                ChapterContent(
                    chapterId = chapter.id,
                    revision = 0L,
                    blocks = listOf(Block(UUID.randomUUID().toString(), "")),
                    updatedAt = now
                )
            )
            chapter
        }
    }

    suspend fun openChapter(chapterId: String): OpenedChapter? = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            var chapter = db.chapterDao().byId(chapterId) ?: return@withLock null
            var content = try {
                files.read(chapterId)
            } catch (error: Exception) {
                // 文件损坏时不要用空章节覆盖；交给上层提示/导出备份。
                return@withLock null
            }
            if (content == null) {
                content = ChapterContent(
                    chapterId = chapterId,
                    revision = chapter.revision,
                    blocks = listOf(Block(UUID.randomUUID().toString(), "")),
                    updatedAt = System.currentTimeMillis()
                )
                files.write(content)
            }
            val records = files.readJournal(chapterId)
            if (records.isNotEmpty()) {
                val blocks = applyJournal(content.blocks, records)
                val now = System.currentTimeMillis()
                val revision = chapter.revision + 1
                content = ChapterContent(chapterId, revision, blocks, now)
                files.write(content)
                chapter = chapter.copy(
                    revision = revision,
                    wordCount = countWords(blocks),
                    updatedAt = now
                )
                db.chapterDao().update(chapter)
                files.clearJournal(chapterId)
            }
            OpenedChapter(chapter, content)
        }
    }

    suspend fun saveChapter(
        chapterId: String,
        blocks: List<Block>,
        expectedRevision: Long,
        source: UpdateSource
    ): SaveResult = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val chapter = db.chapterDao().byId(chapterId) ?: return@withLock SaveResult.NotFound
            if (chapter.revision != expectedRevision) {
                return@withLock SaveResult.Conflict(chapter.revision)
            }
            val now = System.currentTimeMillis()
            val newRevision = chapter.revision + 1
            val wordCount = countWords(blocks)
            files.write(ChapterContent(chapterId, newRevision, blocks, now))
            db.chapterDao().update(
                chapter.copy(
                    revision = newRevision,
                    wordCount = wordCount,
                    updatedAt = now
                )
            )
            val work = db.workDao().byId(chapter.workId)
            if (work != null) {
                db.workDao().update(
                    work.copy(
                        wordCount = (work.wordCount + wordCount - chapter.wordCount).coerceAtLeast(0L),
                        updatedAt = now,
                        lastOpenedAt = now
                    )
                )
            }
            files.clearJournal(chapterId)
            SaveResult.Saved(newRevision, wordCount)
        }
    }

    suspend fun appendJournal(chapterId: String, blockId: String, text: String) {
        val record = JournalRecord(
            seq = journalSeq.incrementAndGet(),
            blockId = blockId,
            text = text,
            crc32 = files.crc32(text),
            ts = System.currentTimeMillis()
        )
        files.appendJournal(chapterId, record)
    }

    suspend fun renameWork(workId: String, title: String) = withContext(Dispatchers.IO) {
        val work = db.workDao().byId(workId) ?: return@withContext
        db.workDao().update(work.copy(title = title.trim(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun createChapter(workId: String, title: String): ChapterEntity? = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val work = db.workDao().byId(workId) ?: return@withLock null
            val now = System.currentTimeMillis()
            val chapter = ChapterEntity(
                id = UUID.randomUUID().toString(),
                workId = workId,
                title = title.trim().ifEmpty { "新章" },
                orderIndex = db.chapterDao().maxOrder(workId) + 1,
                revision = 0L,
                updatedAt = now
            )
            db.chapterDao().insert(chapter)
            db.workDao().update(work.copy(chapterCount = work.chapterCount + 1, updatedAt = now))
            files.write(
                ChapterContent(
                    chapterId = chapter.id,
                    revision = 0L,
                    blocks = listOf(Block(UUID.randomUUID().toString(), "")),
                    updatedAt = now
                )
            )
            chapter
        }
    }

    suspend fun deleteChapter(chapterId: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val chapter = db.chapterDao().byId(chapterId) ?: return@withLock
            val work = db.workDao().byId(chapter.workId)
            files.delete(chapterId)
            db.chapterDao().delete(chapterId)
            if (work != null) {
                db.workDao().update(
                    work.copy(
                        chapterCount = (work.chapterCount - 1).coerceAtLeast(0),
                        wordCount = (work.wordCount - chapter.wordCount).coerceAtLeast(0L),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun renameChapter(chapterId: String, title: String) = withContext(Dispatchers.IO) {
        val chapter = db.chapterDao().byId(chapterId) ?: return@withContext
        db.chapterDao().update(chapter.copy(title = title.trim(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteWork(workId: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val chapters = db.chapterDao().listByWork(workId)
            chapters.forEach { files.delete(it.id) }
            db.workDao().delete(workId)
        }
    }

    suspend fun touchWork(workId: String) = withContext(Dispatchers.IO) {
        val work = db.workDao().byId(workId) ?: return@withContext
        db.workDao().update(work.copy(lastOpenedAt = System.currentTimeMillis()))
    }

    fun countWords(blocks: List<Block>): Long {
        var total = 0L
        blocks.forEach { block ->
            var index = 0
            while (index < block.text.length) {
                val codePoint = block.text.codePointAt(index)
                if (!Character.isWhitespace(codePoint)) total++
                index += Character.charCount(codePoint)
            }
        }
        return total
    }

    private fun applyJournal(base: List<Block>, records: List<JournalRecord>): List<Block> {
        val ordered = ArrayList(base)
        val index = ordered.associateBy { it.id }.toMutableMap()
        records.sortedBy { it.seq }.forEach { record ->
            val existing = index[record.blockId]
            if (existing == null) {
                val block = Block(record.blockId, record.text)
                ordered.add(block)
                index[record.blockId] = block
            } else {
                val position = ordered.indexOfFirst { it.id == record.blockId }
                val updated = existing.copy(text = record.text)
                ordered[position] = updated
                index[record.blockId] = updated
            }
        }
        if (ordered.isEmpty()) ordered.add(Block(UUID.randomUUID().toString(), ""))
        return ordered
    }
}
