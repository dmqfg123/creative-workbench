package com.creativeworkbench.writer.ui.editor

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.TextWatcher
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
    val gapPx = with(density) { 9.dp.toPx().toInt() }

    val editText = remember {
        BodyEditText(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            typeface = Typeface.SERIF
            setLineSpacing(0f, lineHeightSp / textSizeSp)
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

    // 段落间距：光标/文本变化后合并 200ms 再刷 span，避免每个字都重扫全文
    var spanJob: android.os.Handler? = remember { null }
    val refreshGaps: () -> Unit = {
        val editable = buffer
        editText.suppress.set(true)
        editable.getSpans(0, editable.length, ParagraphGapSpan::class.java).forEach { editable.removeSpan(it) }
        var index = editable.indexOf('\n')
        while (index >= 0 && index + 1 < editable.length) {
            editable.setSpan(
                ParagraphGapSpan(gapPx),
                index + 1,
                (index + 2).coerceAtMost(editable.length),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            index = editable.indexOf('\n', index + 1)
        }
        editText.suppress.set(false)
    }
    LaunchedEffect(text.length) {
        kotlinx.coroutines.delay(200)
        refreshGaps()
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
        onDispose {
            editText.removeTextChangedListener(watcher)
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

    LaunchedEffect(textSizeSp, lineHeightSp, textColor) {
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        editText.setLineSpacing(0f, lineHeightSp / textSizeSp)
        editText.setTextColor(textColor)
    }

    editText.setOnSelectionChangedListener { offset -> onSel[0]?.invoke(offset) }

    AndroidView(
        factory = { editText },
        modifier = modifier,
        update = { view -> view.setTextColor(textColor) }
    )
}

/**
 * 段落间距：给每个段落的首行加一点上方留白。
 * 单一编辑面里段落只是 \n，靠这个 span 才不会「挤成一坨」。
 * 只覆盖段落首字符，所以只抬高该段第一行，段内行距不变。
 */
private class ParagraphGapSpan(private val gapPx: Int) : android.text.style.LineHeightSpan {
    override fun chooseHeight(
        text: CharSequence,
        start: Int,
        end: Int,
        spanstartv: Int,
        lineHeight: Int,
        fm: android.graphics.Paint.FontMetricsInt
    ) {
        if (gapPx <= 0) return
        fm.top -= gapPx
        fm.ascent -= gapPx
    }
}

private const val MENU_AI_REWRITE = 1001

private fun showSoftKeyboard(view: View) {
    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
    if (!view.isFocused) view.requestFocus()
    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
}

/** 程序改文本时抬起来，别让 TextWatcher 当成用户输入回写一遍。 */
class BodyEditText(context: Context) : EditText(context) {
    val suppress = java.util.concurrent.atomic.AtomicBoolean(false)
    private var selectionListener: ((Int) -> Unit)? = null
    private var lastSelection = -1

    fun setOnSelectionChangedListener(listener: (Int) -> Unit) {
        selectionListener = listener
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (selStart == lastSelection) return
        lastSelection = selStart
        if (!suppress.get()) selectionListener?.invoke(selStart)
    }
}
