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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.creativeworkbench.writer.WorkbenchApp
import com.creativeworkbench.writer.core.ai.AiCallResult
import com.creativeworkbench.writer.core.ai.AiClient
import com.creativeworkbench.writer.core.ai.AiConfig
import com.creativeworkbench.writer.core.ai.ChatMessage
import com.creativeworkbench.writer.core.ai.ModelCatalog
import com.creativeworkbench.writer.core.ai.SearchProvider
import com.creativeworkbench.writer.core.ai.WebSearchClient
import com.creativeworkbench.writer.core.ai.WebSearchOutcome
import com.creativeworkbench.writer.ui.design.ChipRow
import com.creativeworkbench.writer.ui.design.GhostButton
import com.creativeworkbench.writer.ui.design.IconAction
import com.creativeworkbench.writer.ui.design.PillButton
import com.creativeworkbench.writer.ui.design.SectionLabel
import com.creativeworkbench.writer.ui.design.Tag
import kotlinx.coroutines.launch

/**
 * AI 配置：只让用户填「地址 / 模型 / 密钥」，再加一个懂人话的「深度思考」。
 * 上下文窗口、输出上限、temperature、超时全部按模型自动推导（见 ModelCatalog），
 * 用户填这些原始 API 参数只会把请求打出 400。
 */
