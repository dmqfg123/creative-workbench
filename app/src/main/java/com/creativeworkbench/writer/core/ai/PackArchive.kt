package com.creativeworkbench.writer.core.ai

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 动作包解压 + 校验。故意不依赖 Android：只吃 java.io / java.util.zip / org.json，
 * 所以能在 JVM 单测里直接造恶意包（../、绝对路径、反斜杠、超 1 MB、超条目）逐条验。
 *
 * 规则（与 STATE/交接说明一致）：条目 ≤200、单文件 ≤1 MB、解压总量 ≤5 MB、
 * 只收 .json/.md、路径不许绝对/穿越/反斜杠；manifest 与动作定义逐字段校验。
 */
object PackArchive {
    const val MAX_ENTRIES = 200
    const val MAX_FILE_BYTES = 1024L * 1024L
    const val MAX_TOTAL_BYTES = 5L * 1024L * 1024L

    data class Unpack(val errors: List<String> = emptyList(), val files: Int = 0)

    sealed interface Check {
        data class Ok(val pack: ActionPack) : Check
        data class Fail(val errors: List<String>) : Check
    }

    /** 相对路径安全闸：绝对路径、.. 穿越、反斜杠一律拒。返回 null = 安全。 */
    fun rejectReason(name: String): String? = when {
        name.isBlank() -> "非法路径：" + name
        name.startsWith("/") || name.startsWith("\\") -> "非法路径：" + name
        name.contains("..") -> "非法路径：" + name
        name.contains('\\') -> "非法路径：" + name
        name.contains(':') -> "非法路径：" + name
        else -> null
    }

    fun isSupportedFile(name: String): Boolean = name.endsWith(".json") || name.endsWith(".md")

    /** 解压到 dest。任一错误即整体失败（调用方不落盘）。 */
    fun unpack(zip: ZipInputStream, dest: File): Unpack {
        val errors = mutableListOf<String>()
        var total = 0L
        var files = 0
        var count = 0
        while (true) {
            val entry = zip.nextEntry ?: break
            count++
            if (count > MAX_ENTRIES) {
                errors.add("条目超过 " + MAX_ENTRIES + " 个")
                break
            }
            val name = entry.name
            val unsafe = rejectReason(name)
            if (unsafe != null) {
                errors.add(unsafe)
                continue
            }
            if (!isSupportedFile(name)) {
                if (!entry.isDirectory) errors.add("不支持的文件类型：" + name)
                continue
            }
            if (entry.isDirectory) continue
            val bytes = readCapped(zip)
            if (bytes == null) {
                errors.add("单文件超过 1 MB：" + name)
                continue
            }
            total += bytes.size
            if (total > MAX_TOTAL_BYTES) {
                errors.add("解压后总量超过 5 MB")
                break
            }
            val target = File(dest, name)
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            files++
        }
        return Unpack(errors, files)
    }

    /** 分块读，超过 1 MB 立即放弃，避免声明尺寸造假/压缩炸弹把内存打爆。 */
    private fun readCapped(zip: ZipInputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = zip.read(buffer)
            if (read <= 0) break
            out.write(buffer, 0, read)
            if (out.size() > MAX_FILE_BYTES) return null
        }
        return out.toByteArray()
    }

    fun readManifestId(dir: File): String? = runCatching {
        JSONObject(File(dir, "manifest.json").readText()).optString("id").takeIf { it.isNotBlank() }
    }.getOrNull()

    /** 校验解压结果能否安装；existingActionIds 里不能包含本包自己的动作。 */
    fun validate(dir: File, reservedActionIds: Set<String>): Check {
        val manifestFile = File(dir, "manifest.json")
        if (!manifestFile.exists()) return Check.Fail(listOf("缺少 manifest.json"))
        val errors = mutableListOf<String>()
        var pack: ActionPack? = null
        runCatching {
            val json = JSONObject(manifestFile.readText())
            val id = json.optString("id")
            val name = json.optString("name")
            val version = json.optString("version")
            if (!id.matches(Regex("^[a-zA-Z0-9._-]{3,64}$"))) errors.add("manifest.id 不合法")
            if (name.isBlank()) errors.add("manifest.name 必填")
            if (!version.matches(Regex("^\\d+\\.\\d+\\.\\d+$"))) errors.add("manifest.version 必须是 x.y.z")
            val actionFiles = json.optJSONArray("actions") ?: org.json.JSONArray()
            if (actionFiles.length() == 0) errors.add("manifest.actions 为空")
            val actions = mutableListOf<AiActionSpec>()
            for (index in 0 until actionFiles.length()) {
                val relative = actionFiles.optString(index)
                val unsafe = rejectReason(relative)
                if (unsafe != null) {
                    errors.add("动作文件路径不合法：" + relative)
                    continue
                }
                val actionFile = File(dir, relative)
                if (!actionFile.exists()) {
                    errors.add("动作文件不存在：" + relative)
                    continue
                }
                val actionJson = JSONObject(actionFile.readText())
                val actionId = actionJson.optString("id")
                if (actionId.isBlank()) errors.add(relative + "：id 缺失")
                if (reservedActionIds.contains(actionId)) {
                    errors.add(relative + "：动作 id 冲突（" + actionId + "）")
                }
                val promptFile = actionJson.optString("promptFile")
                val promptUnsafe = rejectReason(promptFile)
                if (promptUnsafe != null) {
                    errors.add(relative + "：promptFile 路径不合法")
                }
                val prompt = if (promptUnsafe == null) File(dir, promptFile) else null
                if (promptFile.isBlank() || prompt == null || !prompt.exists() || prompt.length() == 0L) {
                    errors.add(relative + "：promptFile 不存在或为空")
                }
                val result = runCatching { ResultKind.valueOf(actionJson.optString("result")) }
                    .getOrElse { ResultKind.Text }
                val write = runCatching { WriteTarget.valueOf(actionJson.optString("write")) }
                    .getOrElse { WriteTarget.None }
                actions.add(
                    AiActionSpec(
                        id = actionId,
                        name = actionJson.optString("name", actionId),
                        context = listOf(ContextSlot.Chapter, ContextSlot.SettingNotes),
                        promptFile = promptFile,
                        result = result,
                        write = write,
                        whenCondition = ActionWhen.Always,
                        priority = actionJson.optInt("priority", 0),
                        source = ActionSource.Imported,
                        packId = id
                    )
                )
            }
            if (errors.isEmpty()) pack = ActionPack(id, name, version, actions)
        }.onFailure { errors.add("manifest 解析失败：" + (it.message ?: "")) }
        val installed = pack
        return if (errors.isEmpty() && installed != null) Check.Ok(installed) else Check.Fail(errors)
    }
}
