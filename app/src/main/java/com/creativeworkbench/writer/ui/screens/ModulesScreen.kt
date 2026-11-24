package com.creativeworkbench.writer.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creativeworkbench.writer.WorkbenchApp
import com.creativeworkbench.writer.core.ai.ActionPack
import com.creativeworkbench.writer.core.ai.PackImportResult
import com.creativeworkbench.writer.ui.components.ActionSheet
import com.creativeworkbench.writer.ui.components.SheetAction
import com.creativeworkbench.writer.ui.design.GhostButton
import com.creativeworkbench.writer.ui.design.IconAction
import com.creativeworkbench.writer.ui.design.SectionLabel
import com.creativeworkbench.writer.ui.design.WbCard
import kotlinx.coroutines.launch

/**
 * 模块与扩展：设定笔记可关；内核项单列说明；动作包只含 AI 动作与提示词。
 */
@Composable
fun ModulesScreen(
    notesEnabled: Boolean,
    onToggleNotes: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val container = (context.applicationContext as WorkbenchApp).container
    var packs by remember { mutableStateOf<List<ActionPack>>(emptyList()) }
    var deleting by remember { mutableStateOf<ActionPack?>(null) }
    var errorMessages by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { packs = container.plugins.list() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                when (val result = container.plugins.import(uri)) {
                    is PackImportResult.Ok -> {
                        Toast.makeText(context, "已导入「" + result.pack.name + "」", Toast.LENGTH_SHORT).show()
                        packs = container.plugins.list()
                    }
                    is PackImportResult.Error -> errorMessages = result.messages
                }
            }
        }
    }

    if (errorMessages.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { errorMessages = emptyList() },
            title = { Text("动作包没有导入") },
            text = {
                Column {
                    errorMessages.take(8).forEach { message ->
                        Text("· " + message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { errorMessages = emptyList() }) { Text("知道了") } },
            shape = MaterialTheme.shapes.large
        )
    }

    deleting?.let { pack ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除动作包「" + pack.name + "」？") },
            text = { Text("删除后动作立即消失；历史记录保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        container.plugins.delete(pack.id)
                        packs = container.plugins.list()
                        deleting = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
            shape = MaterialTheme.shapes.large
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconAction(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
            Text("模块与扩展", style = MaterialTheme.typography.titleMedium)
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            SectionLabel("可关模块")
            WbCard(contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("设定笔记", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        Text(
                            "关掉后：笔记 Tab、编辑器笔记抽屉、AI 上下文里的笔记一起消失；数据保留，重开恢复。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = notesEnabled, onCheckedChange = onToggleNotes)
                }
            }

            SectionLabel("内核（不可关）")
            WbCard(contentPadding = PaddingValues(16.dp)) {
                Text(
                    "书架、编辑器、AI、备份、设置属于内核，不提供关闭开关；这样做是为了避免关掉后入口和数据对不上。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionLabel("AI 动作包")
            if (packs.isEmpty()) {
                WbCard(contentPadding = PaddingValues(16.dp)) {
                    Text("还没有导入动作包", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "包是 zip（.wbplugin）：manifest.json + actions/*.json + prompts/*.md。只加 AI 动作，不改界面。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    GhostButton(
                        text = "导入动作包",
                        onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }
                    )
                }
            } else {
                packs.forEach { pack ->
                    WbCard(contentPadding = PaddingValues(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(pack.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    pack.id + " · v" + pack.version + " · " + pack.actions.size + " 个动作",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { deleting = pack }) { Text("删除") }
                        }
                        pack.actions.forEach { action ->
                            Text(
                                "· " + action.name + "（" + action.result.name + " / " + action.write.name + "）",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                GhostButton(
                    text = "继续导入",
                    onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
