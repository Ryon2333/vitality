package com.jiang.vitality.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jiang.vitality.ui.backdrop.Capsule
import com.jiang.vitality.ui.backdrop.InteractiveHighlight
import com.jiang.vitality.ui.backdrop.LocalBackdrop
import com.jiang.vitality.ui.backdrop.RoundedRectRefractionWithDispersionShaderString
import com.jiang.vitality.ui.backdrop.getCornerRadii
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

val LocalGlassHazeState = staticCompositionLocalOf<HazeState?> { null }

val GlassControlShape = RoundedCornerShape(22.dp)
val GlassDialogShape = RoundedCornerShape(32.dp)
val GlassDialogColor = Color.White.copy(alpha = .16f)

/**
 * 苹果液态玻璃高精度 7 波段色散折射 Shader (AGSL, Android 13+)
 * 参考 Kyant0 AndroidLiquidGlass 实现：
 * 1. 真实球面透镜弧度映射：circleMap(x) = 1.0 - sqrt(1.0 - x * x)
 * 2. 4 角独立圆角 SDF 距离场与法线梯度：gradSdRoundedRect
 * 3. 7 波段真实光谱色散折射 (红、橙、黄、绿、青、蓝、紫)，消除边缘泛灰与塑料感
 */
const val LIQUID_GLASS_SHADER = RoundedRectRefractionWithDispersionShaderString

/**
 * 药丸透镜内容 Shader (AGSL, Android 13+)
 */
const val PILL_LENS_SHADER = """
    uniform shader content;
    uniform float2 uCenter;
    uniform float2 uHalf;
    uniform float uZoom;
    uniform float uChroma;

    float sdRoundRect(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + r;
        return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
    }

    half4 main(float2 fragCoord) {
        float2 p = fragCoord - uCenter;
        float d = sdRoundRect(p, uHalf, uHalf.y);
        if (d >= 0.0) {
            return content.eval(fragCoord);
        }
        float band = max(uHalf.y * 0.8, 1.0);
        float t = clamp(-d / band, 0.0, 1.0);
        float m = smoothstep(0.0, 1.0, t);
        float zx = 1.0 + uZoom * m;
        float zy = 1.0 + uZoom * 0.45 * m;
        float rimMask = t * (1.0 - t) * 4.0;
        float ca = uChroma * rimMask * 0.09;
        float2 baseUv = uCenter + float2(p.x / zx, p.y / zy);
        half4 c;
        c.r = content.eval(uCenter + float2(p.x / (zx * (1.0 + ca)), p.y / zy)).r;
        half4 g = content.eval(baseUv);
        c.g = g.g;
        c.b = content.eval(uCenter + float2(p.x / (zx * (1.0 - ca)), p.y / zy)).b;
        c.a = g.a;
        return c;
    }
"""

/**
 * 玻璃背景：结合 Kyant0 7-band 色散透镜折射 + Haze 动态背景模糊。
 */
@Composable
fun LiquidGlassBackdrop(
    hazeState: HazeState,
    shape: RoundedCornerShape,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surface
    val glassTint = if (isDark) surface.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.16f)
    val fallback = surface.copy(alpha = if (isDark) 0.30f else 0.24f)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val refractionModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { RuntimeShader(LIQUID_GLASS_SHADER) }
        val refractionHeightPx = with(density) { 16.dp.toPx() }
        val refractionAmountPx = with(density) { 18.dp.toPx() }
        Modifier.graphicsLayer {
            val radii = getCornerRadii(shape, size, layoutDirection, density)
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("offset", 0f, 0f)
            shader.setFloatUniform("cornerRadii", radii)
            shader.setFloatUniform("refractionHeight", refractionHeightPx)
            shader.setFloatUniform("refractionAmount", -refractionAmountPx)
            shader.setFloatUniform("depthEffect", 1f)
            shader.setFloatUniform("chromaticAberration", 1f)
            renderEffect = RenderEffect
                .createRuntimeShaderEffect(shader, "content")
                .asComposeRenderEffect()
            clip = false
        }
    } else {
        Modifier.clip(shape)
    }

    Box(
        modifier = modifier
            .then(refractionModifier)
            .hazeEffect(state = hazeState) {
                blurRadius = 18.dp
                noiseFactor = if (isDark) 0.06f else 0.015f
                tints = listOf(HazeTint(glassTint))
                fallbackTint = HazeTint(fallback)
            },
    )
}

