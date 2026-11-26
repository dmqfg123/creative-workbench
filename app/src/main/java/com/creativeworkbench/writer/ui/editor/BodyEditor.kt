package com.creativeworkbench.writer.ui.editor

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.view.MotionEvent
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 正文编辑面：**一整章一个 EditText、一个 Editable**。
 *
 * 为什么这么改（0.17.0）——不是审美，是 AOSP 的硬约束：
 * `TextView.setText(CharSequence, EDITABLE)` 会新建一个 Editable、清空撤销栈、
 * 并在下一帧 `scheduleRestartInputForSetText()` 重启输入法，而 IME 手里的 InputConnection
 * 还指着旧的那个 Editable。所以「每段一个 EditText + 回写 setText」这条路**必然**吞字/跳光标。
 * （出处见 参考/编辑器实现调研_20261126.md；Notally 用 setEditableFactory 规避，正是这里的做法。）
 *
 * 本实现的四条规矩：
 * 1. **永不 setText**：外部改文本（AI 写入/撤销/段落操作/换章）一律在同一个 Editable 上
 *    `replace(0, length, newText)`；
 * 2. **EditableFactory 恒定返回同一个 buffer**：任何内部 setText 都复用这块内存，身份不变；
 * 3. **不设内滚**：EditText 自己滚（原生一条路径），外面不再套 LazyColumn；
 * 4. **不拦回车**：单缓冲区里换行就是换行，段落是「按 \n 派生的数据」，不再有「拆块」这个动作。
 */
class BodyEditorController {
    internal var editText: BodyEditText? = null
    internal var buffer: SpannableStringBuilder? = null
    /** 编辑器销毁后界面还要存一次位置：销毁前把最后的值留在这里。 */
    internal var lastCaret: Int = 0
    internal var lastScrollRatio: Float = 0f
    /** 视口顶部那一行的首字符偏移（主锚点，见 EditorPositionStore 的注释）。 */
    internal var lastTopChar: Int = 0

    val isAttached: Boolean get() = editText != null

    fun caret(): Int = editText?.selectionStart ?: 0

    /** 外部改文本：原地 replace，保住输入法连接与撤销栈。 */
    fun setTextExternally(text: String, caret: Int? = null) {
        val view = editText ?: return
        val editable = buffer ?: return
        if (editable.toString() == text) {
            caret?.let { view.setSelection(it.coerceIn(0, editable.length)) }
            return
        }
        val target = (caret ?: view.selectionStart).coerceIn(0, text.length)
        view.suppress.set(true)
        editable.replace(0, editable.length, text)
        view.setSelection(target.coerceIn(0, editable.length))
        view.suppress.set(false)
    }

    fun moveCaret(offset: Int) {
        val view = editText ?: return
        val target = offset.coerceIn(0, view.text?.length ?: 0)
        view.setSelection(target)
        scrollCaretIntoView()
    }

    /** 只放光标，不动滚动（恢复位置时用：滚动要按上次的比例走，不能被光标抢）。 */
    fun setCaret(offset: Int) {
        val view = editText ?: return
        view.setSelection(offset.coerceIn(0, view.text?.length ?: 0))
    }

