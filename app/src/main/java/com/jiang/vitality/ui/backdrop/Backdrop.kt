package com.jiang.vitality.ui.backdrop

import androidx.compose.runtime.staticCompositionLocalOf
typealias Backdrop = com.kyant.backdrop.Backdrop

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }
