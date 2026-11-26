package com.creativeworkbench.writer.core.ai

import com.creativeworkbench.writer.core.data.NoteEntity
import com.creativeworkbench.writer.core.data.NoteKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「自动挑设定」的本地打分：规则错了，AI 就会拿到无关的旧设定，
 * 而且用户很难从界面上看出是哪一步错的。
 */
class NoteRetrieverTest {

    private fun note(
        id: String,
        kind: String = NoteKind.CHARACTER,
        title: String,
        content: String = "",
        pinned: Boolean = false,
        aliases: String = ""
    ) = NoteEntity(
        id = id,
        workId = "w1",
        kind = kind,
        title = title,
        content = content,
        aliases = aliases,
        pinned = pinned,
        createdAtMs = 0L,
        updatedAtMs = 0L
    )

    @Test
    fun mentionedNoteRanksAboveSilentOne() {
        val notes = listOf(
            note("a", title = "林小满", content = "主角"),
            note("b", title = "老陈", content = "配角")
        )
        val ranked = NoteRetriever.candidates(notes, "林小满推开门，林小满又看了一眼。")
        assertEquals("a", ranked.first().note.id)
        assertEquals(2, ranked.first().mentions)
        assertTrue(ranked.first().reason.contains("提到"))
    }

    @Test
    fun pinnedNoteGetsBonusWhenNothingMatches() {
        val notes = listOf(
            note("a", title = "林小满"),
            note("b", title = "世界观总纲", kind = NoteKind.WORLD, pinned = true)
        )
        val ranked = NoteRetriever.candidates(notes, "今天下雨。")
        assertEquals("b", ranked.first().note.id)
    }

    @Test
    fun aliasesCountAsMentions() {
        val notes = listOf(note("a", title = "林小满", aliases = "小满"))
        val ranked = NoteRetriever.candidates(notes, "小满没说话。")
        assertEquals(1, ranked.first().mentions)
    }

    @Test
    fun budgetStopsAtCapButKeepsFirst() {
        val notes = (1..5).map { index ->
            note("n" + index, title = "人物" + index, content = "设定内容".repeat(200))
        }
        val candidates = NoteRetriever.candidates(notes, "")
        val picked = NoteRetriever.pickByScore(candidates, cap = 300)
        assertEquals("装不下就只留第一条兜底", 1, picked.size)
    }

    @Test
    fun candidatePoolIsCapped() {
        val notes = (1..60).map { index -> note("n" + index, title = "人物" + index) }
        val candidates = NoteRetriever.candidates(notes, "")
        assertEquals(NoteRetriever.POOL_LIMIT, candidates.size)
    }

    @Test
    fun localOnlyPickIsDeterministic() {
        val notes = listOf(
            note("a", title = "林小满"),
            note("b", title = "老陈")
        )
        val selector = NoteSelector(AiClient())
        val first = selector.localOnly(notes, "林小满笑了", "", cap = 10_000)
        val second = selector.localOnly(notes, "林小满笑了", "", cap = 10_000)
        assertEquals(first.chosen.map { it.note.id }, second.chosen.map { it.note.id })
        assertEquals("第一次挑中的就是命中的那条", "a", first.chosen.first().note.id)
    }
}
