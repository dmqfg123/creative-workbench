package com.creativeworkbench.writer.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 动作包安全用例：逐一造恶意包，验证路径穿越/绝对路径/反斜杠/超大文件/超条目/坏 manifest
 * 都被拒，且被拒时不往目标目录外写任何文件。
 */
class PackArchiveTest {

    // 本机 \$TEMP 在中文用户名下不可写，测试目录固定落到模块 build/ 里。
    private fun tempDir(): File {
        val dir = File("build/tmp/pack-test-" + java.lang.Long.toHexString(System.nanoTime()))
        dir.mkdirs()
        return dir
    }

    private fun zipStream(entries: List<Pair<String, ByteArray>>): ZipInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return ZipInputStream(ByteArrayInputStream(bytes.toByteArray()))
    }

    private fun unpack(vararg entries: Pair<String, ByteArray>): Pair<PackArchive.Unpack, File> {
        val dest = File(tempDir(), "unpacked")
        dest.mkdirs()
        val result = PackArchive.unpack(zipStream(entries.toList()), dest)
        return result to dest
    }

    private fun goodPack(): List<Pair<String, ByteArray>> = listOf(
        "manifest.json" to """{"id":"demo.pack","name":"示例","version":"1.0.0","actions":["actions/a.json"]}""".toByteArray(),
        "actions/a.json" to """{"id":"demo.pack.a","name":"示例动作","promptFile":"prompts/a.md","result":"Text","write":"None"}""".toByteArray(),
        "prompts/a.md" to "把这段写得更细。".toByteArray()
    )

    @Test
    fun rejectReasonFlagsDangerousPaths() {
        assertNotNull(PackArchive.rejectReason("../evil.json"))
        assertNotNull(PackArchive.rejectReason("a/../../evil.json"))
        assertNotNull(PackArchive.rejectReason("/data/local/tmp/evil.json"))
        assertNotNull(PackArchive.rejectReason("prompts\\a.md"))
        assertNotNull(PackArchive.rejectReason("C:/Windows/evil.json"))
        assertNull(PackArchive.rejectReason("prompts/a.md"))
        assertNull(PackArchive.rejectReason("actions/a.json"))
    }

    @Test
    fun traversalEntryIsRejectedAndNotWritten() {
        val (result, dest) = unpack(
            "manifest.json" to goodPack()[0].second,
            "../evil.json" to "x".toByteArray()
        )
        assertTrue(result.errors.any { it.contains("非法路径") })
        assertFalse(File(dest.parentFile, "evil.json").exists())
        // 只有 manifest.json 落盘，穿越条目一个字节都没写
        assertEquals(1, result.files)
        assertFalse(File(dest, "evil.json").exists())
    }

    @Test
    fun absolutePathEntryIsRejected() {
        val (result, _) = unpack("/sdcard/evil.json" to "x".toByteArray())
        assertTrue(result.errors.isNotEmpty())
        assertEquals(0, result.files)
    }

    @Test
    fun backslashEntryIsRejected() {
        val (result, _) = unpack("prompts\\a.md" to "x".toByteArray())
        assertTrue(result.errors.any { it.contains("非法路径") })
    }

    @Test
    fun unsupportedFileTypeIsRejected() {
        val (result, dest) = unpack("readme.txt" to "x".toByteArray())
        assertTrue(result.errors.any { it.contains("不支持的文件类型") })
        assertFalse(File(dest, "readme.txt").exists())
    }

    @Test
    fun oversizedFileIsRejected() {
        val big = ByteArray((1024 * 1024) + 1) { 'a'.code.toByte() }
        val (result, dest) = unpack("prompts/big.md" to big)
        assertTrue(result.errors.any { it.contains("单文件超过 1 MB") })
        assertFalse(File(dest, "prompts/big.md").exists())
    }

    @Test
    fun tooManyEntriesIsRejected() {
        val entries = (1..201).map { index -> "prompts/p" + index + ".md" to "x".toByteArray() }
        val (result, _) = unpack(*entries.toTypedArray())
        assertTrue(result.errors.any { it.contains("条目超过") })
    }

    @Test
    fun totalSizeOverFiveMbIsRejected() {
        val chunk = ByteArray(1024 * 1024) { 'a'.code.toByte() }
        val entries = (1..6).map { index -> "prompts/p" + index + ".md" to chunk }
        val (result, _) = unpack(*entries.toTypedArray())
        assertTrue(result.errors.any { it.contains("总量超过 5 MB") })
    }

    @Test
    fun wellFormedPackUnpacksAndValidates() {
        val (result, dest) = unpack(*goodPack().toTypedArray())
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(3, result.files)
        val check = PackArchive.validate(dest, emptySet())
        assertTrue(CheckMessage, check is PackArchive.Check.Ok)
        val pack = (check as PackArchive.Check.Ok).pack
        assertEquals("demo.pack", pack.id)
        assertEquals(1, pack.actions.size)
        assertEquals("prompts/a.md", pack.actions[0].promptFile)
    }

    @Test
    fun missingManifestFails() {
        val dir = tempDir()
        val check = PackArchive.validate(dir, emptySet())
        assertTrue(check is PackArchive.Check.Fail)
        assertTrue((check as PackArchive.Check.Fail).errors.any { it.contains("manifest.json") })
    }

    @Test
    fun badVersionFails() {
        val dir = tempDir()
        File(dir, "manifest.json").writeText(
            """{"id":"demo.pack","name":"示例","version":"1.0","actions":["a.json"]}"""
        )
        File(dir, "a.json").writeText("""{"id":"demo.a","promptFile":"a.md"}""")
        File(dir, "a.md").writeText("提示词")
        val check = PackArchive.validate(dir, emptySet())
        assertTrue(check is PackArchive.Check.Fail)
        assertTrue((check as PackArchive.Check.Fail).errors.any { it.contains("x.y.z") })
    }

    @Test
    fun builtinIdConflictFails() {
        val dir = tempDir()
        File(dir, "manifest.json").writeText(
            """{"id":"demo.pack","name":"示例","version":"1.0.0","actions":["a.json"]}"""
        )
        File(dir, "a.json").writeText("""{"id":"continue","promptFile":"a.md"}""")
        File(dir, "a.md").writeText("提示词")
        val check = PackArchive.validate(dir, setOf("continue"))
        assertTrue(check is PackArchive.Check.Fail)
        assertTrue((check as PackArchive.Check.Fail).errors.any { it.contains("动作 id 冲突") })
    }

    @Test
    fun promptFileTraversalFails() {
        val dir = tempDir()
        File(dir, "manifest.json").writeText(
            """{"id":"demo.pack","name":"示例","version":"1.0.0","actions":["a.json"]}"""
        )
        File(dir, "a.json").writeText("""{"id":"demo.a","promptFile":"../../secret.md"}""")
        val check = PackArchive.validate(dir, emptySet())
        assertTrue(check is PackArchive.Check.Fail)
        assertTrue((check as PackArchive.Check.Fail).errors.any { it.contains("promptFile 路径不合法") })
    }

    @Test
    fun actionFileTraversalFails() {
        val dir = tempDir()
        File(dir, "manifest.json").writeText(
            """{"id":"demo.pack","name":"示例","version":"1.0.0","actions":["../a.json"]}"""
        )
        val check = PackArchive.validate(dir, emptySet())
        assertTrue(check is PackArchive.Check.Fail)
        assertTrue((check as PackArchive.Check.Fail).errors.any { it.contains("动作文件路径不合法") })
    }

    @Test
    fun emptyPromptFileFails() {
        val dir = tempDir()
        File(dir, "manifest.json").writeText(
            """{"id":"demo.pack","name":"示例","version":"1.0.0","actions":["a.json"]}"""
        )
        File(dir, "a.json").writeText("""{"id":"demo.a","promptFile":"a.md"}""")
        File(dir, "a.md").writeText("")
        val check = PackArchive.validate(dir, emptySet())
        assertTrue(check is PackArchive.Check.Fail)
        assertTrue((check as PackArchive.Check.Fail).errors.any { it.contains("promptFile 不存在或为空") })
    }

    private companion object {
        const val CheckMessage = "合法包应当校验通过"
    }
}
