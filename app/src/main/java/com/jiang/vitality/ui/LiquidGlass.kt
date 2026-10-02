package com.jiang.vitality.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

val LocalGlassHazeState = staticCompositionLocalOf<HazeState?> { null }

val GlassControlShape = RoundedCornerShape(20.dp)
val GlassDialogShape = RoundedCornerShape(32.dp)
val GlassDialogColor = Color.White.copy(alpha = .72f)

/**
 * A stable, low-detail source for the glass renderer. Moving geometry is intentionally
 * drawn on its own layer underneath this texture, so it remains visible through the
 * translucent material without forcing every glass surface to recalculate a full-screen
 * blur for every physics tick.
 */
@Composable
fun GlassBackdropSource(
    recoveryMode: Boolean,
    modifier: Modifier = Modifier
) {
    val hazeState = LocalGlassHazeState.current
    if (hazeState == null) return
    Canvas(modifier.hazeSource(hazeState)) {
        val cool = if (recoveryMode) Color(0xFFE9C8B5) else Color(0xFFBCD0EF)
        val pale = if (recoveryMode) Color(0xFFF5DED0) else Color(0xFFD9DFF2)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(cool.copy(alpha = .17f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(size.width * .18f, size.height * .20f),
                radius = size.minDimension * .54f
            ),
            radius = size.minDimension * .54f,
            center = androidx.compose.ui.geometry.Offset(size.width * .18f, size.height * .20f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(pale.copy(alpha = .18f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(size.width * .84f, size.height * .74f),
                radius = size.minDimension * .62f
            ),
            radius = size.minDimension * .62f,
            center = androidx.compose.ui.geometry.Offset(size.width * .84f, size.height * .74f)
        )
    }
}

@Composable
@OptIn(ExperimentalHazeMaterialsApi::class)
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(30.dp),
    blurRadius: Dp = 14.dp,
    elevation: Dp = 16.dp,
    backdropBlur: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalVitalityColors.current
    val hazeState = LocalGlassHazeState.current
    val material = if (backdropBlur && hazeState != null) {
        Modifier
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeMaterials.ultraThin(containerColor = palette.background)
            ) {
                this.blurRadius = blurRadius
                noiseFactor = .04f
            }
    } else {
        Modifier.background(Brush.linearGradient(palette.cardColors), shape)
    }

    Box(
        modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = palette.ink.copy(alpha = .055f),
                spotColor = palette.ink.copy(alpha = .095f)
            )
            .then(material)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = .96f),
                        Color.White.copy(alpha = .58f),
                        palette.cardBorder.copy(alpha = .40f)
                    )
                ),
                shape = shape
            )
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = .11f),
                            Color.White.copy(alpha = .03f),
                            Color.Transparent
                        )
                    ),
                    shape
                )
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = .09f), Color.Transparent),
                        radius = 420f
                    ),
                    shape
                )
        )
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) = LiquidGlassSurface(
    modifier = modifier,
    shape = RoundedCornerShape(30.dp),
    blurRadius = 14.dp,
    elevation = 15.dp,
    content = content
)

@Composable
fun LiquidGlassNavigationSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) = LiquidGlassSurface(
    modifier = modifier,
    shape = RoundedCornerShape(32.dp),
    blurRadius = 16.dp,
    elevation = 20.dp,
    backdropBlur = true,
    content = content
)

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors? = null,
    content: @Composable RowScope.() -> Unit
) {
    val palette = LocalVitalityColors.current
    val resolvedColors = colors ?: ButtonDefaults.buttonColors(
        containerColor = palette.accent.copy(alpha = .86f),
        contentColor = Color.White,
        disabledContainerColor = palette.accent.copy(alpha = .11f),
        disabledContentColor = palette.accent.copy(alpha = .58f)
    )
    Button(
        onClick = onClick,
        modifier = modifier.shadow(
            elevation = if (enabled) 9.dp else 0.dp,
            shape = GlassControlShape,
            clip = false,
            ambientColor = palette.accent.copy(alpha = .07f),
            spotColor = palette.accent.copy(alpha = .13f)
        ),
        enabled = enabled,
        shape = GlassControlShape,
        colors = resolvedColors,
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (enabled) .42f else .64f)),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        content = content
    )
}

@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val palette = LocalVitalityColors.current
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = GlassControlShape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White.copy(alpha = .32f),
            contentColor = palette.accent,
            disabledContainerColor = Color.White.copy(alpha = .18f)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .84f)),
        content = content
    )
}

@Composable
fun glassTextFieldColors(): TextFieldColors {
    val palette = LocalVitalityColors.current
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.White.copy(alpha = .48f),
        unfocusedContainerColor = Color.White.copy(alpha = .32f),
        disabledContainerColor = Color.White.copy(alpha = .20f),
        focusedBorderColor = palette.accent.copy(alpha = .62f),
        unfocusedBorderColor = Color.White.copy(alpha = .88f),
        cursorColor = palette.accent,
        focusedLabelColor = palette.accent
    )
}
