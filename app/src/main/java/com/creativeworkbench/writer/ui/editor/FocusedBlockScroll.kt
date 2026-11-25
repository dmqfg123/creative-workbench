package com.creativeworkbench.writer.ui.editor

/**
 * 聚焦块的最小滚动策略。思路来自 cascade-editor 的 focusedBlockScrollTarget：
 * 已可见就不动；部分被裁就只滚被裁的像素；未组合的块才 scrollToItem。
 * 这样按回车新段落在键盘上方时不会把整篇文档跳到顶部。
 */
data class VisibleBlockBounds(
    val index: Int,
    val offset: Int,
    val size: Int
)

sealed interface FocusScrollTarget {
    data class By(val deltaPx: Int) : FocusScrollTarget
    data class ToItem(val index: Int, val scrollOffset: Int) : FocusScrollTarget
}

fun focusedBlockScrollTarget(
    focusedIndex: Int,
    visibleItems: List<VisibleBlockBounds>,
    viewportStartOffset: Int,
    viewportEndOffset: Int,
    bottomMarginPx: Int
): FocusScrollTarget? {
    if (focusedIndex < 0 || viewportEndOffset <= viewportStartOffset) return null
    val effectiveEnd = (viewportEndOffset - bottomMarginPx.coerceAtLeast(0))
        .coerceAtLeast(viewportStartOffset)
    val focused = visibleItems.firstOrNull { it.index == focusedIndex }
    if (focused != null) {
        val start = focused.offset
        val end = focused.offset + focused.size
        val available = (effectiveEnd - viewportStartOffset).coerceAtLeast(1)
        val hiddenBelow = (end - effectiveEnd).coerceAtLeast(0)
        // 规则：没被挡住就不动；被挡掉一大截（或整段比视口还高）就把段首对齐到视口顶部，
        // 保证「正在写的这一段从第一行起就看得见」。只挡一点点才做最小滚动。
        // 之前一律「把段尾推到键盘上沿」，遇到长段落会把段首顶出屏幕——空有对齐，看不见自己在写哪。
        return when {
            start < viewportStartOffset -> FocusScrollTarget.By(start - viewportStartOffset)
            hiddenBelow == 0 -> null
            focused.size > available -> FocusScrollTarget.By(start - viewportStartOffset)
            hiddenBelow > available / 6 -> FocusScrollTarget.By(start - viewportStartOffset)
            else -> FocusScrollTarget.By(hiddenBelow)
        }
    }
    val firstVisible = visibleItems.minOfOrNull { it.index }
        ?: return FocusScrollTarget.ToItem(focusedIndex, 0)
    val lastVisible = visibleItems.maxOf { it.index }
    if (focusedIndex < firstVisible) {
        return FocusScrollTarget.ToItem(focusedIndex, 0)
    }
    if (focusedIndex > lastVisible) {
        val estimated = visibleItems
            .filter { it.index < focusedIndex }
            .maxByOrNull { it.index }
            ?.size
            ?: visibleItems.maxByOrNull { it.index }?.size
            ?: 0
        val desiredTop = effectiveEnd - estimated.coerceAtLeast(0)
        val scrollOffset = (desiredTop - viewportStartOffset).coerceAtLeast(0)
        return FocusScrollTarget.ToItem(focusedIndex, scrollOffset)
    }
    return null
}
