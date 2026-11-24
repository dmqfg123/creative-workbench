package com.creativeworkbench.writer.core.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.creativeworkbench.writer.core.ai.AiHistoryStore
import com.creativeworkbench.writer.core.data.Block
import com.creativeworkbench.writer.core.data.ChapterContent
import com.creativeworkbench.writer.core.data.ChapterEntity
import com.creativeworkbench.writer.core.data.ChapterFileStore
import com.creativeworkbench.writer.core.data.NoteEntity
import com.creativeworkbench.writer.core.data.WorkEntity
import com.creativeworkbench.writer.core.data.WorkbenchDatabase
import com.creativeworkbench.writer.core.settings.AppSettings
import androidx.room.withTransaction
import com.creativeworkbench.writer.core.ai.AiConfigStore
import com.creativeworkbench.writer.core.settings.FontSizePreset
import com.creativeworkbench.writer.core.settings.SettingsRepository
import com.creativeworkbench.writer.core.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class BackupPreview(val works: Int, val chapters: Int, val notes: Int, val exportedAt: Long)

sealed interface ImportResult {
    data class Ok(val preview: BackupPreview) : ImportResult
    data class Error(val message: String) : ImportResult
}

/**
 * 备份与恢复：BackupBundle 是唯一格式。
 * 导入是整包替换，失败不动原数据；导入前先写一份本地备份。
 */