    fun focusAndShowKeyboard() {
        val view = editText ?: return
        if (!view.isFocused) view.requestFocus()
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard() {
        val view = editText ?: return
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    /** 把光标所在行滚进可视区：只做一次 requestRectangleOnScreen，不监听滚动。 */
    fun scrollCaretIntoView() {
        val view = editText ?: return
        val layout = view.layout ?: return
        val offset = view.selectionStart.coerceAtLeast(0)
        val line = layout.getLineForOffset(offset)
        view.requestRectangleOnScreen(
            android.graphics.Rect(0, layout.getLineTop(line), view.width, layout.getLineBottom(line)),
            false
        )
    }

    fun selection(): Pair<Int, Int> {
        val view = editText ?: return 0 to 0
        return view.selectionStart to view.selectionEnd
    }

    fun scrollY(): Int = editText?.scrollY ?: 0

    fun scrollTo(y: Int) {
        editText?.post { editText?.scrollTo(0, y) }
    }

    /** 可滚动的总距离（内容高 − 视口高）。布局没完成时是 0。 */
    fun scrollMax(): Int {
        val view = editText ?: return 0
        val content = view.layout?.height ?: return 0
        return (content - view.height).coerceAtLeast(0)
    }

    /** 当前滚动比例（0–1），存盘用。 */
    fun scrollRatio(): Float {
        val max = scrollMax()
        return if (max <= 0) 0f else (scrollY().toFloat() / max).coerceIn(0f, 1f)
    }

    /** 位置变化回调（滚动/移光标时触发，内部按 300ms 节流）：界面用它持续落盘。 */
    var onPositionChanged: ((caret: Int, ratio: Float, topChar: Int) -> Unit)? = null
    private var lastNotifyAt = 0L

    /**
     * 只在「有滚动/移光标」时刷新缓存并落盘。
     * 真机日志证明：离开页面那一刻（ON_PAUSE / onDispose）EditText 的 scrollY 已经是 0，
     * 在这里重算会把刚存好的 0.140 覆盖成 0.000——这正是「每次都回到开头」的真正原因。
     * 所以缓存只由滚动事件刷新，保存只读缓存。
     */
    internal fun notifyPosition(force: Boolean = false) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - lastNotifyAt < 300) return
        lastNotifyAt = now
        editText?.let { view ->
            lastCaret = view.selectionStart.coerceAtLeast(0)
            lastScrollRatio = scrollRatio()
            lastTopChar = topCharOffset()
        }
        onPositionChanged?.invoke(lastCaret, lastScrollRatio, lastTopChar)
    }

    /** 存盘用：永远是「最后一次滚动/移光标」的快照，绝不在离开页面时重算。 */
    fun caretForSave(): Int = lastCaret

    fun ratioForSave(): Float = lastScrollRatio

    fun topCharForSave(): Int = lastTopChar

    /** 视口顶部那一行的首字符偏移。布局没完成时退回上次的值。 */
    fun topCharOffset(): Int {
        val view = editText ?: return lastTopChar
        val layout = view.layout ?: return lastTopChar
        val line = layout.getLineForVertical(view.scrollY)
        return layout.getLineStart(line).coerceAtLeast(0).also { lastTopChar = it }
    }

    /**
     * 按「字符偏移」恢复（阅读器的做法：定位到那句，而不是某个像素）。
     * 布局没出来就隔一帧重试；最终还不行才退回按比例。
     */
    fun restoreToChar(offset: Int, ratioFallback: Float, attempts: Int = 8) {
        val view = editText ?: return
        val layout = view.layout
        val length = view.text?.length ?: 0
        if (layout == null || length == 0 || scrollMax() <= 0) {
            if (attempts > 0) {
                view.postDelayed({ restoreToChar(offset, ratioFallback, attempts - 1) }, 50)
            } else {
                restoreScroll(ratioFallback)
            }
            return
        }
        val safe = offset.coerceIn(0, length)
        val line = layout.getLineForOffset(safe)
        val target = layout.getLineTop(line).coerceIn(0, scrollMax())
        view.scrollTo(0, target)
        android.util.Log.i("WB_POS", "restore-char offset=" + safe + " line=" + line +
            " target=" + target + " scrollY=" + view.scrollY)
        view.postDelayed({
            // 同样的道理：只在被布局重置成 0 时补一次，不抢用户滚动
            if (view.scrollY == 0 && target > 0 && scrollMax() > 0) view.scrollTo(0, target)
        }, 160)
    }

    /**
     * 按比例恢复滚动。**必须等布局算完**：
     * EditText 的 StaticLayout 是异步的，刚挂载时 layout 为空、内容高还没出来，
     * 这时 scrollTo 会被系统裁成 0——这正是「重新打开总是回到开头」的直接原因。
     * 所以这里带重试，布局没就绪就隔一帧再来。
     */
    fun restoreScroll(ratio: Float, attempts: Int = 6) {
        android.util.Log.i(
            "WB_POS",
            "restore ratio=" + String.format(java.util.Locale.US, "%.3f", ratio) +
                " max=" + scrollMax() + " attempts=" + attempts
        )
        val view = editText ?: return
        val safeRatio = ratio.coerceIn(0f, 1f)
        val max = scrollMax()
        if (max <= 0) {
            if (attempts > 0) {
                view.postDelayed({ restoreScroll(safeRatio, attempts - 1) }, 50)
            }
            return
        }
        view.scrollTo(0, (safeRatio * max).toInt())
        android.util.Log.i("WB_POS", "applied max=" + max + " scrollY=" + view.scrollY)
        // 布局偶尔会二次排版把位置重置：只在「确实被重置」时补一次，
        // 不要持续重试——那会和用户刚做的滑动抢滚动。
        view.postDelayed({
            // 只有「被布局重置成 0」才补一次：用户自己滑走的位置绝不抢回来
            val now = scrollMax()
            if (now > 0 && view.scrollY == 0) {
                val target = (safeRatio * now).toInt()
                if (target > 0) view.scrollTo(0, target)
            }
        }, 160)
    }
}

