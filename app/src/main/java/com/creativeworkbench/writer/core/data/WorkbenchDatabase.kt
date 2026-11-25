package com.creativeworkbench.writer.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WorkEntity::class,
        ChapterEntity::class,
        NoteEntity::class,
        OutlineNodeEntity::class,
        SettingEntity::class,
        ModuleEntity::class,
        SnapshotEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class WorkbenchDatabase : RoomDatabase() {
    abstract fun workDao(): WorkDao
    abstract fun chapterDao(): ChapterDao
    abstract fun noteDao(): NoteDao
    abstract fun outlineDao(): OutlineDao
    abstract fun settingDao(): SettingDao
    abstract fun moduleDao(): ModuleDao
    abstract fun snapshotDao(): SnapshotDao

    companion object {
        fun create(context: Context): WorkbenchDatabase =
            // 不再使用 fallbackToDestructiveMigration：schema 升级必须显式写 Migration，
            // 否则宁可启动失败，也不能静默清库。v1 的 schema 已导出到 app/schemas/。
            Room.databaseBuilder(context.applicationContext, WorkbenchDatabase::class.java, "workbench.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /**
         * v2：新增大纲表。纯新增，不动既有列，老库升级不丢数据。
         * 建表语句与 Room 为 OutlineNodeEntity 生成的 SQL 逐字一致（含列顺序），
         * 否则 Room 打开时会报 schema 校验失败。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `outline_nodes` (" +
                        "`id` TEXT NOT NULL, " +
                        "`workId` TEXT NOT NULL, " +
                        "`chapterId` TEXT, " +
                        "`title` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`orderIndex` INTEGER NOT NULL, " +
                        "`createdAtMs` INTEGER NOT NULL, " +
                        "`updatedAtMs` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_outline_nodes_workId` ON `outline_nodes` (`workId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_outline_nodes_chapterId` ON `outline_nodes` (`chapterId`)")
            }
        }
    }
}
