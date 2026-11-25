package com.creativeworkbench.writer.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkDao {
    @Query("SELECT * FROM works ORDER BY lastOpenedAt DESC")
    fun observeAll(): Flow<List<WorkEntity>>

    @Query("SELECT * FROM works WHERE id = :id")
    suspend fun byId(id: String): WorkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(work: WorkEntity)

    @Update
    suspend fun update(work: WorkEntity)

    @Query("DELETE FROM works WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM works")
    suspend fun deleteAll()
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE workId = :workId ORDER BY orderIndex ASC")
    fun observeByWork(workId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE workId = :workId ORDER BY orderIndex ASC")
    suspend fun listByWork(workId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun byId(id: String): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chapter: ChapterEntity)

    @Update
    suspend fun update(chapter: ChapterEntity)

    @Query("DELETE FROM chapters WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM chapters")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM chapters WHERE workId = :workId")
    suspend fun countByWork(workId: String): Int

    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM chapters WHERE workId = :workId")
    suspend fun maxOrder(workId: String): Int
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAtMs DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE workId IS NULL OR workId = :workId ORDER BY pinned DESC, updatedAtMs DESC")
    fun observeForWork(workId: String?): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAtMs DESC")
    suspend fun listAll(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun byId(id: String): NoteEntity?

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Query("UPDATE notes SET pinned = :pinned, updatedAtMs = :now WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, now: Long)

    @Query("UPDATE notes SET aiReference = :value, updatedAtMs = :now WHERE id = :id")
    suspend fun setAiReference(id: String, value: Boolean, now: Long)

    @Query("SELECT * FROM notes WHERE workId = :workId")
    suspend fun listByWork(workId: String): List<NoteEntity>

    @Query("DELETE FROM notes WHERE workId = :workId")
    suspend fun deleteByWork(workId: String)

    /**
     * 删作品时把笔记摘下来，而不是删掉：笔记（尤其人物/设定）是跨作品复用的资产，
     * 用户明确要求「删书本不删笔记」。workId 置空后它们出现在「全部作品」里。
     */
    @Query("UPDATE notes SET workId = NULL, updatedAtMs = :now WHERE workId = :workId")
    suspend fun detachFromWork(workId: String, now: Long)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}

@Dao
interface OutlineDao {
    @Query("SELECT * FROM outline_nodes WHERE workId = :workId ORDER BY orderIndex ASC")
    fun observeByWork(workId: String): Flow<List<OutlineNodeEntity>>

    @Query("SELECT * FROM outline_nodes WHERE workId = :workId ORDER BY orderIndex ASC")
    suspend fun listByWork(workId: String): List<OutlineNodeEntity>

    @Query("SELECT * FROM outline_nodes WHERE id = :id")
    suspend fun byId(id: String): OutlineNodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: OutlineNodeEntity)

    @Query("DELETE FROM outline_nodes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM outline_nodes WHERE workId = :workId")
    suspend fun deleteByWork(workId: String)

    @Query("DELETE FROM outline_nodes WHERE chapterId = :chapterId")
    suspend fun deleteByChapter(chapterId: String)

    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM outline_nodes WHERE workId = :workId")
    suspend fun maxOrder(workId: String): Int

    @Query("SELECT COUNT(*) FROM outline_nodes WHERE workId = :workId")
    suspend fun countByWork(workId: String): Int
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings")
    suspend fun all(): List<SettingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)
}

@Dao
interface ModuleDao {
    @Query("SELECT * FROM modules")
    suspend fun all(): List<ModuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(module: ModuleEntity)
}

@Dao
interface SnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: SnapshotEntity)

    @Query("SELECT * FROM snapshots WHERE chapterId = :chapterId ORDER BY createdAtMs DESC")
    suspend fun listForChapter(chapterId: String): List<SnapshotEntity>

    @Query("SELECT * FROM snapshots ORDER BY createdAtMs ASC")
    suspend fun all(): List<SnapshotEntity>

    @Query("SELECT COUNT(*) FROM snapshots")
    suspend fun count(): Int

    @Query("DELETE FROM snapshots WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM snapshots WHERE chapterId = :chapterId")
    suspend fun deleteByChapter(chapterId: String)

    @Query("DELETE FROM snapshots")
    suspend fun deleteAll()
}
