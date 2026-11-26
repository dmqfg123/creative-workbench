package com.creativeworkbench.writer.core.backup

import com.creativeworkbench.writer.core.data.Block
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupBundleTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun apiKeyIsAbsentByDefaultAndRoundTripsWhenExplicit() {
        val plain = BackupBundle(appVersion = "0.5.0", exportedAt = 1L)
        assertTrue(json.decodeFromString<BackupBundle>(json.encodeToString(plain)).apiKey == null)

        val withKey = plain.copy(apiKey = "sk-test-123")
        val decoded = json.decodeFromString<BackupBundle>(json.encodeToString(withKey))
        assertEquals("sk-test-123", decoded.apiKey)
    }

    @Test
    fun roundTripsV1Bundle() {
        val bundle = BackupBundle(
            appVersion = "0.1.0",
            exportedAt = 1763900000000L,
            works = listOf(
                WorkBackup(
                    id = "w1",
                    title = "雨夜长歌",
                    wordCount = 1240,
                    chapterCount = 1,
                    createdAt = 1L,
                    updatedAt = 2L,
                    lastOpenedAt = 3L
                )
            ),
            chapters = listOf(
                ChapterBackup(
                    id = "c1",
                    workId = "w1",
                    title = "第一章",
                    orderIndex = 0,
                    wordCount = 1240,
                    note = "",
                    summary = "开篇",
                    revision = 3,
                    updatedAt = 2L,
                    blocks = listOf(Block("b1", "雨落在旧钟楼上。"))
                )
            ),
            notes = emptyList(),
            settings = mapOf("notes_enabled" to "true"),
            modules = mapOf("notes" to true),
            plugins = emptyList()
        )
        val text = json.encodeToString(bundle)
        val decoded = json.decodeFromString<BackupBundle>(text)
        assertEquals(1, decoded.schemaVersion)
        assertEquals("雨夜长歌", decoded.works.first().title)
        assertEquals("雨落在旧钟楼上。", decoded.chapters.first().blocks.first().text)
        assertTrue(decoded.modules.getValue("notes"))
    }
}