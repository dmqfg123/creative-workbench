package com.creativeworkbench.writer.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creativeworkbench.writer.ui.theme.Motion

/** 页面标题：大标题 + 一行副标题 + 右侧图标动作。所有一级页统一用这个。 */
@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, top = 14.dp, bottom = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), content = actions)
        }
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp)) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** 内容卡：奶油底 + 1dp 描边，不用重阴影。 */
@Composable
fun WbCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = border
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = border
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** 主按钮：48dp 胶囊，全 App 只有这一个实心按钮样式。 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        contentPadding = PaddingValues(horizontal = 22.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 次按钮：描边胶囊。 */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 空态：一张线稿纸 + 一句人话 + 一个动作。 */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    AnimatedVisibility(
        visible = appeared,
        enter = fadeIn(tween(Motion.Medium)) +
            slideInVertically(tween(Motion.Medium, easing = Motion.EmphasizedDecelerate)) { it / 12 }
    ) {
        Column(
            modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PaperMark()
            Spacer(Modifier.height(20.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(20.dp))
                PillButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

/** 线稿纸：不引资源，跟着主题色走。 */
@Composable
fun PaperMark(size: Dp = 92.dp) {
    val line = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * 0.022f
        drawRoundRect(
            color = line,
            topLeft = Offset(s * 0.22f, s * 0.12f),
            size = Size(s * 0.56f, s * 0.76f),
            cornerRadius = CornerRadius(s * 0.06f, s * 0.06f),
            style = Stroke(width = stroke)
        )
        repeat(3) { index ->
            val y = s * (0.34f + index * 0.13f)
            drawLine(
                color = line,
                start = Offset(s * 0.32f, y),
                end = Offset(s * (0.68f - index * 0.08f), y),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
        drawCircle(color = accent, radius = s * 0.045f, center = Offset(s * 0.66f, s * 0.74f))
    }
}

/** 搜索框：胶囊，无边框重影。 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onClose: () -> Unit
) {
    // 点开搜索就聚焦：以前还得再点一次输入框，键盘才出来
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp).padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭搜索",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** 小胶囊标签：类型、标签、筛选都用它。 */
@Composable
fun Tag(
    text: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val bg = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = CircleShape,
        color = bg,
        modifier = if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

/**
 * 横向滚动的胶囊行。裁切边缘加渐隐：右边还能滚时，最后半个 chip 淡出，
 * 用户一眼看出「还有」；滚到底渐隐自动消失，不长期挡内容。
 * 渐隐在 draw 阶段直接画在内容之上——LazyColumn 给 item 的是无界高度约束，
 * 用嵌套 Box + fillMaxHeight 量出来是 0，画不出来。
 */
@Composable
fun ChipRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val scroll = rememberScrollState()
    val background = MaterialTheme.colorScheme.background
    val edge = 28.dp
    Box(
        modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                val fade = edge.toPx()
                if (fade <= 0f) return@drawWithContent
                if (scroll.value > 0) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(background, Color.Transparent),
                            startX = 0f,
                            endX = fade
                        ),
                        size = Size(fade, size.height)
                    )
                }
                if (scroll.canScrollForward) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, background),
                            startX = size.width - fade,
                            endX = size.width
                        ),
                        topLeft = Offset(size.width - fade, 0f),
                        size = Size(fade, size.height)
                    )
                }
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

/** 正文样式：正文区唯一入口，保证字号/行高口径一致。 */
@Composable
fun bodyStyle(fontSizeSp: Float, lineHeightSp: Float): TextStyle =
    MaterialTheme.typography.bodyLarge.copy(fontSize = fontSizeSp.sp, lineHeight = lineHeightSp.sp)

@Composable
fun MutedText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun DividerLine() {
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
fun LocalContentColorCompat(): Color = LocalContentColor.current

@Composable
fun SpacerH(height: Dp) = Spacer(Modifier.height(height))

@Composable
fun SpacerW(width: Dp) = Spacer(Modifier.width(width))