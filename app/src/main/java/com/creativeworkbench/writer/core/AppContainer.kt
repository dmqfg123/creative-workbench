package com.creativeworkbench.writer.core

import android.content.Context
import com.creativeworkbench.writer.core.ai.AiConfigStore
import com.creativeworkbench.writer.core.ai.AiHistoryStore
import com.creativeworkbench.writer.core.ai.PluginStore
import com.creativeworkbench.writer.core.backup.BackupService
import com.creativeworkbench.writer.core.data.ChapterFileStore
import com.creativeworkbench.writer.core.data.ChapterRepository
import com.creativeworkbench.writer.core.data.NoteRepository
import com.creativeworkbench.writer.core.data.WorkbenchDatabase
import com.creativeworkbench.writer.core.nav.ModuleHost
import com.creativeworkbench.writer.core.nav.ModuleRuntime
import com.creativeworkbench.writer.core.settings.SettingsRepository

class AppContainer(context: Context) {
    val database: WorkbenchDatabase = WorkbenchDatabase.create(context)
    val chapterFiles: ChapterFileStore = ChapterFileStore(context)
    val chapters: ChapterRepository = ChapterRepository(database, chapterFiles)
    val notes: NoteRepository = NoteRepository(database)
    val aiConfig: AiConfigStore = AiConfigStore(context)
    val aiHistory: AiHistoryStore = AiHistoryStore(context)
    val plugins: PluginStore = PluginStore(context)
    val settings: SettingsRepository = SettingsRepository(context)
    val backup: BackupService = BackupService(context, database, chapterFiles, aiHistory, settings)
    val moduleHost: ModuleHost = ModuleHost()
    val moduleRuntime: ModuleRuntime = ModuleRuntime(moduleHost)
}
