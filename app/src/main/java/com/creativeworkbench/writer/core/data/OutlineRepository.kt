package com.creativeworkbench.writer.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 大纲：一部作品一条有序列表，节点可绑定到某一章。
 * 不做树形（卷-章-beat 在手机上点不动），靠 orderIndex 排序 + chapterId 绑定表达层级。
 */
class OutlineRepository(private val db: WorkbenchDatabase) {
    fun observeByWork(workId: String): Flow<List<OutlineNodeEntity>> =
        db.outlineDao().observeByWork(workId)

    suspend fun list(workId: String): List<OutlineNodeEntity> = withContext(Dispatchers.IO) {
        db.outlineDao().listByWork(workId)
    }

    suspend fun count(workId: String): Int = withContext(Dispatchers.IO) {
        db.outlineDao().countByWork(workId)
    }

    suspend fun upsert(node: OutlineNodeEntity) = withContext(Dispatchers.IO) {
        db.outlineDao().insert(node)
    }

    /** 新建：orderIndex 自动接在最后。 */
    suspend fun create(
        workId: String,
        chapterId: String?,
        title: String,
        content: String
    ): OutlineNodeEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val node = OutlineNodeEntity(
            id = UUID.randomUUID().toString(),
            workId = workId,
            chapterId = chapterId,
            title = title.trim().ifEmpty { "未命名节点" },
            content = content.trim(),
            orderIndex = db.outlineDao().maxOrder(workId) + 1,
            createdAtMs = now,
            updatedAtMs = now
        )
        db.outlineDao().insert(node)
        node
    }

    suspend fun update(id: String, title: String, content: String, chapterId: String?) =
        withContext(Dispatchers.IO) {
            val node = db.outlineDao().byId(id) ?: return@withContext
            db.outlineDao().insert(
                node.copy(
                    title = title.trim().ifEmpty { node.title },
                    content = content.trim(),
                    chapterId = chapterId,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
        }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { db.outlineDao().delete(id) }

    suspend fun deleteByChapter(chapterId: String) = withContext(Dispatchers.IO) {
        db.outlineDao().deleteByChapter(chapterId)
    }

    /** 上移/下移：整表重排 orderIndex，避免出现两个相同序号。 */
    suspend fun move(workId: String, id: String, delta: Int) = withContext(Dispatchers.IO) {
        val list = db.outlineDao().listByWork(workId).toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return@withContext
        val target = (index + delta).coerceIn(0, list.lastIndex)
        if (target == index) return@withContext
        val node = list.removeAt(index)
        list.add(target, node)
        val now = System.currentTimeMillis()
        list.forEachIndexed { position, item ->
            if (item.orderIndex != position) {
                db.outlineDao().insert(item.copy(orderIndex = position, updatedAtMs = now))
            }
        }
    }
}
