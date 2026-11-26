package com.creativeworkbench.writer.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 酒馆预设导入：格式容错是重点——ST 的预设文件有多种历史写法，
 * 解析错了不会崩，但会静默丢掉用户的提示词。
 */
class StPresetParserTest {

    @Test
    fun readsPromptsArrayWithOrder() {
        val json = """
            {
              "temperature": 1.0,
              "prompts": [
                {"identifier":"main","role":"system","content":"主提示"},
                {"identifier":"jailbreak","role":"system","content":"越狱提示"},
                {"identifier":"post_history_instructions","role":"system","content":"后期指令"}
              ],
              "prompt_order": [{"character_id": 100000, "order": [
                {"identifier":"main"},{"identifier":"jailbreak"},{"identifier":"post_history_instructions"}
              ]}],
              "assistant_prefill": "他推开门，"
            }
        """.trimIndent()
        val p = StPresetParser.parse(json)
        assertTrue(p.system.contains("主提示"))
        assertTrue(p.system.contains("越狱提示"))
        assertEquals("后期指令", p.postHistory)
        assertEquals("他推开门，", p.prefill)
        assertTrue(p.imported.contains("post_history_instructions"))
    }

    @Test
    fun readsSettingsJsonWithOpenaiSettings() {
        val json = """
            {"openai_settings":[{"prompts":[{"identifier":"main","content":"来自设置文件"}],"assistant_prefill":"续"}]}
        """.trimIndent()
        val p = StPresetParser.parse(json)
        assertEquals("来自设置文件", p.system)
        assertEquals("续", p.prefill)
    }

    @Test
    fun legacyFlatFieldsStillWork() {
        val json = """{"main_prompt":"旧主提示","jailbreak_prompt":"旧越狱","nsfw_prompt":"旧NSFW"}"""
        val p = StPresetParser.parse(json)
        assertTrue(p.system.contains("旧主提示"))
        assertTrue(p.system.contains("旧NSFW"))
    }

    @Test
    fun garbageReportsErrorInsteadOfThrowing() {
        val p = StPresetParser.parse("not json at all")
        assertNotNull(p.error)
        assertTrue(p.isEmpty)
    }
}
