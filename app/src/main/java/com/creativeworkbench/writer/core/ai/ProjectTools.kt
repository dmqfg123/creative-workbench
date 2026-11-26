package com.creativeworkbench.writer.core.ai

import com.creativeworkbench.writer.core.data.Block
import com.creativeworkbench.writer.core.data.ChapterEntity
import com.creativeworkbench.writer.core.data.ChapterRepository
import com.creativeworkbench.writer.core.data.ForeshadowingRepository
import com.creativeworkbench.writer.core.data.NoteEntity
import com.creativeworkbench.writer.core.data.NoteKind
import com.creativeworkbench.writer.core.data.NoteRepository
import com.creativeworkbench.writer.core.data.OutlineNodeEntity
import com.creativeworkbench.writer.core.data.OutlineRepository
import com.creativeworkbench.writer.core.data.SaveResult
import com.creativeworkbench.writer.core.data.UpdateSource
import org.json.JSONObject
import java.util.UUID

/**
 * 项目工具：让 AI 对话能真正读写这个 App 的四个板块（笔记 / 大纲 / 正文 / 伏笔）。
 *
 * 两条铁律：
 * 1. **读工具直接执行**（查资料、读章节不需要用户同意）；
 * 2. **写工具只出提案**，用户点「应用」才落库——和编辑器里 AI 写入必须二次确认是同一条规则。
 *    提案里带完整参数，用户在卡片上看得到要写什么，不会出现「模型偷偷改了正文」。
 *
 * 参考：
 * - Novelcrafter「Chat with Codex」：聊天里能引用/新建 Codex 条目，但改动要落到场景卡上；
 * - Cursor / Claude Code 的「工具调用 → diff → 接受」三段式；
 * - SillyTavern 的「模型只能通过既定工具读写，平台掌握上下文装配权」。
 */
