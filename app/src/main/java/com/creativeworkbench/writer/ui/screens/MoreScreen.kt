package com.creativeworkbench.writer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creativeworkbench.writer.BuildConfig
import com.creativeworkbench.writer.R
import com.creativeworkbench.writer.core.settings.AppSettings
import com.creativeworkbench.writer.core.settings.FontSizePreset
import com.creativeworkbench.writer.core.settings.ThemeMode
import com.creativeworkbench.writer.ui.AppViewModel
import com.creativeworkbench.writer.ui.design.IconAction
import com.creativeworkbench.writer.ui.design.PageHeader
import com.creativeworkbench.writer.ui.design.SectionLabel
import com.creativeworkbench.writer.ui.design.WbCard

/**
 * 更多：AI、模块、数据、外观。个人工具，不做运营入口。
 */
@Composable
fun MoreScreen(
    viewModel: AppViewModel,
    settings: AppSettings,
    onOpenAi: () -> Unit,
    onOpenAiHistory: () -> Unit,
    onOpenModules: () -> Unit,
    onOpenData: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        PageHeader(title = "更多", subtitle = "AI、模块、数据与外观")

        Column(Modifier.padding(horizontal = 24.dp)) {
            SectionLabel("AI 与内容")
            NavRow(
                icon = painterResource(R.drawable.ic_sparkle),
                title = "AI 助手",
                desc = "配置接口、模型与密钥；续写、改写、检查",
                onClick = onOpenAi
            )
            Spacer(Modifier.height(8.dp))
            NavRow(
                icon = Icons.Default.List,
                title = "调用历史",
                desc = "最近 200 条；写入过的可以撤销",
                onClick = onOpenAiHistory
            )
            Spacer(Modifier.height(8.dp))
            NavRow(
                icon = Icons.Default.Settings,
                title = "模块与扩展",
                desc = "设定笔记开关；动作包导入",
                onClick = onOpenModules
            )
            Spacer(Modifier.height(8.dp))
            NavRow(
                icon = Icons.Default.List,
                title = "数据与备份",
                desc = "导出 / 导入 / 清空 / 单作品导出 / 崩溃日志",
                onClick = onOpenData
            )

            SectionLabel("外观")
            WbCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                Text("主题", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                SegmentedRow(
                    options = listOf("跟随系统", "浅色", "深色"),
                    selected = when (settings.theme) {
                        ThemeMode.System -> 0
                        ThemeMode.Light -> 1
                        ThemeMode.Dark -> 2
                    },
                    onSelect = {
                        viewModel.setTheme(
                            when (it) {
                                0 -> ThemeMode.System
                                1 -> ThemeMode.Light
                                else -> ThemeMode.Dark
                            }
                        )
                    }
                )
                Spacer(Modifier.height(16.dp))
                Text("正文字号", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                SegmentedRow(
                    options = listOf("小", "标准", "大"),
                    selected = when (settings.fontSize) {
                        FontSizePreset.Small -> 0
                        FontSizePreset.Standard -> 1
                        FontSizePreset.Large -> 2
                    },
                    onSelect = {
                        viewModel.setFontSize(
                            when (it) {
                                0 -> FontSizePreset.Small
                                1 -> FontSizePreset.Standard
                                else -> FontSizePreset.Large
                            }
                        )
                    }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "正文 17 / 行高 31；只作用于正文，行距跟着字号变。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionLabel("关于")
            WbCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("雨刮模拟器", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "版本 " + BuildConfig.VERSION_NAME + " · 单机 · GPL-3.0",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "所有数据只在本机；除 AI 调用外不联网。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))
            SwitchRow(
                title = "允许明文 HTTP",
                desc = "默认只允许 https；本机 Ollama / LM Studio 才需要打开",
                checked = settings.allowCleartextHttp,
                onChange = viewModel::setAllowCleartextHttp
            )
            SwitchRow(
                title = "显示帧率",
                desc = "只在编辑器生效，默认关闭",
                checked = settings.fpsOverlay,
                onChange = viewModel::setFpsOverlay
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    WbCard(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(
                    desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NavRow(
    icon: androidx.compose.ui.graphics.painter.Painter,
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    WbCard(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(
                    desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentedRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = selected == index,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(label, fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Normal) }
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
