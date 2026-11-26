package com.creativeworkbench.writer.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiIssueParserTest {
    @Test
    fun parsesFencedJson() {
        val raw = "```json\n{\"issues\":[{\"blockId\":\"b1\",\"excerpt\":\"他笑了\",\"severity\":\"medium\",\"message\":\"与前文矛盾\"}]}\n```"
        val issues = AiIssueParser.parse(raw)
        assertEquals(1, issues?.size)
        assertEquals("b1", issues?.first()?.blockId)
        assertEquals("medium", issues?.first()?.severity)
    }

    @Test
    fun parsesPlainJsonWithExtraProse() {
        val raw = "检查结果如下：{\"issues\":[{\"blockId\":\"b2\",\"message\":\"错别字\"}]} 以上。"
        val issues = AiIssueParser.parse(raw)
        assertEquals(1, issues?.size)
        assertEquals("错别字", issues?.first()?.message)
    }

    @Test
    fun returnsNullForNonJson() {
        assertNull(AiIssueParser.parse("模型没有按 JSON 返回"))
    }

    @Test
    fun emptyIssuesIsValid() {
        val issues = AiIssueParser.parse("{\"issues\":[]}")
        assertEquals(0, issues?.size)
    }
}
