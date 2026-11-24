package com.creativeworkbench.writer.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class JsonSalvageTest {

    @Test
    fun extractsObjectEvenWithTrailingProseContainingBraces() {
        val raw = "好的，结果如下：\n{\"issues\":[{\"blockId\":\"b0\",\"message\":\"缺逗号\"}]}\n\n（说明：{括号}只是说明）"
        val json = JsonSalvage.extractObject(raw)
        assertNotNull(json)
        assertEquals(1, JSONObject(json!!).getJSONArray("issues").length())
    }

    @Test
    fun stripsCodeFence() {
        val raw = "\u0060\u0060\u0060json\n{\"issues\":[]}\n\u0060\u0060\u0060"
        assertEquals("{\"issues\":[]}", JsonSalvage.extractObject(raw))
    }

    @Test
    fun removesTrailingCommaOutsideStrings() {
        val raw = "{\"issues\":[],}"
        val json = JsonSalvage.extractObject(raw)
        assertNotNull(json)
        assertEquals(0, JSONObject(json!!).getJSONArray("issues").length())
    }

    @Test
    fun keepsCommaInsideString() {
        val raw = "{\"issues\":[{\"message\":\"a,}\"}]}"
        val json = JsonSalvage.extractObject(raw)
        assertNotNull(json)
        assertEquals("a,}", JSONObject(json!!).getJSONArray("issues").getJSONObject(0).getString("message"))
    }

    @Test
    fun normalizesSmartQuotesWhenNoAsciiQuote() {
        val raw = "\u007b\u201cissues\u201d:\u005b\u005d\u007d"
        val json = JsonSalvage.extractObject(raw)
        assertNotNull(json)
        assertTrue(json!!.contains('"'))
    }

    @Test
    fun returnsNullForTruncatedObject() {
        assertNull(JsonSalvage.extractObject("{\"issues\":[{\"message\":\"x\""))
    }

    @Test
    fun handlesBracesAndEscapesInsideStrings() {
        val raw = "{\"issues\":[{\"message\":\"含 } 和 \\\" 引号\"}]}"
        val json = JsonSalvage.extractObject(raw)
        assertNotNull(json)
        assertEquals(1, JSONObject(json!!).getJSONArray("issues").length())
    }

    @Test
    fun issueParserAcceptsFencedRepairedJson() {
        val raw = "\u0060\u0060\u0060json\n{\"issues\":[{\"blockId\":\"b1\",\"excerpt\":\"雨\",\"severity\":\"low\",\"message\":\"重复\"},]}\n\u0060\u0060\u0060"
        val issues = AiIssueParser.parse(raw)
        assertNotNull(issues)
        assertEquals(1, issues!!.size)
        assertEquals("b1", issues[0].blockId)
    }
}
