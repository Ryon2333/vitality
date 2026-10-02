package com.jiang.vitality.ui.navigation

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

// 导航栏所有物理动画的规格集中于此。全部使用带阻尼与回弹的 spring，
// 营造「软、慢、有生命感」的液态形变，而不是机械的线性过渡。

// 气泡 ↔ 导航栏 morph：iOS 风格的顺滑弹簧——开始缓慢、中间流畅、接近目标柔和减速、结束极轻微回弹。
internal val navigationMorphSpec: SpringSpec<Float> =
    spring(dampingRatio = 0.75f, stiffness = 90f)

// 液态光源在玻璃内部流动：略快一点但仍柔和。
internal val liquidLightSpec: SpringSpec<Float> =
    spring(dampingRatio = 0.58f, stiffness = 135f)

// 导航项按压响应：轻快。
internal val itemPressSpec: SpringSpec<Float> =
    spring(dampingRatio = 0.55f, stiffness = 340f)