@Composable
fun BodyEditor(
    controller: BodyEditorController,
    /** 全文（段落之间是 \n）。只有外部变化时才会原地 replace 到 buffer。 */
    text: String,
    textSizeSp: Float,
    lineHeightSp: Float,
    onTextChange: (text: String) -> Unit,
    onSelectionChange: (offset: Int) -> Unit,
    onAiRewriteSelection: (start: Int, end: Int, selected: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val onText = remember { arrayOfNulls<(String) -> Unit>(1) }
    val onSel = remember { arrayOfNulls<(Int) -> Unit>(1) }
    val onRewrite = remember { arrayOfNulls<(Int, Int, String) -> Unit>(1) }
    onText[0] = onTextChange
    onSel[0] = onSelectionChange
    onRewrite[0] = onAiRewriteSelection

    val buffer = remember { SpannableStringBuilder(text) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    // 行距增量：只用系统 setLineSpacing（add 是固定值，不会随测量次数累积）。
    // 之前自造的 LineHeightSpan 在每次 chooseHeight 里又减一次 gap，行高越测越大，
    // 结果就是「光标 3–4 行高、每行之间空一大截」。
    val lineExtraPx = with(density) { 5.dp.toPx() }

    val editText = remember {
        BodyEditText(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            typeface = Typeface.SERIF
            setLineSpacing(lineExtraPx, lineHeightSp / textSizeSp)
            gravity = Gravity.TOP or Gravity.START
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setTextColor(textColor)
            // 恒定返回同一块 buffer：任何内部 setText 都不换 Editable 身份
            setEditableFactory(object : Editable.Factory() {
                override fun newEditable(source: CharSequence?): Editable = buffer
            })
            setText(buffer, android.widget.TextView.BufferType.EDITABLE)
            setSelection(0)
            setOnClickListener { showSoftKeyboard(this) }
            customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
                override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                    menu?.add(0, MENU_AI_REWRITE, 0, "AI 改写")
                        ?.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    // 不收起键盘：选中之后很可能紧接着就要输入（改字/替换），
                    // 收键盘是我之前擅自加的推断，用户没有要求这个行为。
                    return true
                }

                override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?) = false

                override fun onActionItemClicked(
                    mode: android.view.ActionMode?,
                    item: android.view.MenuItem?
                ): Boolean {
                    if (item?.itemId != MENU_AI_REWRITE) return false
                    val start = selectionStart
                    val end = selectionEnd
                    if (start in 0 until end) {
                        onRewrite[0]?.invoke(start, end, text.substring(start, end))
                    }
                    mode?.finish()
                    return true
                }

                override fun onDestroyActionMode(mode: android.view.ActionMode?) = Unit
            }
        }
    }

    DisposableEffect(editText) {
        controller.editText = editText
        controller.buffer = buffer
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (editText.suppress.get()) return
                onText[0]?.invoke(s?.toString().orEmpty())
            }
        }
        editText.addTextChangedListener(watcher)
        editText.onScrollChanged = { controller.notifyPosition() }
        onDispose {
            editText.removeTextChangedListener(watcher)
            editText.onScrollChanged = null
            controller.editText = null
            controller.buffer = null
        }
    }

    // 外部文本变化：原地 replace，绝不 setText
    LaunchedEffect(text) {
        val editable = controller.buffer ?: return@LaunchedEffect
        if (editable.toString() == text) return@LaunchedEffect
        val caret = editText.selectionStart.coerceAtLeast(0)
        editText.suppress.set(true)
        editable.replace(0, editable.length, text)
        editText.setSelection(caret.coerceIn(0, editable.length))
        editText.suppress.set(false)
    }

    LaunchedEffect(textSizeSp, lineHeightSp, textColor, lineExtraPx) {
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        editText.setLineSpacing(lineExtraPx, lineHeightSp / textSizeSp)
        editText.setTextColor(textColor)
    }

    editText.setOnSelectionChangedListener { offset ->
        controller.notifyPosition()
        onSel[0]?.invoke(offset)
    }

    AndroidView(
        factory = { editText },
        modifier = modifier,
        update = { view -> view.setTextColor(textColor) }
    )
}

