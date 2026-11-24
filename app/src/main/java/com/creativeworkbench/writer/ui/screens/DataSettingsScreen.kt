package com.creativeworkbench.writer.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.creativeworkbench.writer.WorkbenchApp
import com.creativeworkbench.writer.core.backup.BackupBundle
import com.creativeworkbench.writer.core.backup.ImportResult
import com.creativeworkbench.writer.core.common.CrashLogger
import com.creativeworkbench.writer.core.data.WorkEntity
import com.creativeworkbench.writer.ui.components.ActionSheet
import com.creativeworkbench.writer.ui.components.SheetAction
import com.creativeworkbench.writer.ui.design.GhostButton
import com.creativeworkbench.writer.ui.design.IconAction
import com.creativeworkbench.writer.ui.design.PillButton
import com.creativeworkbench.writer.ui.design.SectionLabel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据与备份：本地自动备份 + SAF 手动导出/导入 + 单作品导出 + 清空 + 崩溃日志。
 * 不做云、不做账号；清空前必须先落一份外部备份。
 */
@Composable
fun DataSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as WorkbenchApp
    val backup = app.container.backup
    val container = app.container
    val scope = rememberCoroutineScope()
    var works by remember { mutableStateOf<List<WorkEntity>>(emptyList()) }
    var localFiles by remember { mutableStateOf(listOf<java.io.File>()) }
    var crashFiles by remember { mutableStateOf(listOf<java.io.File>()) }
    var lastBackup by remember { mutableStateOf(app.container.settings.settings.value.lastBackupAt) }
    var noteCount by remember { mutableStateOf(0) }
    var lastSafAt by remember { mutableStateOf(app.container.settings.settings.value.lastSafBackupAt) }
    var workPicker by remember { mutableStateOf(false) }
    var exportTarget by remember { mutableStateOf<WorkEntity?>(null) }
    var exportMarkdown by remember { mutableStateOf(true) }
    var confirmClear by remember { mutableStateOf(false) }
    var clearText by remember { mutableStateOf("") }
    var pendingClear by remember { mutableStateOf(false) }
    var includeKey by remember { mutableStateOf(false) }
    var confirmIncludeKey by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<BackupBundle?>(null) }

    LaunchedEffect(Unit) {
        works = container.database.workDao().observeAll().first()
        localFiles = backup.localBackups()
        crashFiles = CrashLogger.list(context)
        noteCount = container.notes.count()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = backup.export(uri, gzip = true, includeApiKey = includeKey)
                val message = if (result.isSuccess) "已导出备份" else "导出失败：" + result.exceptionOrNull()?.message
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                if (pendingClear && result.isSuccess) {
                    pendingClear = false
                    val cleared = backup.clearAll()
                    Toast.makeText(
                        context,
                        if (cleared.isSuccess) "已清空" else "清空失败：" + cleared.exceptionOrNull()?.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            pendingClear = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val (bundle, error) = backup.readBundle(uri)
                if (bundle == null) {
                    Toast.makeText(context, error ?: "读取失败", Toast.LENGTH_LONG).show()
                } else {
                    // 先让用户看清要导入多少，再整包替换
                    pendingImport = bundle
                }
            }
        }
    }

    fun finishExport(uri: android.net.Uri?, markdown: Boolean) {
        val target = exportTarget
        if (uri != null && target != null) {
            scope.launch {
                val result = backup.exportWork(target.id, markdown, uri)
                Toast.makeText(
                    context,
                    if (result.isSuccess) "已导出《" + target.title + "》" else "导出失败",
                    Toast.LENGTH_SHORT
                ).show()
                exportTarget = null
            }
        } else {
            exportTarget = null
        }
    }

    val markdownExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri -> finishExport(uri, markdown = true) }

    val txtExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> finishExport(uri, markdown = false) }

    var formatPicker by remember { mutableStateOf(false) }

    val crashExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val latest = CrashLogger.list(context).firstOrNull()
                if (latest == null) {
                    Toast.makeText(context, "没有崩溃日志", Toast.LENGTH_SHORT).show()
                } else {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(latest.readText().toByteArray(Charsets.UTF_8))
                        }
                    }
                    Toast.makeText(context, "已导出崩溃日志", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (workPicker) {
        ActionSheet(
            title = "导出哪部作品",
            subtitle = "按目录顺序导出，TXT 或 Markdown",
            actions = works.map { work ->
                SheetAction(
                    label = work.title,
                    description = work.chapterCount.toString() + " 章 · " + work.wordCount + " 字",
                    onClick = {
                        workPicker = false
                        exportTarget = work
                        formatPicker = true
                    }
                )
            },
            onDismiss = { workPicker = false }
        )
    }

    if (formatPicker) {
        ActionSheet(
            title = "导出格式",
            subtitle = exportTarget?.title,
            actions = listOf(
                SheetAction(
                    label = "Markdown（.md）",
                    description = "作品名一级标题，章二级标题",
                    onClick = {
                        formatPicker = false
                        markdownExportLauncher.launch((exportTarget?.title ?: "作品") + ".md")
                    }
                ),
                SheetAction(
                    label = "纯文本（.txt）",
                    description = "空行 + 章标题，不带头部符号",
                    onClick = {
                        formatPicker = false
                        txtExportLauncher.launch((exportTarget?.title ?: "作品") + ".txt")
                    }
                )
            ),
            onDismiss = { formatPicker = false }
        )
    }

    pendingImport?.let { bundle ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("导入这份备份？") },
            text = {
                Text(
                    "将导入 " + bundle.works.size + " 部作品 / " + bundle.chapters.size + " 章 / " +
                        bundle.notes.size + " 条笔记 / " + bundle.aiHistory.size + " 条调用历史。\n" +
                        "当前数据会被整包替换；导入前会自动在本机备份一份。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = bundle
                    pendingImport = null
                    scope.launch {
                        when (val result = backup.importReplace(target)) {
                            is ImportResult.Ok -> {
                                Toast.makeText(
                                    context,
                                    "已恢复 " + result.preview.works + " 部作品 / " + result.preview.notes + " 条笔记",
                                    Toast.LENGTH_LONG
                                ).show()
                                works = container.database.workDao().observeAll().first()
                                noteCount = container.notes.count()
                                localFiles = backup.localBackups()
                            }
                            is ImportResult.Error ->
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("取消") } },
            shape = MaterialTheme.shapes.large
        )
    }

    if (confirmIncludeKey) {
        AlertDialog(
            onDismissRequest = { confirmIncludeKey = false },
            title = { Text("备份里带密钥？") },
            text = { Text("导出的 .json.gz 会包含明文 API 密钥，放在手机或网盘里都可能被别人读到。只在换机或重装时用，用完记得删掉。") },
            confirmButton = {
                TextButton(onClick = {
                    includeKey = true
                    confirmIncludeKey = false
                }) { Text("我知道了") }
            },
            dismissButton = { TextButton(onClick = { confirmIncludeKey = false }) { Text("取消") } },
            shape = MaterialTheme.shapes.large
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空全部数据") },
            text = {
                Column {
                    Text("会先导出备份到文件选择器，再清空本机数据。输入「清空」确认。")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = clearText,
                        onValueChange = { clearText = it },
                        singleLine = true,
                        label = { Text("输入「清空」") },
                        shape = MaterialTheme.shapes.small
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        clearText = ""
                        pendingClear = true
                        exportLauncher.launch("雨刮模拟器备份.json.gz")
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    enabled = clearText.trim() == "清空"
                ) { Text("先备份，再清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
            shape = MaterialTheme.shapes.large
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconAction(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
            Text("数据与备份", style = MaterialTheme.typography.titleMedium)
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "本机数据：作品 " + works.size + " 部 · 笔记 " + noteCount + " 条",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "上次备份：" + (lastBackup?.let { formatTime(it) } ?: "还没有"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "卸载 App 会删掉私有目录，换手机前先导出。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionLabel("备份")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(text = "导出全量备份", onClick = { exportLauncher.launch("雨刮模拟器备份.json.gz") })
                GhostButton(text = "导入恢复", onClick = { importLauncher.launch(arrayOf("application/gzip", "application/json", "application/octet-stream")) })
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("导出包含 API 密钥", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "默认关闭。打开后备份里会有明文密钥，换机更方便，但文件要自己看紧。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = includeKey,
                    onCheckedChange = { checked ->
                        if (checked) confirmIncludeKey = true else includeKey = false
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                scope.launch {
                    val file = backup.writeLocalBackup()
                    localFiles = backup.localBackups()
                    lastBackup = System.currentTimeMillis()
                    Toast.makeText(context, "已备份：" + file.name, Toast.LENGTH_SHORT).show()
                }
            }) { Text("立即在本机备份一份") }

            localFiles.take(3).forEach { file ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(file.name, style = MaterialTheme.typography.labelMedium)
                        Text(
                            formatSize(file.length()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = {
                        scope.launch {
                            val (bundle, error) = backup.readLocalBackup(file)
                            if (bundle == null) {
                                Toast.makeText(context, error ?: "读取失败", Toast.LENGTH_LONG).show()
                            } else {
                                when (val result = backup.importReplace(bundle)) {
                                    is ImportResult.Ok -> {
                                        Toast.makeText(context, "已从本机备份恢复", Toast.LENGTH_SHORT).show()
                                        works = container.database.workDao().observeAll().first()
                                    }
                                    is ImportResult.Error -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }) { Text("恢复") }
                }
            }

            SectionLabel("每日自动备份")
            Text(
                "每天启动自动写一份到：文件管理 → Download → 雨刮模拟器备份/",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "不用选目录、不用授权；这个文件夹不随卸载消失。上次写入：" +
                    (lastSafAt?.let { formatTime(it) } ?: "还没有"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            GhostButton(
                text = "立即备份一份",
                onClick = {
                    scope.launch {
                        val result = backup.writeDailyPublicBackup()
                        lastSafAt = System.currentTimeMillis()
                        Toast.makeText(
                            context,
                            result.fold(
                                { "已备份到 Download/雨刮模拟器备份/" + it },
                                { "备份失败：" + (it.message ?: "") }
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )

            SectionLabel("单部作品")
            GhostButton(text = "导出作品 TXT / Markdown", onClick = { workPicker = true })

            SectionLabel("危险操作")
            GhostButton(text = "清空全部数据（先自动备份）", onClick = { confirmClear = true })

            SectionLabel("崩溃日志")
            Text(
                "本机保留 " + crashFiles.size + " 份；只写本地，不联网。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(
                    text = "导出最新日志",
                    onClick = { crashExportLauncher.launch("crash-log.txt") }
                )
                TextButton(onClick = { CrashLogger.triggerDebugCrash() }) {
                    Text("触发一次崩溃（调试）", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

private fun formatTime(time: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(time))

private fun formatSize(bytes: Long): String =
    if (bytes >= 1024 * 1024) String.format(Locale.CHINA, "%.1f MB", bytes / 1024.0 / 1024.0)
    else String.format(Locale.CHINA, "%.0f KB", bytes / 1024.0)