package com.creativeworkbench.writer.core.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class AiHistoryRecord(
    val id: String,
    val actionId: String,
    val actionName: String,
    val workId: String? = null,
    val chapterId: String? = null,
    val chapterRevision: Long = 0L,
    val contentHash: String = "",
    val model: String = "",
    val contextSummary: String = "",
    val resultKind: String = "text",
    val resultPreview: String = "",
    val resultText: String = "",
    val undoMode: String = "none",
    val undoBlockId: String? = null,
    val undoStart: Int = -1,
    val undoEnd: Int = -1,
    val undoBefore: String = "",
    val undoAfter: String = "",
    val undoIds: List<String> = emptyList(),
    val applied: Boolean = false,
    val createdAtMs: Long = 0L
)

/** AI 历史：本地 JSON，最多 200 条；不落盘完整 prompt。 */
class AiHistoryStore(context: Context) {
    private val file = File(context.filesDir, "workbench/ai-history.json")
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    suspend fun list(): List<AiHistoryRecord> = withContext(Dispatchers.IO) {
        mutex.withLock { read() }
    }

    suspend fun add(record: AiHistoryRecord) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = read().toMutableList()
            current.add(0, record)
            write(current.take(MAX_RECORDS))
        }
    }

    suspend fun replace(id: String, record: AiHistoryRecord) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = read().map { if (it.id == id) record else it }
            write(current)
        }
    }

    suspend fun markApplied(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = read().map { if (it.id == id) it.copy(applied = true) else it }
            write(current)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock { write(emptyList()) }
    }

    private fun read(): List<AiHistoryRecord> = runCatching {
        if (!file.exists()) return emptyList()
        json.decodeFromString<List<AiHistoryRecord>>(file.readText())
    }.getOrDefault(emptyList())

    private fun write(records: List<AiHistoryRecord>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(records))
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    private companion object {
        const val MAX_RECORDS = 200
    }
}