private const val MENU_AI_REWRITE = 1001

private fun showSoftKeyboard(view: View) {
    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
    if (!view.isFocused) view.requestFocus()
    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
}

/**
 * 程序改文本时抬起来，别让 TextWatcher 当成用户输入回写一遍。
 *
 * 另外补上 EditText **本来就没有**的滑动惯性：
 * AOSP 的 TextView/EditText 拖动靠 MovementMethod 在 MOVE 里 scrollTo，抬手就停，没有 fling
 * （ScrollView/RecyclerView 才有 OverScroller 惯性）。所以正文滑动一直是「划一下停一下」。
 * 这里照 ScrollView.onTouchEvent 的标准做法补一段：抬手时用 VelocityTracker 取速度，
 * 交给 OverScroller 按平台默认摩擦（ViewConfiguration.getScrollFriction，0.015）滚到停——
 * 手感和系统列表一致，不自己发明曲线。
 */
class BodyEditText(context: Context) : EditText(context) {
    val suppress = java.util.concurrent.atomic.AtomicBoolean(false)
    private var selectionListener: ((Int) -> Unit)? = null
    private var lastSelection = -1

    /** 平台默认惯性（ScrollView / RecyclerView 同一套参数）。 */
    private val flingScroller = android.widget.OverScroller(context)
    private val velocityTracker = android.view.VelocityTracker.obtain()
    private val viewConfig = android.view.ViewConfiguration.get(context)
    private val touchSlop = viewConfig.scaledTouchSlop
    private val minFlingVelocity = viewConfig.scaledMinimumFlingVelocity
    private val maxFlingVelocity = viewConfig.scaledMaximumFlingVelocity
    private var gestureDownY = 0f
    private var gestureDragged = false

    /**
     * 选中文字后「另一根手指上下滑窗口」。
     *
     * 系统行为：TextView 只用一根活动指针做选择拖动，第二根手指的 MOVE 会被丢掉，
     * 于是长按选中后只能拖手柄，不能一边选一边滚——用户反馈的就是这个。
     * 做法：选择非空时记下第二根指针，把它的 y 位移直接转成 scrollBy；
     * 选择锚点按字符偏移保存，滚动不会破坏选区（手柄会跟着文字走）。
     * 参见 sora-editor 的 EditorTouchEventHandler：滚动手势与选择手势分状态处理，
     * 选择拖动时用 scrollIfThumbReachesEdge 补滚动。
     */
    private var selectionPanPointerId = -1
    private var selectionPanLastY = 0f

    /**
     * 选区激活时「拖正文 = 滚屏」。
     *
     * 为什么双指那条路上不去：长按后按住的是**选择手柄（独立的 HandleView）**，
     * 第一根手指的事件根本不进 EditText；第二根手指落到正文时，对 EditText 来说是一次
     * 全新的 ACTION_DOWN（不是 POINTER_DOWN），所以「双指」分支永远等不到。
     * 结论：选区激活时，正文上任何一个拖动都应当解释成滚动，而不是挪光标/取消选区。
     */
    private var selectionPanConsumed = false
    private var selectionPanStartY = 0f
    /** 按下前就存在的选区：TextView 会在 DOWN 时清掉它，这里记住再恢复。 */
    private var keepSelectionStart = -1
    private var keepSelectionEnd = -1

    private fun hasTextSelection(): Boolean = selectionStart != selectionEnd

    private val flingRunnable = object : Runnable {
        override fun run() {
            if (flingScroller.computeScrollOffset()) {
                scrollTo(0, flingScroller.currY)
                postOnAnimation(this)
            }
        }
    }

    private fun scrollLimit(): Int {
        val content = layout?.height ?: return 0
        return (content - height).coerceAtLeast(0)
    }

    private fun startFling(velocityY: Int) {
        val limit = scrollLimit()
        if (limit <= 0) return
        flingScroller.fling(
            0, scrollY,
            0, velocityY,
            0, 0,
            0, limit
        )
        postOnAnimation(flingRunnable)
    }

