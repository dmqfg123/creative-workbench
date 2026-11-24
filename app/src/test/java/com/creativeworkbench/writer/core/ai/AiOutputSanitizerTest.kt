package com.creativeworkbench.writer.core.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiOutputSanitizerTest {
    @Test
    fun stripsBracketLabels() {
        assertEquals(
            "钟声停下后，走廊尽头传来呼吸。",
            AiOutputSanitizer.clean("【模拟续写】钟声停下后，走廊尽头传来呼吸。")
        )
        assertEquals(
            "他推开门。",
            AiOutputSanitizer.clean("【续写】\n他推开门。")
        )
    }

    @Test
    fun stripsColonLabelsAndFences() {
        assertEquals("雨落在铁皮上。", AiOutputSanitizer.clean("```\n改写：雨落在铁皮上。\n```"))
    }

    @Test
    fun keepsNormalBracketsInBody() {
        assertEquals(
            "【雨夜】他站在门口。",
            AiOutputSanitizer.clean("【雨夜】他站在门口。")
        )
    }

    @Test
    fun collapsesBlankLines() {
        assertEquals("一\n\n二", AiOutputSanitizer.clean("一\n\n\n\n二"))
    }
}
