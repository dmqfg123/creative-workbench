package com.creativeworkbench.writer.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WorkEntity::class,
        ChapterEntity::class,
        NoteEntity::class,
        SettingEntity::class,
        ModuleEntity::class,
        SnapshotEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WorkbenchDatabase : RoomDatabase() {
    abstract fun workDao(): WorkDao
    abstract fun chapterDao(): ChapterDao
    abstract fun noteDao(): NoteDao
    abstract fun settingDao(): SettingDao
    abstract fun moduleDao(): ModuleDao
    abstract fun snapshotDao(): SnapshotDao

    companion object {
        fun create(context: Context): WorkbenchDatabase =
            Room.databaseBuilder(context.applicationContext, WorkbenchDatabase::class.java, "workbench.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
