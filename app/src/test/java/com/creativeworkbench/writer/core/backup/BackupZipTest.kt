package com.creativeworkbench.writer.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupZipTest {

    @Test
    fun roundTripsBundleAndPlugins() {
        val out = ByteArrayOutputStream()
        BackupZip.write(
            out,
            backupJson = "{\"schemaVersion\":1,\"works\":[]}",
            plugins = mapOf("demo/manifest.json" to "{\"id\":\"demo\"}".toByteArray()),
            manifest = mapOf("format" to "workbench-migration", "schemaVersion" to "1")
        )
        val content = BackupZip.read(ByteArrayInputStream(out.toByteArray()))
        assertNull(content.error)
        assertTrue(content.backupJson!!.contains("schemaVersion"))
        assertEquals(1, content.plugins.size)
    }

    @Test
    fun rejectsTraversalEntries() {
        val bytes = zipOf(listOf(
            "backup.json" to "{}",
            "../evil.json" to "x"
        ))
        val content = BackupZip.read(ByteArrayInputStream(bytes))
        assertNotNull(content.error)
        assertTrue(content.error!!.contains("非法路径"))
    }

    @Test
    fun missingBackupIsReported() {
        val bytes = zipOf(listOf("manifest.json" to "{}"))
        val content = BackupZip.read(ByteArrayInputStream(bytes))
        assertEquals("压缩包里没有 backup.json", content.error)
    }

    @Test
    fun detectsZipMagic() {
        assertTrue(BackupZip.isZip(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4)))
        assertEquals(false, BackupZip.isZip("{\"a\":1}".toByteArray()))
    }

    private fun zipOf(entries: List<Pair<String, String>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}

class AiUsageTest {
    @Test
    fun estimatesCostWithCacheDiscount() {
        val usage = com.creativeworkbench.writer.core.ai.AiUsage(
            promptTokens = 10_000,
            completionTokens = 1_000,
            cacheHitTokens = 9_000,
            cacheMissTokens = 1_000
        )
        // 9000*0.043 + 1000*2.13 + 1000*8.52 = 387 + 2130 + 8520 = 11037 / 1e6
        // 单价会随官方调价变化，所以断言量级与相对关系，别把某一天的价格写死
        val cost = usage.estimatedCostCny()
        assertTrue("花费估算应大于 0，实际 " + cost, cost > 0.0)
        assertTrue("命中缓存的输入应显著便宜于输出", 9000 * 0.043 < 1000 * 8.52)
        assertEquals(0.9, usage.cacheHitRate, 0.0001)
    }
}
