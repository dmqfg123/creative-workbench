package com.creativeworkbench.writer.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 笔记只有一张表，六种 kind 都是它的视图。
 * 设定类（character / world）必须带 workId；素材类可以只有当前作品。
 */
class NoteRepository(private val db: WorkbenchDatabase) {
    fun observeAll(): Flow<List<NoteEntity>> = db.noteDao().observeAll()

    suspend fun byId(id: String): NoteEntity? = withContext(Dispatchers.IO) { db.noteDao().byId(id) }

    suspend fun count(): Int = withContext(Dispatchers.IO) { db.noteDao().count() }

    suspend fun upsert(note: NoteEntity) = withContext(Dispatchers.IO) { db.noteDao().insert(note) }

    suspend fun setPinned(id: String, pinned: Boolean) = withContext(Dispatchers.IO) {
        db.noteDao().setPinned(id, pinned, System.currentTimeMillis())
    }

    suspend fun setAiReference(id: String, value: Boolean) = withContext(Dispatchers.IO) {
        db.noteDao().setAiReference(id, value, System.currentTimeMillis())
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { db.noteDao().delete(id) }

    suspend fun replaceAll(notes: List<NoteEntity>) = withContext(Dispatchers.IO) {
        db.noteDao().deleteAll()
        notes.forEach { db.noteDao().insert(it) }
    }
}

object NoteKind {
    const val CHARACTER = "character"
    const val WORLD = "world"
    const val IDEA = "idea"
    const val FRAGMENT = "fragment"
    const val DIALOGUE = "dialogue"
    const val MEMO = "memo"

    val all = listOf(CHARACTER, WORLD, IDEA, FRAGMENT, DIALOGUE, MEMO)
    val setting = listOf(CHARACTER, WORLD)
    val material = listOf(IDEA, FRAGMENT, DIALOGUE)
    val quick = listOf(MEMO, IDEA, FRAGMENT, DIALOGUE)

    fun label(kind: String): String = when (kind) {
        CHARACTER -> "人物"
        WORLD -> "设定"
        IDEA -> "灵感"
        FRAGMENT -> "片段"
        DIALOGUE -> "对白"
        MEMO -> "备忘"
        else -> kind
    }

    /** 设定类必须挂在作品上；其它类型可以为空（跨作品可见）。 */
    fun requiresWork(kind: String): Boolean = kind == CHARACTER || kind == WORLD
}
