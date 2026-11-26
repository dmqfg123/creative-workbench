package com.creativeworkbench.writer.core.data

import androidx.room.withTransaction
import com.creativeworkbench.writer.core.common.Breadcrumbs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

sealed interface OpenChapterResult {
    data class Ok(val opened: OpenedChapter) : OpenChapterResult
    data object Missing : OpenChapterResult
    data class Corrupt(val detail: String) : OpenChapterResult
}

class ChapterRepository(
    private val db: WorkbenchDatabase,
    private val files: ChapterFileStore
) {
    private val writeMutex = Mutex()
    private val journalSeq = AtomicLong(System.currentTimeMillis())
    /** 每个章节上一次写进 journal 的段落顺序，用来决定要不要补一条 layout 记录。 */
    private val lastWrittenOrder = HashMap<String, List<String>>()

    fun observeWorks(): Flow<List<WorkEntity>> = db.workDao().observeAll()

    fun observeChapters(workId: String): Flow<List<ChapterEntity>> = db.chapterDao().observeByWork(workId)

    suspend fun chapters(workId: String): List<ChapterEntity> = db.chapterDao().listByWork(workId)

    suspend fun work(workId: String): WorkEntity? = db.workDao().byId(workId)

    /**
     * 最近动过的作品：冷启动时 currentWorkId 是空的（只在内存里），
     * 这时新建的人物/设定会挂不上 workId，永远进不了 AI 上下文。
     * 用一个「最近打开」兜底，比持久化一个可能已删的 id 安全。
     */
    suspend fun mostRecentWork(): WorkEntity? =
        db.workDao().observeAll().first().maxByOrNull { maxOf(it.lastOpenedAt, it.updatedAt) }

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

    suspend fun openChapter(chapterId: String): OpenedChapter? =
        (openChapterResult(chapterId) as? OpenChapterResult.Ok)?.opened

    suspend fun openChapterResult(chapterId: String): OpenChapterResult = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            var chapter = db.chapterDao().byId(chapterId)
                ?: return@withLock OpenChapterResult.Missing
            var content = try {
                files.read(chapterId)
            } catch (error: Exception) {
                // 文件损坏时不要用空章节覆盖；交给上层提示/导出备份。
                return@withLock OpenChapterResult.Corrupt(error.message ?: "章节文件无法解析")
            }
            if (content == null) {
                // 正式文件不见了：先看 .import 暂存与 .bak 上一版。
                // 都对不上就报 Corrupt，绝不在这里写一个空章——那会把「丢文件」固化成「这章就是空的」。
                when (val salvage = files.salvage(chapterId)) {
                    is ChapterFileStore.Salvage.Found -> {
                        content = salvage.content
                        files.write(content)
                        Breadcrumbs.record("章节文件缺失，已从" + salvage.source + "恢复")
                    }
                    is ChapterFileStore.Salvage.Unreadable -> {
                        return@withLock OpenChapterResult.Corrupt(
                            "章节文件不见了，" + salvage.source + "也读不出来（" + salvage.detail + "）；请从备份恢复"
                        )
                    }
                    null -> return@withLock OpenChapterResult.Corrupt(
                        "章节文件不见了（可能是上次导入/清空没写完），请从备份恢复这一章"
                    )
                }
            }
            // 文件先写、DB 后写；如果中间崩溃，文件 revision 会领先。
            // 以文件为准修复 DB 与作品总字数，避免覆盖已落盘的正文。
            if (content.revision > chapter.revision) {
                val oldWords = chapter.wordCount
                val newWords = countWords(content.blocks)
                chapter = chapter.copy(
                    revision = content.revision,
                    wordCount = newWords,
                    updatedAt = content.updatedAt
                )
                db.chapterDao().update(chapter)
                val work = db.workDao().byId(chapter.workId)
                if (work != null) {
                    db.workDao().update(
                        work.copy(
                            wordCount = (work.wordCount - oldWords + newWords).coerceAtLeast(0L),
                            updatedAt = content.updatedAt
                        )
                    )
                }
            } else if (content.revision < chapter.revision) {
                return@withLock OpenChapterResult.Corrupt("章节文件版本落后于数据库，已停止编辑")
            }
            val records = files.readJournal(chapterId)
            if (records.isNotEmpty()) {
                val blocks = applyJournal(content.blocks, records)
                val now = System.currentTimeMillis()
                val revision = chapter.revision + 1
                val oldWords = chapter.wordCount
                val newWords = countWords(blocks)
                content = ChapterContent(chapterId, revision, blocks, now)
                files.write(content)
                chapter = chapter.copy(
                    revision = revision,
                    wordCount = newWords,
                    updatedAt = now
                )
                db.chapterDao().update(chapter)
                val work = db.workDao().byId(chapter.workId)
                if (work != null) {
                    db.workDao().update(
                        work.copy(
                            wordCount = (work.wordCount - oldWords + newWords).coerceAtLeast(0L),
                            updatedAt = now
                        )
                    )
                }
                files.clearJournal(chapterId)
                lastWrittenOrder.remove(chapterId)
            }
            OpenChapterResult.Ok(OpenedChapter(chapter, content))
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
            lastWrittenOrder.remove(chapterId)
            SaveResult.Saved(newRevision, wordCount)
        }
    }

    /** 冲突自愈用：磁盘上这一章现在的 revision。 */
    suspend fun currentRevision(chapterId: String): Long? =
        withContext(Dispatchers.IO) { db.chapterDao().byId(chapterId)?.revision }

    suspend fun appendJournal(chapterId: String, blockId: String, text: String) {
        appendJournalBlocks(chapterId, listOf(Block(blockId, text)))
    }

    suspend fun appendJournalBlocks(chapterId: String, blocks: List<Block>) = withContext(Dispatchers.IO) {
        if (blocks.isEmpty()) return@withContext
        // 和 saveChapter/openChapter 共用一把写锁：否则刚写进 journal 的最后一段可能被 clearJournal 删掉
        writeMutex.withLock {
            appendJournalLocked(chapterId, blocks)
        }
    }

    private fun appendJournalLocked(chapterId: String, blocks: List<Block>) {
        val now = System.currentTimeMillis()
        val order = blocks.map { it.id }
        val records = ArrayList<JournalRecord>(blocks.size + 1)
        // 顺序/删除只能靠 layout 行表达：只写每段文本，回放时把删掉的段复活、新段甩到章末。
        if (lastWrittenOrder[chapterId] != order) {
            records.add(
                JournalRecord(
                    seq = journalSeq.incrementAndGet(),
                    kind = JournalRecord.KIND_LAYOUT,
                    order = order,
                    ts = now
                )
            )
            lastWrittenOrder[chapterId] = order
        }
        blocks.forEach { block ->
            records.add(
                JournalRecord(
                    seq = journalSeq.incrementAndGet(),
                    kind = JournalRecord.KIND_BLOCK,
                    blockId = block.id,
                    text = block.text,
                    crc32 = files.crc32(block.text),
                    ts = now
                )
            )
        }
        // 磁盘满/文件被删时不要崩进程：journal 只是兜底，失败记一条面包屑
        runCatching { files.appendJournalBatch(chapterId, records) }
            .onFailure { Breadcrumbs.record("journal 写入失败：" + (it.message ?: "IO")) }
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
            files.delete(chapterId)
            files.deleteSnapshots(chapterId)
            // 快照行也要删：只删文件会留下孤儿行，count() 虚高会让 200 上限提前淘汰有效快照
            db.snapshotDao().deleteByChapter(chapterId)
            lastWrittenOrder.remove(chapterId)
        }
    }

    suspend fun renameChapter(chapterId: String, title: String) = withContext(Dispatchers.IO) {
        // 持锁读改写：不持锁的话可能把另一路刚刚保存的 revision / wordCount 写回旧值
        writeMutex.withLock {
            val chapter = db.chapterDao().byId(chapterId) ?: return@withLock
            db.chapterDao().update(chapter.copy(title = title.trim(), updatedAt = System.currentTimeMillis()))
        }
    }

    /** 写入前存快照：全局最多 200 个，按时间淘汰最早的。 */
    suspend fun saveSnapshot(chapterId: String, blocks: List<Block>) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val now = System.currentTimeMillis()
            val chapter = db.chapterDao().byId(chapterId) ?: return@withLock
            val count = db.snapshotDao().count()
            val file = files.writeSnapshot(
                chapterId,
                now,
                count,
                ChapterContent(chapterId, chapter.revision, blocks, now)
            )
            db.snapshotDao().insert(
                SnapshotEntity(
                    id = UUID.randomUUID().toString(),
                    chapterId = chapterId,
                    createdAtMs = now,
                    seq = count,
                    path = file.absolutePath
                )
            )
            val all = db.snapshotDao().all()
            if (all.size > 200) {
                all.take(all.size - 200).forEach { old ->
                    files.deleteSnapshot(old.path)
                    db.snapshotDao().delete(old.id)
                }
            }
        }
    }

    suspend fun updateChapterNote(chapterId: String, note: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val chapter = db.chapterDao().byId(chapterId) ?: return@withLock
            db.chapterDao().update(chapter.copy(note = note, updatedAt = System.currentTimeMillis()))
        }
    }

    /** 上移 / 下移：只交换 orderIndex，v1 不做拖拽。 */
    suspend fun moveChapter(chapterId: String, delta: Int) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val chapter = db.chapterDao().byId(chapterId) ?: return@withLock
            val siblings = db.chapterDao().listByWork(chapter.workId)
            val index = siblings.indexOfFirst { it.id == chapterId }
            val target = index + delta
            if (index < 0 || target < 0 || target >= siblings.size) return@withLock
            val other = siblings[target]
            val now = System.currentTimeMillis()
            db.withTransaction {
                db.chapterDao().update(chapter.copy(orderIndex = other.orderIndex, updatedAt = now))
                db.chapterDao().update(other.copy(orderIndex = chapter.orderIndex, updatedAt = now))
            }
        }
    }

    suspend fun updateSummary(chapterId: String, summary: String?) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val chapter = db.chapterDao().byId(chapterId) ?: return@withLock
            db.chapterDao().update(
                chapter.copy(
                    summary = summary?.trim()?.takeIf { it.isNotEmpty() },
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteWork(workId: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val chapters = db.chapterDao().listByWork(workId)
            // 标题要在删之前读：删完 byId 就是 null 了（笔记的来源标签要用它）
            val workTitle = db.workDao().byId(workId)?.title
            db.workDao().delete(workId)
            chapters.forEach { chapter ->
                files.delete(chapter.id)
                files.deleteSnapshots(chapter.id)
                db.snapshotDao().deleteByChapter(chapter.id)
            }
            // 笔记是独立板块：删作品只把笔记摘下来（workId 置空），不连带删除。
            // 人物/设定是花时间写的资产，误删作品连笔记一起没了是不可接受的。
            // 摘下来时把来源作品名写成标签，否则一堆孤儿笔记分不清是谁的设定。
            val now = System.currentTimeMillis()
            if (!workTitle.isNullOrBlank()) {
                db.noteDao().listByWork(workId).forEach { note ->
                    val tag = "原·" + workTitle
                    val tags = note.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                    if (tags.none { it == tag }) tags.add(tag)
                    db.noteDao().insert(note.copy(tags = tags.joinToString(","), updatedAtMs = now))
                }
            }
            db.noteDao().detachFromWork(workId, now)
            db.outlineDao().deleteByWork(workId)
        }
    }

    /**
     * 字数抽检：随机抽 5 章，用章节文件重算字数，和 Room 索引比；
     * 偏差 >1% 判定索引漂移，全量重算并写回（PRD §5.3）。
     * 返回修正的章节数，正常运行几乎总是 0。
     */
    suspend fun auditWordCounts(sample: Int = 5): Int = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val works = db.workDao().observeAll().first()
            val allChapters = works.flatMap { db.chapterDao().listByWork(it.id) }
            if (allChapters.isEmpty()) return@withLock 0
            val drifted = allChapters.shuffled().take(sample).any { chapter ->
                val actual = runCatching { files.read(chapter.id)?.blocks?.let { countWords(it) } }.getOrNull()
                if (actual == null) {
                    false
                } else {
                    kotlin.math.abs(actual - chapter.wordCount) * 100 >
                        chapter.wordCount.coerceAtLeast(1L)
                }
            }
            if (!drifted) return@withLock 0
            var fixed = 0
            works.forEach { work ->
                var total = 0L
                db.chapterDao().listByWork(work.id).forEach { chapter ->
                    val actual = runCatching { files.read(chapter.id)?.blocks?.let { countWords(it) } }
                        .getOrNull() ?: chapter.wordCount
                    if (actual != chapter.wordCount) {
                        db.chapterDao().update(chapter.copy(wordCount = actual))
                        fixed++
                    }
                    total += actual
                }
                if (total != work.wordCount) db.workDao().update(work.copy(wordCount = total))
            }
            fixed
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

    private fun applyJournal(base: List<Block>, records: List<JournalRecord>): List<Block> =
        JournalReplay.apply(base, records)
}
