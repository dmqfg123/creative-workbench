package com.creativeworkbench.writer.core.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API 配置：普通字段进 SharedPreferences，密钥用 Android Keystore 加密后再落盘。
 * 密钥永远不进备份、日志、崩溃日志。
 * 0.11.0 起只存「地址 / 模型 / 深度思考」，旧的温度、max_tokens、窗口、超时四个键在保存时清掉。
 */
class AiConfigStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("workbench_ai", Context.MODE_PRIVATE)

    fun config(): AiConfig {
        // 只在一个键都没有时给默认地址；用户手填的空地址要保留成空，
        // 否则「自己搭的兼容服务」里清空输入框、保存后又被悄悄换回官方地址。
        val storedBase = if (prefs.contains("base_url")) prefs.getString("base_url", "") ?: "" else ModelCatalog.DEEPSEEK_BASE_URL
        val storedModel = prefs.getString("model", "") ?: ""
        return AiConfig(
            baseUrl = storedBase.trim(),
            model = storedModel.trim().ifBlank { ModelCatalog.DEFAULT_MODEL },
            deepThinking = prefs.getBoolean("deep_thinking", false),
            allowCleartextHttp = prefs.getBoolean("allow_cleartext", false)
        )
    }

    fun save(config: AiConfig) {
        prefs.edit()
            .putString("base_url", config.baseUrl.trim())
            .putString("model", config.model.trim().ifBlank { ModelCatalog.DEFAULT_MODEL })
            .putBoolean("deep_thinking", config.deepThinking)
            .putBoolean("allow_cleartext", config.allowCleartextHttp)
            // 旧版本让用户手填的参数：现在按模型自动推导，键留着只会误导。
            .remove("temperature")
            .remove("max_tokens")
            .remove("timeout_seconds")
            .remove("context_window")
            .remove("reserved_output")
            .apply()
    }

    fun saveApiKey(key: String) {
        if (key.isBlank()) {
            clearApiKey()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit().putString("api_key_enc", payload).apply()
    }

    fun apiKey(): String {
        val payload = prefs.getString("api_key_enc", null) ?: return ""
        return runCatching {
            val (ivPart, dataPart) = payload.split(":", limit = 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, Base64.decode(ivPart, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(dataPart, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrDefault("")
    }

    fun maskedKey(): String {
        val key = apiKey()
        if (key.isBlank()) return "未配置"
        return if (key.length <= 8) "••••" else key.take(4) + "••••" + key.takeLast(4)
    }

    fun clearApiKey() {
        prefs.edit().remove("api_key_enc").apply()
    }

    fun isConfigured(): Boolean = apiKey().isNotBlank() && config().baseUrl.isNotBlank()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "workbench_ai_key"
    }
}
