package com.creativeworkbench.writer.core.editor

import com.creativeworkbench.writer.core.data.Block
import java.util.UUID

sealed interface EditCommand {
    data class Move(val blockId: String, val toIndex: Int) : EditCommand
    data class Remove(val blockId: String) : EditCommand
    data class Split(val blockId: String, val codePointOffset: Int, val newBlockId: String = UUID.randomUUID().toString()) : EditCommand
    data class Merge(val upperBlockId: String, val lowerBlockId: String) : EditCommand
    data class Insert(val index: Int, val block: Block) : EditCommand
}

data class UndoEntry(val before: List<Block>, val after: List<Block>)

object EditEngine {
    fun apply(blocks: List<Block>, command: EditCommand): UndoEntry? {
        val before = blocks.toList()
        val after = when (command) {
            is EditCommand.Move -> move(before, command.blockId, command.toIndex)
            is EditCommand.Remove -> remove(before, command.blockId)
            is EditCommand.Split -> split(before, command.blockId, command.codePointOffset, command.newBlockId)
            is EditCommand.Merge -> merge(before, command.upperBlockId, command.lowerBlockId)
            is EditCommand.Insert -> insert(before, command.index, command.block)
        } ?: return null
        if (after == before) return null
        return UndoEntry(before, after)
    }

    private fun move(blocks: List<Block>, blockId: String, toIndex: Int): List<Block>? {
        val from = blocks.indexOfFirst { it.id == blockId }
        if (from < 0) return null
        val mutable = blocks.toMutableList()
        val item = mutable.removeAt(from)
        val target = toIndex.coerceIn(0, mutable.size)
        mutable.add(target, item)
        return mutable
    }

    private fun remove(blocks: List<Block>, blockId: String): List<Block>? {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return null
        val mutable = blocks.toMutableList()
        mutable.removeAt(index)
        if (mutable.isEmpty()) mutable.add(Block(UUID.randomUUID().toString(), ""))
        return mutable
    }

    private fun split(blocks: List<Block>, blockId: String, codePointOffset: Int, newBlockId: String): List<Block>? {
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return null
        val text = blocks[index].text
        val total = text.codePointCount(0, text.length)
        val offset = codePointOffset.coerceIn(0, total)
        val charOffset = charIndexForCodePoint(text, offset)
        val head = text.substring(0, charOffset)
        val tail = text.substring(charOffset)
        val mutable = blocks.toMutableList()
        mutable[index] = blocks[index].copy(text = head)
        mutable.add(index + 1, Block(newBlockId, tail))
        return mutable
    }

    private fun merge(blocks: List<Block>, upperBlockId: String, lowerBlockId: String): List<Block>? {
        val upper = blocks.indexOfFirst { it.id == upperBlockId }
        val lower = blocks.indexOfFirst { it.id == lowerBlockId }
        if (upper < 0 || lower < 0 || upper == lower) return null
        val first = minOf(upper, lower)
        val second = maxOf(upper, lower)
        val merged = blocks[first].text + blocks[second].text
        val mutable = blocks.toMutableList()
        mutable[first] = blocks[first].copy(text = merged)
        mutable.removeAt(second)
        return mutable
    }

    private fun insert(blocks: List<Block>, index: Int, block: Block): List<Block> {
        val mutable = blocks.toMutableList()
        mutable.add(index.coerceIn(0, mutable.size), block)
        return mutable
    }

    fun charIndexForCodePoint(text: String, codePointOffset: Int): Int {
        var index = 0
        var remaining = codePointOffset
        while (index < text.length && remaining > 0) {
            index += Character.charCount(text.codePointAt(index))
            remaining--
        }
        return index
    }
}
