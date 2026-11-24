package com.creativeworkbench.writer.core.ai

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

data class ActionPack(
    val id: String,
    val name: String,
    val version: String,
    val actions: List<AiActionSpec>
)

sealed interface PackImportResult {
    data class Ok(val pack: ActionPack) : PackImportResult
    data class Error(val messages: List<String>) : PackImportResult
}

/**
 * 动作包（.wbplugin，本质是 zip）：只含动作定义与提示词，不贡献 UI。
 * 校验失败逐条报错且不落盘；解压总量 ≤5 MB、单文件 ≤1 MB、条目 ≤200。
 */
class PluginStore(private val context: Context) {
    private val root = File(context.filesDir, "workbench/plugins")

    fun list(): List<ActionPack> {
        val dirs = root.listFiles()?.filter { it.isDirectory }.orEmpty()
        return dirs.mapNotNull { dir -> runCatching { readPack(dir) }.getOrNull() }
    }

    fun findAction(actionId: String): AiActionSpec? =
        list().flatMap { it.actions }.firstOrNull { it.id == actionId }

    /** 读动作自己的提示词（prompts 目录下的 md）。 */
    fun promptFor(action: AiActionSpec): String? {
        val packId = action.packId ?: return null
        val file = File(File(root, packId), action.promptFile)
        if (!file.exists()) return null
        return runCatching { file.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun delete(id: String) {
        root.listFiles()?.firstOrNull { it.name == id }?.deleteRecursively()
    }

    suspend fun import(uri: Uri): PackImportResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val temp = File(context.cacheDir, "pack-" + System.currentTimeMillis())
        temp.mkdirs()
        var total = 0L
        var count = 0
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        count++
                        if (count > 200) {
                            errors.add("条目超过 200 个")
                            break
                        }
                        val name = entry.name
                        if (name.startsWith("/") || name.contains("..") || name.contains('\\')) {
                            errors.add("非法路径：" + name)
                            continue
                        }
                        if (!(name.endsWith(".json") || name.endsWith(".md") || name.endsWith("manifest.json"))) {
                            if (!entry.isDirectory) errors.add("不支持的文件类型：" + name)
                            continue
                        }
                        val target = File(temp, name)
                        target.parentFile?.mkdirs()
                        val bytes = zip.readBytes()
                        if (bytes.size > 1024 * 1024) {
                            errors.add("单文件超过 1 MB：" + name)
                            continue
                        }
                        total += bytes.size
                        if (total > 5L * 1024 * 1024) {
                            errors.add("解压后总量超过 5 MB")
                            break
                        }
                        target.writeBytes(bytes)
                    }
                }
            } ?: errors.add("无法读取文件")
        }.onFailure { errors.add("解压失败：" + (it.message ?: "")) }

        if (errors.isEmpty()) {
            val manifest = File(temp, "manifest.json")
            if (!manifest.exists()) {
                errors.add("缺少 manifest.json")
            } else {
                runCatching {
                    val json = JSONObject(manifest.readText())
                    val id = json.optString("id")
                    val name = json.optString("name")
                    val version = json.optString("version")
                    if (!id.matches(Regex("^[a-zA-Z0-9._-]{3,64}$"))) errors.add("manifest.id 不合法")
                    if (name.isBlank()) errors.add("manifest.name 必填")
                    if (!version.matches(Regex("^\\d+\\.\\d+\\.\\d+$"))) errors.add("manifest.version 必须是 x.y.z")
                    val actionFiles = json.optJSONArray("actions") ?: org.json.JSONArray()
                    if (actionFiles.length() == 0) errors.add("manifest.actions 为空")
                    val builtinIds = AiActions.builtin.map { it.id }.toSet()
                    // 同名包重复导入视为覆盖，不算动作 id 冲突。
                    val existingIds = list()
                        .filter { it.id != id }
                        .flatMap { pack -> pack.actions.map { it.id } }
                        .toSet()
                    val actions = mutableListOf<AiActionSpec>()
                    for (index in 0 until actionFiles.length()) {
                        val relative = actionFiles.optString(index)
                        val actionFile = File(temp, relative)
                        if (!actionFile.exists()) {
                            errors.add("动作文件不存在：" + relative)
                            continue
                        }
                        val actionJson = JSONObject(actionFile.readText())
                        val actionId = actionJson.optString("id")
                        if (actionId.isBlank()) errors.add(relative + "：id 缺失")
                        if (builtinIds.contains(actionId) || existingIds.contains(actionId)) {
                            errors.add(relative + "：动作 id 冲突（" + actionId + "）")
                        }
                        val promptFile = actionJson.optString("promptFile")
                        val prompt = File(temp, promptFile)
                        if (promptFile.isBlank() || !prompt.exists() || prompt.length() == 0L) {
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
                    if (errors.isEmpty()) {
                        val target = File(root, id)
                        target.deleteRecursively()
                        temp.copyRecursively(target, overwrite = true)
                        return@withContext PackImportResult.Ok(
                            ActionPack(id, name, version, actions)
                        )
                    }
                }.onFailure { errors.add("manifest 解析失败：" + (it.message ?: "")) }
            }
        }
        temp.deleteRecursively()
        PackImportResult.Error(errors.ifEmpty { listOf("导入失败") })
    }

    private fun readPack(dir: File): ActionPack {
        val manifest = File(dir, "manifest.json")
        val json = JSONObject(manifest.readText())
        val actionFiles = json.optJSONArray("actions") ?: org.json.JSONArray()
        val actions = (0 until actionFiles.length()).mapNotNull { index ->
            runCatching {
                val actionJson = JSONObject(File(dir, actionFiles.optString(index)).readText())
                AiActionSpec(
                    id = actionJson.optString("id"),
                    name = actionJson.optString("name"),
                    context = listOf(ContextSlot.Chapter),
                    promptFile = actionJson.optString("promptFile"),
                    result = runCatching { ResultKind.valueOf(actionJson.optString("result")) }.getOrDefault(ResultKind.Text),
                    write = runCatching { WriteTarget.valueOf(actionJson.optString("write")) }.getOrDefault(WriteTarget.None),
                    whenCondition = ActionWhen.Always,
                    priority = actionJson.optInt("priority", 0),
                    source = ActionSource.Imported,
                    packId = dir.name
                )
            }.getOrNull()
        }
        return ActionPack(
            id = json.optString("id"),
            name = json.optString("name"),
            version = json.optString("version"),
            actions = actions
        )
    }
}
