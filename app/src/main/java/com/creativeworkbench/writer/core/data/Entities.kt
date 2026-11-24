package com.creativeworkbench.writer.core.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "works")
data class WorkEntity(
    @PrimaryKey val id: String,
    val title: String,
    val wordCount: Long = 0L,
    val chapterCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long
)

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = WorkEntity::class,
            parentColumns = ["id"],
            childColumns = ["workId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workId"), Index("workId", "orderIndex")]
)
data class ChapterEntity(
    @PrimaryKey val id: String,
    val workId: String,
    val parentId: String? = null,
    val title: String,
    val orderIndex: Int,
    val wordCount: Long = 0L,
    val note: String = "",
    val summary: String? = null,
    val revision: Long = 0L,
    val updatedAt: Long
)

@Entity(tableName = "notes", indices = [Index("workId"), Index("kind")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val workId: String?,
    val kind: String,
    val title: String,
    val aliases: String = "",
    val content: String = "",
    val tags: String = "",
    val aiReference: Boolean = false,
    val pinned: Boolean = false,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val sourceNoteId: String? = null
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "modules")
data class ModuleEntity(
    @PrimaryKey val id: String,
    val enabled: Boolean
)

@Entity(tableName = "snapshots", indices = [Index("chapterId")])
data class SnapshotEntity(
    @PrimaryKey val id: String,
    val chapterId: String,
    val createdAtMs: Long,
    val seq: Int,
    val path: String
)
