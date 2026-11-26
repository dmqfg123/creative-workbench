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
        ForeshadowingEntity::class,
        SettingEntity::class,
        ModuleEntity::class,
        SnapshotEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class WorkbenchDatabase : RoomDatabase() {
    abstract fun workDao(): WorkDao
    abstract fun chapterDao(): ChapterDao
    abstract fun noteDao(): NoteDao
    abstract fun outlineDao(): OutlineDao
    abstract fun foreshadowingDao(): ForeshadowingDao
    abstract fun settingDao(): SettingDao
    abstract fun moduleDao(): ModuleDao
    abstract fun snapshotDao(): SnapshotDao

    companion object {
        fun create(context: Context): WorkbenchDatabase =
            // 不再使用 fallbackToDestructiveMigration：schema 升级必须显式写 Migration，
            // 否则宁可启动失败，也不能静默清库。v1 的 schema 已导出到 app/schemas/。
            Room.databaseBuilder(context.applicationContext, WorkbenchDatabase::class.java, "workbench.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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

        /**
         * v3：大纲节点加粒度/目标字数/beat 角色（照 Manuskript 的多粒度字段），
         * 新增伏笔账本表（照 long-novel-writer 的 foreshadowing-ledger），
         * 笔记加「从哪一章开始生效」（抄 Novelcrafter 的 Codex Progressions）。
         * 新列全部可空：SQLite 的 ADD COLUMN 对 NOT NULL 要带默认值，
         * 与 Room 的 schema 校验容易打架，可空最省事。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `outline_nodes` ADD COLUMN `level` INTEGER")
                db.execSQL("ALTER TABLE `outline_nodes` ADD COLUMN `targetWords` INTEGER")
                db.execSQL("ALTER TABLE `outline_nodes` ADD COLUMN `beatRole` TEXT")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `fromChapterId` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `foreshadowing` (" +
                        "`id` TEXT NOT NULL, " +
                        "`workId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`detail` TEXT NOT NULL, " +
                        "`plantedChapterId` TEXT, " +
                        "`dueChapterId` TEXT, " +
                        "`paidChapterId` TEXT, " +
                        "`status` TEXT NOT NULL, " +
                        "`priority` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_foreshadowing_workId` ON `foreshadowing` (`workId`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_foreshadowing_plantedChapterId` " +
                        "ON `foreshadowing` (`plantedChapterId`)"
                )
            }
        }
    }
}
