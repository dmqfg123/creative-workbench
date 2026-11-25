package com.creativeworkbench.writer.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 伏笔账本。抄 long-novel-writer 的 foreshadowing-ledger：
 * 伏笔是有状态的（planted / reinforced / paid / abandoned），不是一行标签；
 * 写正文时把「未回收」的那些强制带上，防止埋了忘。
 */
class ForeshadowingRepository(private val db: WorkbenchDatabase) {

    fun observeByWork(workId: String): Flow<List<ForeshadowingEntity>> =
        db.foreshadowingDao().observeByWork(workId)

    suspend fun listByWork(workId: String): List<ForeshadowingEntity> = withContext(Dispatchers.IO) {
        db.foreshadowingDao().listByWork(workId)
    }

    suspend fun upsert(item: ForeshadowingEntity) = withContext(Dispatchers.IO) {
        db.foreshadowingDao().insert(item)
    }

    suspend fun create(workId: String, title: String, detail: String, plantedChapterId: String?, dueChapterId: String?) =
        withContext(Dispatchers.IO) {
            val item = ForeshadowingEntity(
                id = UUID.randomUUID().toString(),
                workId = workId,
                title = title.trim().ifEmpty { "未命名伏笔" },
                detail = detail.trim(),
                plantedChapterId = plantedChapterId,
                dueChapterId = dueChapterId,
                status = "planted"
            )
            db.foreshadowingDao().insert(item)
            item
        }

    suspend fun update(item: ForeshadowingEntity) = withContext(Dispatchers.IO) {
        db.foreshadowingDao().insert(item)
    }

    suspend fun markPaid(id: String, paidChapterId: String?) = withContext(Dispatchers.IO) {
        val item = db.foreshadowingDao().byId(id) ?: return@withContext
        db.foreshadowingDao().insert(item.copy(status = "paid", paidChapterId = paidChapterId))
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { db.foreshadowingDao().delete(id) }

    /** 未回收（含强化中）：写正文时要强制带上。 */
    suspend fun unsettled(workId: String): List<ForeshadowingEntity> = withContext(Dispatchers.IO) {
        db.foreshadowingDao().listByWork(workId).filter { it.status != "paid" && it.status != "abandoned" }
    }
}