class BackupService(
    private val context: Context,
    private val db: WorkbenchDatabase,
    private val chapterFiles: ChapterFileStore,
    private val historyStore: AiHistoryStore,
    private val settings: SettingsRepository,
    private val aiConfig: AiConfigStore
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        // schemaVersion / apiKey 这类默认值也要显式写出来，方便以后按版本迁移
        encodeDefaults = true
    }

    suspend fun buildBundle(includeApiKey: Boolean = false): BackupBundle = withContext(Dispatchers.IO) {
        val works = db.workDao().observeAll().first()
        val chapters = mutableListOf<ChapterBackup>()
        works.forEach { work ->
            db.chapterDao().listByWork(work.id).forEach { chapter ->
                val blocks = runCatching { chapterFiles.read(chapter.id)?.blocks ?: emptyList() }.getOrDefault(emptyList())
                chapters.add(chapter.toBackup(blocks))
            }
        }
        val notes = db.noteDao().observeAll().first()
        val history = historyStore.list()
        BackupBundle(
            schemaVersion = SCHEMA_VERSION,
            appVersion = com.creativeworkbench.writer.BuildConfig.VERSION_NAME,
            exportedAt = System.currentTimeMillis(),
            works = works.map { it.toBackup() },
            chapters = chapters,
            notes = notes.map { it.toBackup() },
            aiHistory = history.map { json.encodeToString(AiHistoryBackup.serializer(), it.toBackup()) },
            settings = settings.settings.value.toMap(),
            modules = mapOf("notes" to settings.settings.value.notesEnabled),
            apiKey = if (includeApiKey) aiConfig.apiKey().takeIf { it.isNotBlank() } else null
        )
    }

    suspend fun export(uri: Uri, gzip: Boolean = true, includeApiKey: Boolean = false): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val bundle = buildBundle(includeApiKey)
            val text = json.encodeToString(BackupBundle.serializer(), bundle)
            val stream = context.contentResolver.openOutputStream(uri)
                ?: error("无法写入文件")
            stream.use { out ->
                if (gzip) {
                    GZIPOutputStream(out).use { gz -> gz.write(text.toByteArray(Charsets.UTF_8)) }
                } else {
                    out.write(text.toByteArray(Charsets.UTF_8))
                }
            }
            text.length
        }
    }

    /** 整机迁移包：manifest + backup.json + plugins/。 */
    suspend fun exportBundleZip(uri: Uri, includeApiKey: Boolean = false): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val bundle = buildBundle(includeApiKey = includeApiKey)
            val backupJson = json.encodeToString(BackupBundle.serializer(), bundle)
            val plugins = readPluginFiles()
            val manifest = mapOf(
                "format" to "workbench-migration",
                "schemaVersion" to SCHEMA_VERSION.toString(),
                "appVersion" to com.creativeworkbench.writer.BuildConfig.VERSION_NAME,
                "exportedAt" to System.currentTimeMillis().toString(),
                "works" to bundle.works.size.toString(),
                "chapters" to bundle.chapters.size.toString(),
                "notes" to bundle.notes.size.toString(),
                "plugins" to plugins.size.toString()
            )
            val out = context.contentResolver.openOutputStream(uri) ?: error("无法写入文件")
            out.use { BackupZip.write(it, backupJson, plugins, manifest) }
            backupJson.length + plugins.values.sumOf { it.size }
        }
    }

    private fun readPluginFiles(): Map<String, ByteArray> {
        val rootDir = File(context.filesDir, "workbench/plugins")
        if (!rootDir.exists()) return emptyMap()
        val result = mutableMapOf<String, ByteArray>()
        var total = 0L
        rootDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.relativeTo(rootDir).path.replace('\\', '/')
            if (result.size >= 500) return@forEach
            val bytes = runCatching { file.readBytes() }.getOrNull() ?: return@forEach
            if (bytes.size > 512 * 1024) return@forEach
            total += bytes.size
            if (total > 16L * 1024 * 1024) return@forEach
            result[relative] = bytes
        }
        return result
    }

    /** 恢复迁移包里的动作包文件；返回写入的文件数。 */
    suspend fun applyImportedPlugins(plugins: Map<String, ByteArray>): Int = withContext(Dispatchers.IO) {
        if (plugins.isEmpty()) return@withContext 0
        val rootDir = File(context.filesDir, "workbench/plugins")
        var written = 0
        plugins.forEach { (name, bytes) ->
            if (name.isBlank() || name.contains("..") || name.startsWith("/")) return@forEach
            val target = File(rootDir, name)
            target.parentFile?.mkdirs()
            runCatching { target.writeBytes(bytes) }.onSuccess { written++ }
        }
        written
    }

    data class ImportedBackup(
        val bundle: BackupBundle?,
        val error: String?,
        val plugins: Map<String, ByteArray> = emptyMap()
    )

    /** 兼容三种文件：整机迁移包 .zip、gzip 备份、纯 JSON。 */
    suspend fun readBackupFile(uri: Uri): ImportedBackup = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@runCatching ImportedBackup(null, "无法读取文件")
            if (BackupZip.isZip(bytes)) {
                val content = BackupZip.read(bytes.inputStream())
                if (content.error != null) {
                    ImportedBackup(null, content.error, content.plugins)
                } else {
                    val parsed = runCatching {
                        json.decodeFromString(BackupBundle.serializer(), content.backupJson!!)
                    }.getOrElse { error ->
                        return@runCatching ImportedBackup(null, "备份解析失败：" + (error.message ?: ""))
                    }
                    if (parsed.schemaVersion > SCHEMA_VERSION) {
                        ImportedBackup(null, "备份版本比当前 App 新（" + parsed.schemaVersion + "），请先升级 App")
                    } else {
                        ImportedBackup(parsed, null, content.plugins)
                    }
                }
            } else {
                val text = if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
                    GZIPInputStream(bytes.inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8)
                } else {
                    bytes.toString(Charsets.UTF_8)
                }
                val bundle = json.decodeFromString(BackupBundle.serializer(), text)
                if (bundle.schemaVersion > SCHEMA_VERSION) {
                    ImportedBackup(null, "备份版本比当前 App 新（" + bundle.schemaVersion + "），请先升级 App")
                } else {
                    ImportedBackup(bundle, null)
                }
            }
        }.getOrElse { error ->
            ImportedBackup(null, "文件解析失败：" + (error.message ?: "格式不对"))
        }
    }

    suspend fun readBundle(uri: Uri): Pair<BackupBundle?, String?> = withContext(Dispatchers.IO) {
        runCatching {
            val input: InputStream = context.contentResolver.openInputStream(uri)
                ?: return@runCatching null to "无法读取文件"
            val bytes = input.use { it.readBytes() }
            val text = if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
                GZIPInputStream(bytes.inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8)
            } else {
                bytes.toString(Charsets.UTF_8)
            }
            val bundle = json.decodeFromString(BackupBundle.serializer(), text)
            if (bundle.schemaVersion > SCHEMA_VERSION) {
                null to "备份版本比当前 App 新（" + bundle.schemaVersion + "），请先升级 App"
            } else {
                bundle to null
            }
        }.getOrElse { error -> null to ("文件解析失败：" + (error.message ?: "格式不对")) }
    }

    suspend fun readLocalBackup(file: File): Pair<BackupBundle?, String?> = withContext(Dispatchers.IO) {
        runCatching {
            val text = GZIPInputStream(file.inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8)
            val bundle = json.decodeFromString(BackupBundle.serializer(), text)
            bundle to null
        }.getOrElse { error -> null to ("本地备份损坏：" + (error.message ?: "")) }
    }

    suspend fun importReplace(bundle: BackupBundle): ImportResult = withContext(Dispatchers.IO) {
        runCatching {
            // 先备份当前数据，失败就中止
            writeLocalBackup()

            // 1) 章节正文先写 .import 暂存：这一步失败，数据库还没动，原数据完整
            bundle.chapters.forEach { chapter ->
                chapterFiles.writeImport(
                    ChapterContent(
                        chapterId = chapter.id,
                        revision = chapter.revision,
                        blocks = chapter.blocks.ifEmpty { listOf(Block(UUID.randomUUID().toString(), "")) },
                        updatedAt = chapter.updatedAt
                    )
                )
            }

            // 2) 数据库整包替换在一个事务里：任一条失败自动回滚，原数据还在
            db.withTransaction {
                db.noteDao().deleteAll()
                db.chapterDao().deleteAll()
                db.workDao().deleteAll()
                bundle.works.forEach { work ->
                    db.workDao().insert(
                        WorkEntity(
                            id = work.id,
                            title = work.title,
                            wordCount = work.wordCount,
                            chapterCount = work.chapterCount,
                            createdAt = work.createdAt,
                            updatedAt = work.updatedAt,
                            lastOpenedAt = work.lastOpenedAt
                        )
                    )
                }
                bundle.chapters.forEach { chapter ->
                    db.chapterDao().insert(
                        ChapterEntity(
                            id = chapter.id,
                            workId = chapter.workId,
                            parentId = chapter.parentId,
                            title = chapter.title,
                            orderIndex = chapter.orderIndex,
                            wordCount = chapter.wordCount,
                            note = chapter.note,
                            summary = chapter.summary,
                            revision = chapter.revision,
                            updatedAt = chapter.updatedAt
                        )
                    )
                }
                bundle.notes.forEach { note ->
                    db.noteDao().insert(
                        NoteEntity(
                            id = note.id,
                            workId = note.workId,
                            kind = note.kind,
                            title = note.title,
                            aliases = note.aliases,
                            content = note.content,
                            tags = note.tags,
                            aiReference = note.aiReference,
                            pinned = note.pinned,
                            createdAtMs = note.createdAtMs,
                            updatedAtMs = note.updatedAtMs,
                            sourceNoteId = note.sourceNoteId
                        )
                    )
                }
            }

            // 3) 事务成功后才把暂存文件换名成正式章节文件，再清掉旧章节
            bundle.chapters.forEach { chapterFiles.commitImport(it.id) }
            chapterFiles.deleteOtherThan(bundle.chapters.map { it.id }.toSet())

            // 4) 设置与密钥（密钥只有导出时勾选过才在包里）
            bundle.settings["theme"]?.let { name ->
                runCatching { settings.setTheme(ThemeMode.valueOf(name)) }
            }
            bundle.settings["font_size"]?.let { name ->
                runCatching { settings.setFontSize(FontSizePreset.valueOf(name)) }
            }
            bundle.settings["notes_enabled"]?.let { settings.setNotesEnabled(it.toBoolean()) }
            bundle.apiKey?.takeIf { it.isNotBlank() }?.let { aiConfig.saveApiKey(it) }

            ImportResult.Ok(
                BackupPreview(
                    works = bundle.works.size,
                    chapters = bundle.chapters.size,
                    notes = bundle.notes.size,
                    exportedAt = bundle.exportedAt
                )
            )
        }.getOrElse { error -> ImportResult.Error("导入失败：" + (error.message ?: "未知错误")) }
    }

    /** 每天一份到 Download/雨刮模拟器备份/，不需要用户选目录。 */
    suspend fun writeDailyPublicBackup(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            writeDailyPublicBackupText(json.encodeToString(BackupBundle.serializer(), buildBundle()))
        }
    }

    private fun writeDailyPublicBackupText(text: String): String {
        val bytes = java.io.ByteArrayOutputStream().use { out ->
            GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
            out.toByteArray()
        }
        val name = "雨刮模拟器备份-" +
            SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date()) + ".json.gz"
        PublicBackupStore.write(context, name, bytes).getOrThrow()
        settings.markSafBackup(System.currentTimeMillis())
        return name
    }

    suspend fun autoDailyPublicBackupIfNeeded() = withContext(Dispatchers.IO) {
        val last = settings.settings.value.lastSafBackupAt ?: 0L
        if (last > 0L && isSameDay(last, System.currentTimeMillis())) return@withContext
        runCatching { writeDailyPublicBackup() }
    }

    /**
     * 启动时的自动备份：**整个备份只构建一次**，本机与 Download 两处复用同一份文本。
     * 之前两个 ifNeeded 各构建一次（各读一遍全部章节文件 + 各压一次 gzip），
     * 30 章 29 万字的库上等于白干一倍活。
     */
    suspend fun autoBackupOnStart() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val needLocal = (settings.settings.value.lastBackupAt ?: 0L).let { it <= 0L || !isSameDay(it, now) }
        val needPublic = (settings.settings.value.lastSafBackupAt ?: 0L).let { it <= 0L || !isSameDay(it, now) }
        if (!needLocal && !needPublic) return@withContext
        runCatching {
            val text = json.encodeToString(BackupBundle.serializer(), buildBundle())
            if (needLocal) {
                val hash = sha256(text)
                val stampFile = File(context.filesDir, "workbench/backups/.last-hash")
                val previous = runCatching { stampFile.readText().trim() }.getOrNull()
                if (previous == hash) {
                    settings.markBackup(now)
                } else {
                    writeLocalBackupText(text)
                    stampFile.parentFile?.mkdirs()
                    runCatching { stampFile.writeText(hash) }
                }
            }
            if (needPublic) {
                runCatching { writeDailyPublicBackupText(text) }
            }
        }
    }

    /**
     * 每次启动检查：同一天没备份就写一份；内容哈希和上次一样就跳过，不白写盘。
     * 失败不打扰用户。
     */
    suspend fun autoBackupIfNeeded() = withContext(Dispatchers.IO) {
        val last = settings.settings.value.lastBackupAt ?: 0L
        if (last > 0L && isSameDay(last, System.currentTimeMillis())) return@withContext
        runCatching {
            val text = json.encodeToString(BackupBundle.serializer(), buildBundle())
            val hash = sha256(text)
            val stampFile = File(context.filesDir, "workbench/backups/.last-hash")
            val previous = runCatching { stampFile.readText().trim() }.getOrNull()
            if (previous == hash) {
                settings.markBackup(System.currentTimeMillis())
                return@runCatching
            }
            val file = writeLocalBackupText(text)
            stampFile.parentFile?.mkdirs()
            runCatching { stampFile.writeText(hash) }
            file
        }
    }

    private fun sha256(text: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun isSameDay(first: Long, second: Long): Boolean {
        val a = java.util.Calendar.getInstance()
        val b = java.util.Calendar.getInstance()
        a.timeInMillis = first
        b.timeInMillis = second
        return a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
            a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
    }

    suspend fun writeLocalBackup(): File = withContext(Dispatchers.IO) {
        writeLocalBackupText(json.encodeToString(BackupBundle.serializer(), buildBundle()))
    }

    private fun writeLocalBackupText(text: String): File {
        val dir = File(context.filesDir, "workbench/backups").apply { mkdirs() }
        val name = "auto-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(Date()) + ".json.gz"
        val file = File(dir, name)
        GZIPOutputStream(file.outputStream()).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        // 只留最近 3 份
        dir.listFiles()?.filter { it.name.endsWith(".json.gz") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(3)
            ?.forEach { runCatching { it.delete() } }
        settings.markBackup(System.currentTimeMillis())
        return file
    }

    fun localBackups(): List<File> {
        val dir = File(context.filesDir, "workbench/backups")
        return dir.listFiles()?.filter { it.name.endsWith(".json.gz") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    suspend fun exportWork(workId: String, markdown: Boolean, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val work = db.workDao().byId(workId) ?: error("作品不存在")
            val chapters = db.chapterDao().listByWork(workId)
            val builder = StringBuilder()
            if (markdown) {
                builder.append("# ").append(work.title).append("\n\n")
                chapters.forEach { chapter ->
                    builder.append("## ").append(chapter.title).append("\n\n")
                    runCatching { chapterFiles.read(chapter.id) }
                        .getOrNull()
                        ?.blocks
                        ?.forEach { block ->
                            if (block.text.isNotBlank()) builder.append(block.text).append("\n\n")
                        }
                }
            } else {
                builder.append(work.title).append("\n\n")
                chapters.forEach { chapter ->
                    builder.append(chapter.title).append("\n\n")
                    runCatching { chapterFiles.read(chapter.id) }
                        .getOrNull()
                        ?.blocks
                        ?.forEach { block ->
                            if (block.text.isNotBlank()) builder.append(block.text).append("\n\n")
                        }
                }
            }
            context.contentResolver.openOutputStream(uri)?.use { out: OutputStream ->
                out.write(builder.toString().toByteArray(Charsets.UTF_8))
            } ?: error("无法写入文件")
            builder.length
        }
    }

    suspend fun clearAll(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            writeLocalBackup()
            db.workDao().observeAll().first().forEach { work -> db.workDao().delete(work.id) }
            db.noteDao().deleteAll()
            historyStore.clear()
        }
    }

    private fun WorkEntity.toBackup() = WorkBackup(
        id, title, wordCount, chapterCount, createdAt, updatedAt, lastOpenedAt
    )

    private fun ChapterEntity.toBackup(blocks: List<Block>) = ChapterBackup(
        id = id,
        workId = workId,
        parentId = parentId,
        title = title,
        orderIndex = orderIndex,
        wordCount = wordCount,
        note = note,
        summary = summary,
        revision = revision,
        updatedAt = updatedAt,
        blocks = blocks
    )

    private fun NoteEntity.toBackup() = NoteBackup(
        id = id,
        workId = workId,
        kind = kind,
        title = title,
        aliases = aliases,
        content = content,
        tags = tags,
        aiReference = aiReference,
        pinned = pinned,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
        sourceNoteId = sourceNoteId
    )

    private fun AppSettings.toMap(): Map<String, String> = mapOf(
        "theme" to theme.name,
        "font_size" to fontSize.name,
        "notes_enabled" to notesEnabled.toString(),
        "allow_cleartext_http" to allowCleartextHttp.toString(),
        "fps_overlay" to fpsOverlay.toString()
    )

    private companion object {
        const val SCHEMA_VERSION = 1
    }
}

@kotlinx.serialization.Serializable
data class AiHistoryBackup(
    val id: String,
    val actionId: String,
    val actionName: String,
    val contextSummary: String,
    val resultPreview: String,
    val applied: Boolean,
    val createdAtMs: Long
)

private fun com.creativeworkbench.writer.core.ai.AiHistoryRecord.toBackup() = AiHistoryBackup(
    id = id,
    actionId = actionId,
    actionName = actionName,
    contextSummary = contextSummary,
    resultPreview = resultPreview,
    applied = applied,
    createdAtMs = createdAtMs
)