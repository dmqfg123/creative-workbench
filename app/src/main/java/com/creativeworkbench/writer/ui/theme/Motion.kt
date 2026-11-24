package com.creativeworkbench.writer.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * 动效统一口径：时长 + 曲线集中在这里，页面里不再散落魔法数字。
 * 曲线取 M3 expressive 的 emphasized / standard 值，节奏偏"慢一点、软一点"，
 * 写作场景不希望动画抢注意力。
 */
object Motion {
    /** 按压、图标切换这类即时反馈 */
    const val Fast = 140

    /** 页面内元素展开、底色变化 */
    const val Medium = 260

    /** 页面切换 */
    const val Slow = 340

    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
