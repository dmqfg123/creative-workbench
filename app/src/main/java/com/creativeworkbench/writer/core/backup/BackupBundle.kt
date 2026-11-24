package com.creativeworkbench.writer.core.backup

import com.creativeworkbench.writer.core.data.Block
import kotlinx.serialization.Serializable

@Serializable
data class BackupBundle(
    val schemaVersion: Int = 1,
    val appVersion: String,
    val exportedAt: Long,
    val works: List<WorkBackup> = emptyList(),
    val chapters: List<ChapterBackup> = emptyList(),
    val notes: List<NoteBackup> = emptyList(),
    val aiHistory: List<String> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
    val modules: Map<String, Boolean> = emptyMap(),
    val plugins: List<PluginBackup> = emptyList(),
    /** 只有用户在导出时显式勾选「包含 API 密钥」才有值；默认不许带。 */
    val apiKey: String? = null
)

@Serializable
data class WorkBackup(
    val id: String,
    val title: String,
    val wordCount: Long,
    val chapterCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long
)

@Serializable
data class ChapterBackup(
    val id: String,
    val workId: String,
    val parentId: String? = null,
    val title: String,
    val orderIndex: Int,
    val wordCount: Long,
    val note: String,
    val summary: String? = null,
    val revision: Long,
    val updatedAt: Long,
    val blocks: List<Block> = emptyList()
)

@Serializable
data class NoteBackup(
    val id: String,
    val workId: String?,
    val kind: String,
    val title: String,
    val aliases: String,
    val content: String,
    val tags: String,
    val aiReference: Boolean,
    val pinned: Boolean,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val sourceNoteId: String? = null
)

@Serializable
data class PluginBackup(
    val id: String,
    val name: String,
    val version: String,
    val files: Map<String, String> = emptyMap()
)
