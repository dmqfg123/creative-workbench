package com.creativeworkbench.writer.core.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { System, Light, Dark }
enum class FontSizePreset { Small, Standard, Large }
enum class LibrarySort { Recent, Created, Title, Words }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.System,
    val fontSize: FontSizePreset = FontSizePreset.Standard,
    val notesEnabled: Boolean = true,
    val autoSaveEnabled: Boolean = true,
    val allowCleartextHttp: Boolean = false,
    val fpsOverlay: Boolean = false,
    val lastBackupAt: Long? = null,
    val lastExportAt: Long? = null,
    val librarySort: LibrarySort = LibrarySort.Recent,
    val lastSafBackupAt: Long? = null
)

class SettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("workbench_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings = AppSettings(
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", ThemeMode.System.name)!!) }
            .getOrDefault(ThemeMode.System),
        fontSize = runCatching { FontSizePreset.valueOf(prefs.getString("font_size", FontSizePreset.Standard.name)!!) }
            .getOrDefault(FontSizePreset.Standard),
        notesEnabled = prefs.getBoolean("notes_enabled", true),
        autoSaveEnabled = prefs.getBoolean("auto_save", true),
        allowCleartextHttp = prefs.getBoolean("allow_cleartext_http", false),
        fpsOverlay = prefs.getBoolean("fps_overlay", false),
        lastBackupAt = prefs.getLong("last_backup_at", 0L).takeIf { it > 0L },
        lastExportAt = prefs.getLong("last_export_at", 0L).takeIf { it > 0L },
        librarySort = runCatching { LibrarySort.valueOf(prefs.getString("library_sort", LibrarySort.Recent.name)!!) }
            .getOrDefault(LibrarySort.Recent),
        lastSafBackupAt = prefs.getLong("last_saf_backup_at", 0L).takeIf { it > 0L }
    )

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString("theme", mode.name).apply()
        _settings.value = _settings.value.copy(theme = mode)
    }

    fun setFontSize(preset: FontSizePreset) {
        prefs.edit().putString("font_size", preset.name).apply()
        _settings.value = _settings.value.copy(fontSize = preset)
    }

    fun setLibrarySort(sort: LibrarySort) {
        prefs.edit().putString("library_sort", sort.name).apply()
        _settings.value = _settings.value.copy(librarySort = sort)
    }

    fun setNotesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("notes_enabled", enabled).apply()
        _settings.value = _settings.value.copy(notesEnabled = enabled)
    }

    fun setAutoSaveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_save", enabled).apply()
        _settings.value = _settings.value.copy(autoSaveEnabled = enabled)
    }

    fun setAllowCleartextHttp(enabled: Boolean) {
        prefs.edit().putBoolean("allow_cleartext_http", enabled).apply()
        _settings.value = _settings.value.copy(allowCleartextHttp = enabled)
    }

    fun setFpsOverlay(enabled: Boolean) {
        prefs.edit().putBoolean("fps_overlay", enabled).apply()
        _settings.value = _settings.value.copy(fpsOverlay = enabled)
    }

    fun markSafBackup(now: Long) {
        prefs.edit().putLong("last_saf_backup_at", now).apply()
        _settings.value = _settings.value.copy(lastSafBackupAt = now)
    }

    fun markBackup(now: Long) {
        prefs.edit().putLong("last_backup_at", now).apply()
        _settings.value = _settings.value.copy(lastBackupAt = now)
    }

    fun markExport(now: Long) {
        prefs.edit().putLong("last_export_at", now).apply()
        _settings.value = _settings.value.copy(lastExportAt = now)
    }
}