    private fun stopFling() {
        if (!flingScroller.isFinished) flingScroller.forceFinished(true)
        removeCallbacks(flingRunnable)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 手指按下立即停住惯性（和所有系统可滚控件一致）
                stopFling()
                velocityTracker.clear()
                velocityTracker.addMovement(event)
                gestureDownY = event.y
                gestureDragged = false
                selectionPanPointerId = -1
                selectionPanConsumed = false
                selectionPanStartY = event.y
                keepSelectionStart = if (hasTextSelection()) selectionStart else -1
                keepSelectionEnd = selectionEnd
                val handled = super.onTouchEvent(event)
                // TextView 在 ACTION_DOWN 就把选区清了；先恢复，等抬手时再决定是不是真的结束。
                // 不恢复的话，第二根手指刚落下选区就没了——「另一根手指滚屏」永远无从谈起。
                if (keepSelectionStart >= 0 && !hasTextSelection()) {
                    setSelection(keepSelectionStart, keepSelectionEnd)
                }
                return handled
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // 已经有选区时，第二根手指 = 滚动窗口（不参与选择）
                if (hasTextSelection()) {
                    val index = event.actionIndex
                    selectionPanPointerId = event.getPointerId(index)
                    selectionPanLastY = event.getY(index)
                    stopFling()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                // 双指同时落在正文上时，按第二根指针滚
                if (selectionPanPointerId >= 0) {
                    val index = event.findPointerIndex(selectionPanPointerId)
                    if (index >= 0) {
                        val y = event.getY(index)
                        val dy = (selectionPanLastY - y).toInt()
                        if (dy != 0) {
                            scrollBy(0, dy)
                            selectionPanLastY = y
                        }
                    }
                }
                // 选区激活 + 拖动正文 = 滚屏（不挪光标、不取消选区，交给上面那支手指继续按住手柄）
                if (!selectionPanConsumed && (keepSelectionStart >= 0 || hasTextSelection()) &&
                    kotlin.math.abs(event.y - selectionPanStartY) > touchSlop
                ) {
                    selectionPanConsumed = true
                    selectionPanLastY = event.y
                }
                if (selectionPanConsumed) {
                    val dy = (selectionPanLastY - event.y).toInt()
                    if (dy != 0) {
                        scrollBy(0, dy)
                        selectionPanLastY = event.y
                    }
                    // 不再交给 TextView：它会把这次移动解释成挪光标/取消选区
                    return true
                }
                velocityTracker.addMovement(event)
                if (kotlin.math.abs(event.y - gestureDownY) > touchSlop) gestureDragged = true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == selectionPanPointerId) {
                    selectionPanPointerId = -1
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (selectionPanConsumed) {
                    selectionPanConsumed = false
                    velocityTracker.clear()
                    gestureDragged = false
                    return true
                }
                // 选择状态下的拖动是在调选区，松手不该甩动页面
                if (gestureDragged && !hasTextSelection()) {
                    velocityTracker.addMovement(event)
                    velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                    // 手指往上滑（y 变小）→ 内容往下滚（scrollY 变大），所以要取反
                    val velocity = -velocityTracker.yVelocity.toInt()
                    if (kotlin.math.abs(velocity) > minFlingVelocity) startFling(velocity)
                }
                velocityTracker.clear()
                gestureDragged = false
                selectionPanPointerId = -1
                keepSelectionStart = -1
                keepSelectionEnd = -1
            }
        }
        return super.onTouchEvent(event)
    }

    /** 惯性滚动期间由系统在绘制前驱动（与 ScrollView.computeScroll 同一机制）。 */
    override fun computeScroll() {
        if (flingScroller.computeScrollOffset()) {
            scrollTo(0, flingScroller.currY)
            postInvalidateOnAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        stopFling()
        super.onDetachedFromWindow()
    }

    /**
     * lint 的 ClickableViewAccessibility：自定义了 onTouchEvent 就要显式实现 performClick，
     * 否则读屏/无障碍服务无法触发点击。我们本来就设了 OnClickListener，这里转发即可。
     */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun setOnSelectionChangedListener(listener: (Int) -> Unit) {
        selectionListener = listener
    }

    /** 滚动也回调：位置要随滚动持续落盘，不能只等退出那一刻。 */
    var onScrollChanged: (() -> Unit)? = null

    override fun onScrollChanged(horiz: Int, vert: Int, oldHoriz: Int, oldVert: Int) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert)
        onScrollChanged?.invoke()
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (selStart == lastSelection) return
        lastSelection = selStart
        if (!suppress.get()) selectionListener?.invoke(selStart)
    }
}