/**
 * 高光描边：基于 Kyant0 Directional Highlight 法线高光 + 1px 细边。
 */
@Composable
fun LiquidGlassHighlight(
    shape: RoundedCornerShape,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(Color.White.copy(alpha = 0.36f), Color.White.copy(alpha = 0.06f))
        } else {
            listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.20f))
        },
    )
    val sheenBrush = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (isDark) 0.05f else 0.08f),
        0.55f to Color.White.copy(alpha = if (isDark) 0.01f else 0.025f),
        1f to Color.White.copy(alpha = if (isDark) 0.035f else 0.055f),
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(sheenBrush)
            .border(1.dp, borderBrush, shape),
    )
}

/**
 * 苹果液态玻璃容器：背景模糊 + 折射 + 高光描边，内容层叠在上。
 */
@Composable
fun LiquidGlassContainer(
    hazeState: HazeState,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(26.dp),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        LiquidGlassBackdrop(
            hazeState = hazeState,
            shape = shape,
            isDark = isDark,
            modifier = Modifier.matchParentSize(),
        )
        LiquidGlassHighlight(
            shape = shape,
            isDark = isDark,
            modifier = Modifier.matchParentSize(),
        )
        Box(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/**
 * 稳定且高效的玻璃采样底衬。
 */
@Composable
fun GlassBackdropSource(
    recoveryMode: Boolean,
    modifier: Modifier = Modifier
) {
    val hazeState = LocalGlassHazeState.current ?: return
    Canvas(modifier.hazeSource(hazeState)) {
        val cool = if (recoveryMode) Color(0xFFE9C8B5) else Color(0xFFBCD0EF)
        val pale = if (recoveryMode) Color(0xFFF5DED0) else Color(0xFFD9DFF2)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(cool.copy(alpha = .22f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(size.width * .18f, size.height * .20f),
                radius = size.minDimension * .54f
            ),
            radius = size.minDimension * .54f,
            center = androidx.compose.ui.geometry.Offset(size.width * .18f, size.height * .20f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(pale.copy(alpha = .24f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(size.width * .84f, size.height * .74f),
                radius = size.minDimension * .62f
            ),
            radius = size.minDimension * .62f,
            center = androidx.compose.ui.geometry.Offset(size.width * .84f, size.height * .74f)
        )
    }
}

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    blurRadius: Dp = 22.dp,
    elevation: Dp = 10.dp,
    backdropBlur: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalVitalityColors.current
    val hazeState = LocalGlassHazeState.current
    val backdrop = LocalBackdrop.current
    val isDark = isSystemInDarkTheme()
    val roundedShape = (shape as? RoundedCornerShape) ?: RoundedCornerShape(26.dp)

    val surfaceContainerColor = if (isDark) {
        Color(0xFF161618).copy(alpha = 0.16f)
    } else {
        Color.White.copy(alpha = 0.18f)
    }

    if (backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Box(
            modifier = modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur((blurRadius * .72f).toPx())
                        lens(
                            refractionHeight = 16f.dp.toPx(),
                            refractionAmount = 20f.dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        Highlight.Default.copy(
                            style = HighlightStyle.Default(
                                color = Color.White.copy(alpha = if (isDark) 0.38f else 0.78f),
                                angle = 45f,
                                falloff = 1.2f
                            )
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = elevation,
                            color = palette.ink.copy(alpha = if (isDark) 0.12f else 0.06f)
                        )
                    },
                    innerShadow = {
                        InnerShadow(
                            radius = 6.dp,
                            color = Color.White.copy(alpha = if (isDark) 0.08f else 0.24f)
                        )
                    },
                    onDrawSurface = {
                        drawRect(surfaceContainerColor)
                    }
                )
        ) {
            content()
        }
    } else {
        Box(
            modifier = modifier
                .shadow(
                    elevation = elevation,
                    shape = shape,
                    clip = false,
                    ambientColor = palette.ink.copy(alpha = .035f),
                    spotColor = palette.ink.copy(alpha = .065f)
                )
        ) {
            if (backdropBlur && hazeState != null) {
                LiquidGlassBackdrop(
                    hazeState = hazeState,
                    shape = roundedShape,
                    isDark = isDark,
                    modifier = Modifier.matchParentSize()
                )
                LiquidGlassHighlight(
                    shape = roundedShape,
                    isDark = isDark,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(shape)
                        .background(Brush.linearGradient(palette.cardColors))
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = .85f),
                                    Color.White.copy(alpha = .35f)
                                )
                            ),
                            shape
                        )
                )
            }
            content()
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    elevation: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit
) = LiquidGlassSurface(
    modifier = modifier,
    shape = shape,
    blurRadius = 22.dp,
    elevation = elevation,
    backdropBlur = true,
    content = content
)