class ProjectTools(
    private val chapters: ChapterRepository,
    private val notes: NoteRepository,
    private val outline: OutlineRepository,
    private val foreshadowing: ForeshadowingRepository,
    private val workId: String?,
    private val currentChapterId: String?
) {

    fun specs(): List<AiToolSpec> = listOf(
        AiToolSpec(
            name = "list_chapters",
            description = "列出当前作品的全部章节（序号、标题、字数、有没有摘要）。写入正文或摘要前先用它确认是哪一章。",
            parameters = ToolSchema.obj(emptyList())
        ),
        AiToolSpec(
            name = "read_chapter",
            description = "读取某一章的正文（最多 " + MAX_READ_CHARS + " 字，超长会截断）。chapter 填章节标题、第N章或章节 id；不填就是当前章。",
            parameters = ToolSchema.obj(
                emptyList(),
                "chapter" to ToolSchema.str("章节标题 / 第N章 / 章节 id，留空=当前章")
            )
        ),
        AiToolSpec(
            name = "search_notes",
            description = "按关键词搜笔记（人物、设定、灵感、片段、对白、备忘），返回短 id、类型、标题和摘要。想了解已有设定时先搜，不要凭空假设。",
            parameters = ToolSchema.obj(
                listOf("query"),
                "query" to ToolSchema.str("关键词，例如人物名或设定名"),
                "kind" to ToolSchema.str(
                    "只看某一类（可空）",
                    NoteKind.CHARACTER, NoteKind.WORLD, NoteKind.IDEA,
                    NoteKind.FRAGMENT, NoteKind.DIALOGUE, NoteKind.MEMO
                )
            )
        ),
        AiToolSpec(
            name = "read_note",
            description = "读一条笔记的完整内容。id 可以是搜索结果的短 id。",
            parameters = ToolSchema.obj(
                listOf("id"),
                "id" to ToolSchema.str("笔记 id（短 id 即可）")
            )
        ),
        AiToolSpec(
            name = "list_outline",
            description = "列出当前作品的大纲节点（短 id、标题、要点、绑定章节、目标字数）。",
            parameters = ToolSchema.obj(emptyList())
        ),
        AiToolSpec(
            name = "list_foreshadowing",
            description = "列出伏笔账本（埋了什么、计划哪章回收、当前状态）。",
            parameters = ToolSchema.obj(emptyList())
        ),
        AiToolSpec(
            name = "create_note",
            description = "在笔记板块新建一条。讨论出了人物/世界观/灵感/片段/对白，或用户让你「记下来」时调用。" +
                "content 要写完整内容，不要写「见上文」。" +
                "信息够了就直接调用，不必先搜一遍笔记确认有没有重复。",
            parameters = ToolSchema.obj(
                listOf("kind", "title", "content"),
                "kind" to ToolSchema.str(
                    "类型",
                    NoteKind.CHARACTER, NoteKind.WORLD, NoteKind.IDEA,
                    NoteKind.FRAGMENT, NoteKind.DIALOGUE, NoteKind.MEMO
                ),
                "title" to ToolSchema.str("标题，例如人物名或设定名"),
                "content" to ToolSchema.str("完整内容"),
                "aliases" to ToolSchema.str("别名，逗号分隔（可空）")
            ),
            mutating = true,
            category = "笔记"
        ),
        AiToolSpec(
            name = "update_note",
            description = "修改已有笔记的标题或内容（整段替换 content，不是追加）。",
            parameters = ToolSchema.obj(
                listOf("id", "content"),
                "id" to ToolSchema.str("笔记 id（短 id 即可）"),
                "title" to ToolSchema.str("新标题（可空=不改）"),
                "content" to ToolSchema.str("新的完整内容")
            ),
            mutating = true,
            category = "笔记"
        ),
        AiToolSpec(
            name = "create_outline_node",
            description = "在大纲里加一个节点，可绑定到某一章。用户让你把讨论的情节要点整理进大纲时调用。" +
                "要点写具体事件（谁做了什么、结果如何），不要写「推进剧情」。" +
                "章节标题和编号已经在【项目速览】里，直接调用即可，不要先查章节。",
            parameters = ToolSchema.obj(
                listOf("title", "content"),
                "title" to ToolSchema.str("节点标题"),
                "content" to ToolSchema.str("要点：这一段要写什么、冲突、结尾钩子"),
                "chapter" to ToolSchema.str("绑定到哪一章（标题/第N章，可空=全书级）"),
                "target_words" to ToolSchema.int("预计字数（可空）")
            ),
            mutating = true,
            category = "大纲"
        ),
        AiToolSpec(
            name = "update_outline_node",
            description = "改写已有大纲节点的标题或要点。",
            parameters = ToolSchema.obj(
                listOf("id", "content"),
                "id" to ToolSchema.str("大纲节点 id（短 id 即可）"),
                "title" to ToolSchema.str("新标题（可空=不改）"),
                "content" to ToolSchema.str("新的要点"),
                "chapter" to ToolSchema.str("改绑到哪一章（可空=不改）")
            ),
            mutating = true,
            category = "大纲"
        ),
        AiToolSpec(
            name = "append_chapter_text",
            description = "把一段正文追加到某一章末尾。用户要你把讨论/续写的内容落进正文时调用。" +
                "text 是纯正文，不要带标题、编号、解释。" +
                "知道是哪一章就直接调用，不要先读整章正文。",
            parameters = ToolSchema.obj(
                listOf("text"),
                "text" to ToolSchema.str("要追加的正文（可含换行分段）"),
                "chapter" to ToolSchema.str("追加到哪一章（标题/第N章，可空=当前章）")
            ),
            mutating = true,
            category = "正文"
        ),
        AiToolSpec(
            name = "set_chapter_summary",
            description = "设置某一章的剧情摘要（100-200 字，谁做了什么、结果如何、留下什么钩子）。摘要是后续章节 AI 续写的依据。",
            parameters = ToolSchema.obj(
                listOf("summary"),
                "summary" to ToolSchema.str("摘要正文"),
                "chapter" to ToolSchema.str("哪一章（标题/第N章，可空=当前章）")
            ),
            mutating = true,
            category = "正文"
        ),
        AiToolSpec(
            name = "add_foreshadowing",
            description = "登记一条伏笔（埋了什么、打算哪章回收）。用户提到要埋伏笔/回收伏笔时调用。",
            parameters = ToolSchema.obj(
                listOf("title"),
                "title" to ToolSchema.str("伏笔标题"),
                "detail" to ToolSchema.str("具体内容：埋的是什么、怎么回收"),
                "due_chapter" to ToolSchema.str("计划在第几章回收（可空）")
            ),
            mutating = true,
            category = "伏笔"
        )
    )

    suspend fun run(call: ToolCall): ToolOutcome {
        val args = runCatching { JSONObject(call.arguments.ifBlank { "{}" }) }.getOrElse {
            return ToolOutcome.Failure("参数不是合法 JSON：" + call.arguments.take(80))
        }
        if (workId == null) return ToolOutcome.Failure("还没有选作品，读不到项目数据")
        return try {
            when (call.name) {
                "list_chapters" -> listChapters()
                "read_chapter" -> readChapter(args.optString("chapter"))
                "search_notes" -> searchNotes(args.optString("query"), args.optString("kind"))
                "read_note" -> readNote(args.optString("id"))
                "list_outline" -> listOutline()
                "list_foreshadowing" -> listForeshadowing()
                else -> propose(call.name, args)
            }
        } catch (error: Exception) {
            ToolOutcome.Failure("执行失败：" + (error.message ?: error.javaClass.simpleName))
        }
    }

    // ---------- 读 ----------

    private suspend fun listChapters(): ToolOutcome {
        val list = chapters.chapters(workId!!)
        if (list.isEmpty()) return ToolOutcome.Data("（还没有章节）")
        val text = list.mapIndexed { index, chapter ->
            "第 " + (index + 1) + " 章 " + chapter.title + " · " + chapter.wordCount + " 字" +
                (if (chapter.id == currentChapterId) "（当前章）" else "") +
                (if (chapter.summary.isNullOrBlank()) "" else " · 有摘要")
        }.joinToString("\n")
        return ToolOutcome.Data(text)
    }

    private suspend fun readChapter(ref: String): ToolOutcome {
        val chapter = resolveChapter(ref) ?: return ToolOutcome.Failure("找不到章节：" + ref)
        val opened = chapters.openChapter(chapter.id)
            ?: return ToolOutcome.Failure("这一章打不开（文件缺失或损坏）：" + chapter.title)
        val text = opened.content.blocks.joinToString("\n") { it.text }
        val index = chapters.chapters(workId!!).indexOfFirst { it.id == chapter.id } + 1
        if (text.isBlank()) return ToolOutcome.Data("第 " + index + " 章「" + chapter.title + "」还没有正文。")
        val clipped = text.take(MAX_READ_CHARS)
        return ToolOutcome.Data(
            "第 " + index + " 章「" + chapter.title + "」（" + text.length + " 字）" +
                (if (text.length > MAX_READ_CHARS) "【已截断】\n" else "\n") + clipped
        )
    }

    private suspend fun searchNotes(query: String, kind: String): ToolOutcome {
        val needle = query.trim()
        if (needle.isBlank()) return ToolOutcome.Failure("query 不能为空")
        val all = notes.allOnce().filter { note ->
            (note.workId == null || note.workId == workId) &&
                (kind.isBlank() || note.kind == kind)
        }
        val hits = all.filter { note ->
            note.title.contains(needle, ignoreCase = true) ||
                note.aliases.contains(needle, ignoreCase = true) ||
                note.content.contains(needle, ignoreCase = true) ||
                note.tags.contains(needle, ignoreCase = true)
        }.take(12)
        if (hits.isEmpty()) return ToolOutcome.Data("没有匹配「" + needle + "」的笔记（共 " + all.size + " 条）")
        return ToolOutcome.Data(
            hits.joinToString("\n") { note ->
                shortId(note.id) + " | " + NoteKind.label(note.kind) + " | " + note.title + " | " +
                    note.content.replace('\n', ' ').take(100)
            }
        )
    }

    private suspend fun readNote(id: String): ToolOutcome {
        val note = resolveNote(id) ?: return ToolOutcome.Failure("找不到笔记：" + id)
        return ToolOutcome.Data(
            "[" + NoteKind.label(note.kind) + "] " + note.title +
                (if (note.aliases.isNotBlank()) "（别名：" + note.aliases + "）" else "") + "\n" +
                note.content.take(3000) + if (note.content.length > 3000) "\n【已截断】" else ""
        )
    }

    private suspend fun listOutline(): ToolOutcome {
        val nodes = outline.list(workId!!)
        if (nodes.isEmpty()) return ToolOutcome.Data("（还没有大纲节点）")
        val chapterList = chapters.chapters(workId)
        val order = chapterList.withIndex().associate { it.value.id to it.index + 1 }
        return ToolOutcome.Data(
            nodes.take(MAX_LIST).joinToString("\n") { node ->
                val chapterLabel = node.chapterId?.let { id ->
                    order[id]?.let { "第 " + it + " 章 · " } ?: ""
                } ?: ""
                shortId(node.id) + " | " + chapterLabel + node.title +
                    (if (node.content.isNotBlank()) "：" + node.content.take(120) else "") +
                    (node.targetWords?.takeIf { it > 0 }?.let { "（目标 " + it + " 字）" } ?: "")
            }
        )
    }

    private suspend fun listForeshadowing(): ToolOutcome {
        val items = foreshadowing.listByWork(workId!!)
        if (items.isEmpty()) return ToolOutcome.Data("（还没有伏笔）")
        return ToolOutcome.Data(
            items.take(MAX_LIST).joinToString("\n") { item ->
                "· " + item.title + if (item.detail.isNotBlank()) "：" + item.detail.take(80) else "" +
                    "（" + statusLabel(item.status) + "）"
            }
        )
    }

    // ---------- 写：只出提案 ----------

    private fun propose(name: String, args: JSONObject): ToolOutcome {
        val label: String
        val title: String
        val target: String
        val preview: String
        val map = linkedMapOf<String, String>()
        when (name) {
            "create_note" -> {
                val kind = args.optString("kind").ifBlank { NoteKind.IDEA }
                label = "新建笔记"
                target = "笔记"
                title = NoteKind.label(kind) + " · " + args.optString("title").ifBlank { "未命名" }
                preview = args.optString("content").take(400)
                map["kind"] = kind
                map["title"] = args.optString("title")
                map["content"] = args.optString("content")
                map["aliases"] = args.optString("aliases")
            }
            "update_note" -> {
                label = "修改笔记"
                target = "笔记"
                map["id"] = args.optString("id")
                map["title"] = args.optString("title")
                map["content"] = args.optString("content")
                title = "笔记 " + shortId(args.optString("id"))
                preview = "新内容：\n" + args.optString("content").take(360)
            }
            "create_outline_node" -> {
                label = "加大纲节点"
                target = "大纲"
                map["title"] = args.optString("title")
                map["content"] = args.optString("content")
                map["chapter"] = args.optString("chapter")
                map["target_words"] = args.optInt("target_words", 0).toString()
                title = args.optString("title").ifBlank { "新节点" } +
                    (if (args.optString("chapter").isNotBlank()) "（" + args.optString("chapter") + "）" else "")
                preview = args.optString("content").take(400)
            }
            "update_outline_node" -> {
                label = "改大纲节点"
                target = "大纲"
                map["id"] = args.optString("id")
                map["title"] = args.optString("title")
                map["content"] = args.optString("content")
                map["chapter"] = args.optString("chapter")
                title = "节点 " + shortId(args.optString("id"))
                preview = "新要点：\n" + args.optString("content").take(360)
            }
            "append_chapter_text" -> {
                label = "写入正文"
                target = "正文"
                map["text"] = args.optString("text")
                map["chapter"] = args.optString("chapter")
                title = "追加到「" + args.optString("chapter").ifBlank { "当前章" } + "」"
                preview = args.optString("text").take(400)
            }
            "set_chapter_summary" -> {
                label = "写本章摘要"
                target = "正文"
                map["summary"] = args.optString("summary")
                map["chapter"] = args.optString("chapter")
                title = "「" + args.optString("chapter").ifBlank { "当前章" } + "」的摘要"
                preview = args.optString("summary").take(400)
            }
            "add_foreshadowing" -> {
                label = "登记伏笔"
                target = "伏笔"
                map["title"] = args.optString("title")
                map["detail"] = args.optString("detail")
                map["due_chapter"] = args.optString("due_chapter")
                title = args.optString("title").ifBlank { "未命名伏笔" }
                preview = args.optString("detail").take(400)
            }
            else -> return ToolOutcome.Failure("没有这个工具：" + name)
        }
        if (preview.isBlank() && name != "add_foreshadowing") {
            return ToolOutcome.Failure("内容为空，没有生成提案")
        }
        val proposal = ToolProposal(
            id = UUID.randomUUID().toString(),
            toolName = name,
            label = label,
            title = title,
            preview = preview,
            target = target,
            args = map
        )
        return ToolOutcome.Proposal(proposal)
    }

    // ---------- 应用提案 ----------

    suspend fun apply(proposal: ToolProposal): String {
        val args = proposal.args
        return when (proposal.toolName) {
            "create_note" -> {
                val kind = args["kind"].orEmpty().ifBlank { NoteKind.IDEA }
                val now = System.currentTimeMillis()
                val noteTitle = args["title"].orEmpty().trim().ifBlank { "未命名" }
                notes.upsert(
                    NoteEntity(
                        id = UUID.randomUUID().toString(),
                        workId = workId,
                        kind = kind,
                        title = noteTitle,
                        aliases = args["aliases"].orEmpty().trim(),
                        content = args["content"].orEmpty(),
                        aiReference = true,
                        createdAtMs = now,
                        updatedAtMs = now
                    )
                )
                "已在笔记里新建《" + noteTitle + "》"
            }
            "update_note" -> {
                val note = resolveNote(args["id"].orEmpty())
                    ?: throw IllegalStateException("找不到这条笔记")
                // 模型常把「不改」写成空串；照写会把整条笔记清空
                if (args["content"].isNullOrBlank()) {
                    throw IllegalStateException("新内容为空，已取消（笔记没有被清空）")
                }
                val newTitle = args["title"].orEmpty().trim()
                notes.upsert(
                    note.copy(
                        title = newTitle.ifBlank { note.title },
                        content = args["content"].orEmpty(),
                        updatedAtMs = System.currentTimeMillis()
                    )
                )
                "已更新笔记《" + (newTitle.ifBlank { note.title }) + "》"
            }
            "create_outline_node" -> {
                val chapter = resolveChapter(args["chapter"].orEmpty())
                val node = outline.create(
                    workId = workId!!,
                    chapterId = chapter?.id,
                    title = args["title"].orEmpty(),
                    content = args["content"].orEmpty(),
                    targetWords = args["target_words"]?.toIntOrNull()?.takeIf { it > 0 }
                )
                "已加大纲节点「" + node.title + "」" + (chapter?.let { "（" + it.title + "）" } ?: "")
            }
            "update_outline_node" -> {
                val node = resolveOutlineNode(args["id"].orEmpty())
                    ?: throw IllegalStateException("找不到这个大纲节点")
                if (args["content"].isNullOrBlank()) {
                    throw IllegalStateException("新要点为空，已取消（大纲节点没有被清空）")
                }
                val chapter = if (args["chapter"].isNullOrBlank()) {
                    node.chapterId
                } else {
                    resolveChapter(args["chapter"]!!)?.id
                }
                outline.update(
                    id = node.id,
                    title = args["title"].orEmpty().ifBlank { node.title },
                    content = args["content"].orEmpty(),
                    chapterId = chapter,
                    targetWords = node.targetWords,
                    beatRole = node.beatRole
                )
                "已改大纲节点「" + args["title"].orEmpty().ifBlank { node.title } + "」"
            }
            "append_chapter_text" -> {
                val chapter = resolveChapter(args["chapter"].orEmpty())
                    ?: throw IllegalStateException("找不到章节")
                val opened = chapters.openChapter(chapter.id)
                    ?: throw IllegalStateException("这一章打不开（文件缺失或损坏）")
                val paragraphs = args["text"].orEmpty()
                    .split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (paragraphs.isEmpty()) throw IllegalStateException("没有可写入的文字")
                val existing = opened.content.blocks
                val base = if (existing.size == 1 && existing.first().text.isBlank()) {
                    emptyList()
                } else {
                    existing
                }
                val blocks = base + paragraphs.map { Block(UUID.randomUUID().toString(), it) }
                when (chapters.saveChapter(
                    chapter.id,
                    blocks,
                    opened.chapter.revision,
                    UpdateSource.AiWrite
                )) {
                    is SaveResult.Saved ->
                        "已写入《" + chapter.title + "》末尾（" + paragraphs.sumOf { it.length } + " 字）"
                    is SaveResult.Conflict ->
                        throw IllegalStateException("这一章刚被改过（正在编辑？），回到编辑器再试")
                    SaveResult.NotFound -> throw IllegalStateException("章节不存在")
                }
            }
            "set_chapter_summary" -> {
                val chapter = resolveChapter(args["chapter"].orEmpty())
                    ?: throw IllegalStateException("找不到章节")
                chapters.updateSummary(chapter.id, args["summary"].orEmpty())
                "已写入《" + chapter.title + "》的摘要"
            }
            "add_foreshadowing" -> {
                val due = resolveChapter(args["due_chapter"].orEmpty())
                val item = foreshadowing.create(
                    workId = workId!!,
                    title = args["title"].orEmpty(),
                    detail = args["detail"].orEmpty(),
                    plantedChapterId = resolveChapter("")?.id,
                    dueChapterId = due?.id
                )
                "已登记伏笔「" + item.title + "」"
            }
            else -> throw IllegalStateException("未知操作：" + proposal.toolName)
        }
    }

    // ---------- 解析 ----------

    private suspend fun resolveChapter(ref: String): ChapterEntity? {
        val id = workId ?: return null
        val list = chapters.chapters(id)
        if (list.isEmpty()) return null
        val key = ref.trim()
        if (key.isBlank()) {
            // 没指定章节：优先当前章；在对话 tab 里没有「当前章」，就落到最后一章。
            return list.firstOrNull { it.id == currentChapterId } ?: list.last()
        }
        list.firstOrNull { it.id == key }?.let { return it }
        if (key.length >= 6) list.firstOrNull { it.id.startsWith(key) }?.let { return it }
        val number = key.takeWhile { it.isDigit() }.toIntOrNull()
            ?: key.dropWhile { !it.isDigit() }.takeWhile { it.isDigit() }.toIntOrNull()
        if (number != null) {
            list.getOrNull(number - 1)?.let { return it }
        }
        list.firstOrNull { it.title.contains(key, ignoreCase = true) }?.let { return it }
        return null
    }

    private suspend fun resolveNote(ref: String): NoteEntity? {
        val key = ref.trim()
        if (key.isBlank()) return null
        val all = notes.allOnce().filter { it.workId == null || it.workId == workId }
        all.firstOrNull { it.id == key }?.let { return it }
        if (key.length >= 4) all.firstOrNull { it.id.startsWith(key) }?.let { return it }
        return all.firstOrNull { it.title == key }
    }

    private suspend fun resolveOutlineNode(ref: String): OutlineNodeEntity? {
        val key = ref.trim()
        if (key.isBlank() || workId == null) return null
        val nodes = outline.list(workId)
        nodes.firstOrNull { it.id == key }?.let { return it }
        if (key.length >= 4) nodes.firstOrNull { it.id.startsWith(key) }?.let { return it }
        return nodes.firstOrNull { it.title == key }
    }

    private fun shortId(id: String): String = id.take(8)

    private fun statusLabel(status: String): String = when (status) {
        "paid" -> "已回收"
        "reinforced" -> "强化中"
        "abandoned" -> "弃用"
        else -> "待回收"
    }

    private companion object {
        const val MAX_READ_CHARS = 3000
        const val MAX_LIST = 60
    }
}
