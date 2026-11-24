package com.creativeworkbench.writer.core

import android.content.Context
import com.creativeworkbench.writer.core.data.ChapterFileStore
import com.creativeworkbench.writer.core.data.ChapterRepository
import com.creativeworkbench.writer.core.data.WorkbenchDatabase
import com.creativeworkbench.writer.core.nav.ModuleHost
import com.creativeworkbench.writer.core.nav.ModuleRuntime
import com.creativeworkbench.writer.core.settings.SettingsRepository

class AppContainer(context: Context) {
    val database: WorkbenchDatabase = WorkbenchDatabase.create(context)
    val chapterFiles: ChapterFileStore = ChapterFileStore(context)
    val chapters: ChapterRepository = ChapterRepository(database, chapterFiles)
    val settings: SettingsRepository = SettingsRepository(context)
    val moduleHost: ModuleHost = ModuleHost()
    val moduleRuntime: ModuleRuntime = ModuleRuntime(moduleHost)
}