@Composable
fun GlassPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    LiquidGlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        blurRadius = 12.dp,
        elevation = 5.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            androidx.compose.material3.Text(
                title,
                color = Ink,
                fontSize = 28.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            subtitle?.let {
                androidx.compose.material3.Text(
                    it,
                    color = Muted,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun LiquidGlassNavigationSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) = LiquidGlassSurface(
    modifier = modifier,
    shape = RoundedCornerShape(34.dp),
    blurRadius = 24.dp,
    elevation = 16.dp,
    backdropBlur = true,
    content = content
)

/**
 * 苹果液态玻璃交互按钮：支持物理正切形变、接触点聚光高光、弹性果冻缩放。
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors? = null,
    content: @Composable RowScope.() -> Unit
) {
    val palette = LocalVitalityColors.current
    val isDark = isSystemInDarkTheme()
    val backdrop = LocalBackdrop.current
    val animationScope = rememberCoroutineScope()
    val shape = GlassControlShape

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }

    val bgBrush = if (enabled) {
        Brush.verticalGradient(
            listOf(
                palette.accent.copy(alpha = if (isDark) 0.16f else 0.12f),
                palette.accent.copy(alpha = if (isDark) 0.08f else 0.05f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                palette.accent.copy(alpha = 0.08f),
                palette.accent.copy(alpha = 0.04f)
            )
        )
    }

    val borderBrush = if (enabled) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (isDark) 0.88f else 0.96f),
                palette.accent.copy(alpha = if (isDark) 0.30f else 0.22f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.40f),
                Color.White.copy(alpha = 0.10f)
            )
        )
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.60f, stiffness = 800f),
        label = "glass-button-press"
    )

    val contentColor = if (enabled) palette.accent else palette.accent.copy(alpha = 0.45f)

    if (backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Row(
            modifier = modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(2.dp.toPx())
                        lens(12.dp.toPx(), 20.dp.toPx(), depthEffect = true, chromaticAberration = true)
                    },
                    highlight = {
                        Highlight.Default.copy(
                            style = HighlightStyle.Default(
                                color = Color.White.copy(alpha = if (isDark) 0.50f else 0.88f),
                                angle = 45f,
                                falloff = 1f
                            )
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = if (isPressed) 8.dp else 4.dp,
                            color = palette.accent.copy(alpha = if (isPressed) 0.25f else 0.10f)
                        )
                    },
                    innerShadow = {
                        InnerShadow(
                            radius = 4.dp,
                            color = Color.White.copy(alpha = if (isDark) 0.15f else 0.45f)
                        )
                    },
                    layerBlock = {
                        val progress = interactiveHighlight.pressProgress
                        val s = lerp(scale, scale * (1f + 4.dp.toPx() / size.height), progress)

                        val maxOffset = size.minDimension
                        val initialDerivative = 0.05f
                        val offset = interactiveHighlight.offset
                        translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                        translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                        val maxDragScale = 4.dp.toPx() / size.height
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX = s + maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) * (size.width / size.height).fastCoerceAtMost(1f)
                        scaleY = s + maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) * (size.height / size.width).fastCoerceAtMost(1f)
                    },
                    onDrawSurface = {
                        drawRect(bgBrush)
                    }
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick
                )
                .then(interactiveHighlight.modifier)
                .then(interactiveHighlight.gestureModifier)
                .height(52.dp)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content()
            }
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = if (isPressed) 8.dp else 0.dp,
                    shape = shape,
                    spotColor = palette.accent.copy(alpha = 0.25f),
                    ambientColor = palette.accent.copy(alpha = 0.10f)
                ),
            enabled = enabled,
            shape = shape,
            color = Color.Transparent,
            contentColor = contentColor,
            interactionSource = interactionSource
        ) {
            Box(
                modifier = Modifier
                    .background(bgBrush, shape)
                    .border(1.dp, borderBrush, shape)
                    .padding(horizontal = 20.dp, vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CompositionLocalProvider(LocalContentColor provides contentColor) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * 苹果液态玻璃轮廓/次级按钮。
 */
@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    horizontalPadding: Dp = 16.dp,
    content: @Composable RowScope.() -> Unit
) {
    val palette = LocalVitalityColors.current
    val isDark = isSystemInDarkTheme()
    val backdrop = LocalBackdrop.current
    val animationScope = rememberCoroutineScope()
    val shape = GlassControlShape

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }

    val bgBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (isDark) 0.08f else 0.16f),
            Color.White.copy(alpha = if (isDark) 0.025f else 0.055f)
        )
    )
    val borderBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (isDark) 0.60f else 0.92f),
            Color.White.copy(alpha = if (isDark) 0.10f else 0.35f)
        )
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.60f, stiffness = 800f),
        label = "glass-outline-press"
    )
    val contentColor = if (enabled) palette.accent else palette.accent.copy(alpha = 0.45f)

    if (backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Row(
            modifier = modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(2.dp.toPx())
                        lens(8.dp.toPx(), 14.dp.toPx(), depthEffect = true, chromaticAberration = true)
                    },
                    highlight = {
                        Highlight.Default.copy(
                            style = HighlightStyle.Default(
                                color = Color.White.copy(alpha = if (isDark) 0.35f else 0.85f),
                                angle = 45f,
                                falloff = 1f
                            )
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = if (isPressed) 6.dp else 2.dp,
                            color = palette.ink.copy(alpha = if (isDark) 0.08f else 0.04f)
                        )
                    },
                    innerShadow = {
                        InnerShadow(
                            radius = 3.dp,
                            color = Color.White.copy(alpha = if (isDark) 0.15f else 0.50f)
                        )
                    },
                    layerBlock = {
                        val progress = interactiveHighlight.pressProgress
                        val s = lerp(scale, scale * (1f + 3.dp.toPx() / size.height), progress)
                        scaleX = s
                        scaleY = s
                    },
                    onDrawSurface = {
                        drawRect(bgBrush)
                    }
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick
                )
                .then(interactiveHighlight.modifier)
                .then(interactiveHighlight.gestureModifier)
                .height(height)
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content()
            }
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            enabled = enabled,
            shape = shape,
            color = Color.Transparent,
            contentColor = contentColor,
            interactionSource = interactionSource
        ) {
            Box(
                modifier = Modifier
                    .background(bgBrush, shape)
                    .border(1.dp, borderBrush, shape)
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = if (height < 44.dp) 7.dp else 12.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CompositionLocalProvider(LocalContentColor provides contentColor) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
fun glassTextFieldColors(): TextFieldColors {
    val palette = LocalVitalityColors.current
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.White.copy(alpha = .18f),
        unfocusedContainerColor = Color.White.copy(alpha = .08f),
        disabledContainerColor = Color.White.copy(alpha = .05f),
        focusedBorderColor = palette.accent.copy(alpha = .62f),
        unfocusedBorderColor = Color.White.copy(alpha = .62f),
        cursorColor = palette.accent,
        focusedLabelColor = palette.accent
    )
}

@Composable
fun GlassActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    GlassOutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        height = 38.dp,
        horizontalPadding = 13.dp
    ) {
        androidx.compose.material3.Text(text)
    }
}

@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    text: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            LiquidGlassSurface(
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                shape = GlassDialogShape,
                blurRadius = 18.dp,
                elevation = 18.dp
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    title()
                    text?.invoke()
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions
                    )
                }
            }
        }
    }
}
