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
        val temp = File(context.cacheDir, "pack-" + System.currentTimeMillis())
        temp.mkdirs()
        val unpack = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                PackArchive.unpack(ZipInputStream(input), temp)
            } ?: PackArchive.Unpack(listOf("无法读取文件"))
        }.getOrElse { PackArchive.Unpack(listOf("解压失败：" + (it.message ?: ""))) }
        if (unpack.errors.isNotEmpty()) {
            temp.deleteRecursively()
            return@withContext PackImportResult.Error(unpack.errors)
        }
        // 同名包重复导入视为覆盖：自己的动作 id 不算冲突，别的包和内置动作算。
        val selfId = PackArchive.readManifestId(temp)
        val reserved = AiActions.builtin.map { it.id }.toSet() +
            list().filter { it.id != selfId }.flatMap { pack -> pack.actions.map { it.id } }.toSet()
        when (val check = PackArchive.validate(temp, reserved)) {
            is PackArchive.Check.Fail -> {
                temp.deleteRecursively()
                PackImportResult.Error(check.errors.ifEmpty { listOf("导入失败") })
            }
            is PackArchive.Check.Ok -> {
                val target = File(root, check.pack.id)
                target.deleteRecursively()
                temp.copyRecursively(target, overwrite = true)
                temp.deleteRecursively()
                PackImportResult.Ok(check.pack)
            }
        }
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