@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as WorkbenchApp
    val store = app.container.aiConfig
    val initial = remember { store.config() }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var model by remember { mutableStateOf(initial.model) }
    var apiKey by remember { mutableStateOf("") }
    var deepThinking by remember { mutableStateOf(initial.deepThinking) }
    var allowCleartext by remember { mutableStateOf(initial.allowCleartextHttp) }
    var status by remember { mutableStateOf<String?>(null) }
    var searchProvider by remember { mutableStateOf(store.searchProvider()) }
    var searchKey by remember { mutableStateOf("") }
    var customSearchUrl by remember { mutableStateOf(store.customSearchUrl()) }
    var searchStatus by remember { mutableStateOf<String?>(null) }
    // 默认关：常规生成一条附加指令都不加，让模型完全按作者意愿写
    // （见 参考/内容拒写与提示词_20261126.md）。
    var privateNote by remember {
        mutableStateOf(app.container.settings.aiPref("private_use_note", "false").toBoolean())
    }
    val scope = rememberCoroutineScope()

    fun currentConfig() = AiConfig(
        baseUrl = baseUrl.trim(),
        model = model.trim(),
        deepThinking = deepThinking,
        allowCleartextHttp = allowCleartext
    )

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconAction(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
            Text("AI 助手", style = MaterialTheme.typography.titleMedium)
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Text(
                "密钥加密存本机，不进备份。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionLabel("接口")
            ChipRow {
                Tag(
                    text = "DeepSeek 官方",
                    active = baseUrl.contains("deepseek", ignoreCase = true),
                    onClick = {
                        baseUrl = ModelCatalog.DEEPSEEK_BASE_URL
                        if (model.isBlank()) model = ModelCatalog.DEFAULT_MODEL
                    }
                )
                Tag(
                    text = "自己搭的兼容服务",
                    active = !baseUrl.contains("deepseek", ignoreCase = true) && !baseUrl.contains(":11434"),
                    onClick = { if (baseUrl.contains("deepseek", ignoreCase = true)) baseUrl = "" }
                )
                Tag(
                    text = "本机模型（Ollama）",
                    active = baseUrl.contains(":11434"),
                    onClick = {
                        // 手机上的 127.0.0.1 是手机自己，必须填电脑的局域网 IP。
                        // Ollama 默认只监听 127.0.0.1，要在电脑上设 OLLAMA_HOST=0.0.0.0 才连得上。
                        baseUrl = "http://192.168.1.100:11434/v1"
                        allowCleartext = true
                        status = "把 IP 换成电脑的局域网地址；电脑端先设置 OLLAMA_HOST=0.0.0.0 再 ollama serve"
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                placeholder = { Text(ModelCatalog.DEEPSEEK_BASE_URL) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("模型") },
                placeholder = { Text(ModelCatalog.DEFAULT_MODEL) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            )
            Spacer(Modifier.height(8.dp))
            ChipRow {
                ModelCatalog.presets.forEach { preset ->
                    Tag(
                        text = preset.model,
                        active = model.trim().equals(preset.model, ignoreCase = true),
                        onClick = { model = preset.model }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key（留空则不改）") },
                placeholder = { Text("当前：" + store.maskedKey()) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            )

            SectionLabel("写法")
            // 只有支持思考模式的模型（DeepSeek）才给这个开关；别家模型放上去点了也没用。
            if (currentConfig().profile.supportsThinking) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("深度思考", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "更慢更贵，默认关。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = deepThinking, onCheckedChange = { deepThinking = it })
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            // 可选：每次生成都附一行「本机私用」声明，压制模型凭空引用平台规则。
            // 默认关——作者要的是最大自由度；拒写时仍有自动重试兜底。
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("本机私用声明", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (privateNote) {
                                "每次生成都附一行：没有平台、只给作者本人看。不会限制题材。"
                            } else {
                                "默认不加任何附加指令；模型拒写时会自动重试一次。"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = privateNote,
                        onCheckedChange = {
                            privateNote = it
                            app.container.settings.rememberAiPref("private_use_note", it.toString())
                        }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            AutoParamsCard(
                config = currentConfig()
            )

            Spacer(Modifier.height(12.dp))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("允许明文 HTTP", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "本机服务（Ollama 等）才需要",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = allowCleartext, onCheckedChange = { allowCleartext = it })
                }
            }

            SectionLabel("联网搜索（AI 对话窗口用）")
            Text(
                "对话窗口的「联网」用这里的检索。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            ChipRow {
                SearchProvider.entries.forEach { provider ->
                    Tag(
                        text = provider.label,
                        active = searchProvider == provider,
                        onClick = { searchProvider = provider }
                    )
                }
            }
            Text(
                searchProvider.note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
            )
            if (searchProvider.needsKey) {
                OutlinedTextField(
                    value = searchKey,
                    onValueChange = { searchKey = it },
                    label = { Text(searchProvider.label + " API Key（留空则不改）") },
                    placeholder = { Text("当前：" + store.maskedSearchKey()) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )
            }
            if (searchProvider == SearchProvider.CUSTOM) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customSearchUrl,
                    onValueChange = { customSearchUrl = it },
                    label = { Text("自定义检索地址（POST {\"query\":\"...\"}）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(
                    text = "保存联网设置",
                    onClick = {
                        store.saveSearchProvider(searchProvider)
                        store.saveCustomSearchUrl(customSearchUrl)
                        if (searchKey.isNotBlank()) store.saveSearchKey(searchKey.trim())
                        searchKey = ""
                        searchStatus = "已保存"
                    }
                )
                GhostButton(
                    text = "测试检索",
                    onClick = {
                        if (searchKey.isNotBlank()) store.saveSearchKey(searchKey.trim())
                        store.saveSearchProvider(searchProvider)
                        store.saveCustomSearchUrl(customSearchUrl)
                        searchKey = ""
                        searchStatus = "检索中…"
                        scope.launch {
                            val outcome = WebSearchClient().search(
                                query = "今天杭州天气",
                                provider = searchProvider,
                                apiKey = store.searchKey(),
                                customUrl = store.customSearchUrl()
                            )
                            searchStatus = when (outcome) {
                                is WebSearchOutcome.Ok ->
                                    "检索正常（" + outcome.provider + "）：" +
                                        outcome.hits.first().title.take(30)
                                is WebSearchOutcome.Error -> outcome.message
                            }
                        }
                    }
                )
            }
            searchStatus?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(
                    text = "保存",
                    onClick = {
                        val config = currentConfig()
                        if (config.baseUrl.isBlank() || config.model.isBlank()) {
                            status = "Base URL 和模型不能为空"
                            return@PillButton
                        }
                        store.save(config)
                        if (apiKey.isNotBlank()) store.saveApiKey(apiKey.trim())
                        apiKey = ""
                        status = "已保存"
                    }
                )
                GhostButton(
                    text = "测试连接",
                    onClick = {
                        val config = currentConfig()
                        if (config.baseUrl.isBlank() || config.model.isBlank()) {
                            status = "先填 Base URL 和模型"
                            return@GhostButton
                        }
                        if (apiKey.isNotBlank()) store.saveApiKey(apiKey.trim())
                        apiKey = ""
                        val key = store.apiKey()
                        if (key.isBlank()) {
                            status = "先填 API Key"
                            return@GhostButton
                        }
                        status = "测试中…"
                        scope.launch {
                            val result = AiClient().chat(
                                config,
                                key,
                                listOf(ChatMessage("user", "只回复 OK"))
                            )
                            status = when (result) {
                                is AiCallResult.Ok -> "连接正常：" + result.content.take(40)
                                is AiCallResult.Error -> result.message
                            }
                        }
                    }
                )
            }
            status?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** 把自动推导出来的参数摊开给人看：不给改，但也不藏着。 */
@Composable
private fun AutoParamsCard(config: AiConfig) {
    val profile = config.profile
    val params = config.effectiveParams
    val mode = when {
        !profile.supportsThinking -> profile.label
        params.thinking -> "DeepSeek · 深度思考"
        else -> "DeepSeek · 快速"
    }
    val temperature = params.temperature?.toString() ?: "不发（思考模式忽略）"
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                "自动套用 · " + mode,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            ParamLine("上下文窗口", formatTokens(profile.contextWindowTokens) + " tokens（每次最多带 " + formatTokens(profile.budgetWindowTokens) + "）")
            ParamLine("单次输出上限", formatTokens(params.maxTokens) + " tokens")
            ParamLine("temperature", temperature)
            ParamLine("超时", params.timeoutSeconds.toString() + " 秒")
            Spacer(Modifier.height(6.dp))
            Text(
                "按模型自动给，不用填。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ParamLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatTokens(value: Int): String = when {
    value >= 1_000_000 -> (value / 1_000_000).toString() + "M"
    value % 1024 == 0 -> (value / 1024).toString() + "K"
    else -> String.format(java.util.Locale.US, "%,d", value)
}
