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

/**
 * 大纲节点：一部作品一条有序列表，可选绑定到某一章。
 * 不用树形父子关系：长篇写作里「卷-章-beat」三层在手机上根本点不动，
 * 扁平有序 + 章节绑定就够 AI 知道「这章该写什么、下一章往哪走」（见 参考/外部调研_笔记与大纲）。
 */
@Entity(tableName = "outline_nodes", indices = [Index("workId"), Index("chapterId")])
data class OutlineNodeEntity(
    @PrimaryKey val id: String,
    val workId: String,
    /** 绑定的章节；空 = 全书级/卷级节点，不绑定具体章。 */
    val chapterId: String? = null,
    val title: String,
    /** 这一段要写什么（剧情要点、冲突、结尾钩子）。 */
    val content: String = "",
    val orderIndex: Int,
    val createdAtMs: Long,
    val updatedAtMs: Long
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
