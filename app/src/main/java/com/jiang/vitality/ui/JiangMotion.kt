package com.jiang.vitality.ui

/**
 * JiangOS 的统一运动语言。空间切换、玻璃形变和按压反馈只使用这三组节奏，
 * 避免各页面各自定义时长后产生割裂感。
 */
object JiangMotion {
    const val SpatialDamping = 0.78f
    const val SpatialStiffness = 105f
    const val FluidDamping = 0.66f
    const val FluidStiffness = 180f
    const val PressDamping = 0.58f
    const val PressStiffness = 360f
}
