package com.jiang.vitality.ui.navigation

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jiang.vitality.ui.LocalGlassHazeState
import com.jiang.vitality.ui.LocalVitalityColors
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * iOS 26 液态玻璃的折射 shader（AGSL，Android 13+）。对 Haze 画好的模糊背景做三件事：
 * 1. 圆角矩形 SDF 裁切 + 边缘抗锯齿；
 * 2. 边缘带内把采样坐标往中心位移（越靠边越强）→ 厚玻璃透镜放大，RGB 三通道位移微差 → 色散；
 * 3. Fresnel 边缘增亮（偏向顶部）。
 */
const val LIQUID_GLASS_SHADER = """
    uniform shader content;
    uniform float2 uSize;
    uniform float uRadius;
    uniform float uBand;
    uniform float uDisp;
    uniform float uFresnel;

    float sdRoundRect(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + r;
        return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
    }

    half4 main(float2 fragCoord) {
        float2 halfSize = uSize * 0.5;
        float2 p = fragCoord - halfSize;
        float d = sdRoundRect(p, halfSize, uRadius);
        if (d > 0.0) {
            return half4(0.0);
        }
        float edgeAA = 1.0 - smoothstep(-1.2, 0.0, d);

        float t = clamp(-d / uBand, 0.0, 1.0);
        float bend = (1.0 - t) * (1.0 - t);

        float2 q = abs(p) - halfSize + uRadius;
        float2 n;
        if (q.x > 0.0 && q.y > 0.0) {
            n = normalize(sign(p) * q);
        } else if (q.x > q.y) {
            n = float2(sign(p.x), 0.0);
        } else {
            n = float2(0.0, sign(p.y));
        }

        float2 disp = -n * bend * uDisp;
        half3 col;
        col.r = content.eval(fragCoord + disp * 1.10).r;
        col.g = content.eval(fragCoord + disp).g;
        col.b = content.eval(fragCoord + disp * 0.90).b;

        float topBias = 0.55 + 0.45 * clamp(-p.y / halfSize.y, -1.0, 1.0);
        float rim = bend * uFresnel * topBias;
        float lineBand = smoothstep(2.5, 0.8, -d);
        col += half3(rim + lineBand * uFresnel * 0.6 * topBias);

        return half4(col * edgeAA, edgeAA);
    }
"""

/**
 * 玻璃背景层：Haze 即时背景模糊，Android 13+ 再叠 AGSL 折射/Fresnel。
 * [cornerRadiusPx] 传负数时用「高度一半」当作圆角（胶囊）。
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@SuppressLint("NewApi") // Runtime-gated below; API 29-32 always use the clipped fallback.
@Composable
fun LiquidGlassBackdrop(
    shape: Shape,
    cornerRadiusPx: Float,
    blurRadius: Dp,
    modifier: Modifier = Modifier
) {
    val palette = LocalVitalityColors.current
    val hazeState = LocalGlassHazeState.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val useRuntimeRefraction = remember(context) {
        val lowRam = context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
        val emulator = Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
            Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
            Build.HARDWARE.contains("goldfish", ignoreCase = true)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !lowRam && !emulator
    }

    val refraction = if (useRuntimeRefraction) {
        val shader = remember { RuntimeShader(LIQUID_GLASS_SHADER) }
        val bandPx = with(density) { 20.dp.toPx() }
        val dispPx = with(density) { 9.dp.toPx() }
        Modifier.graphicsLayer {
            shader.setFloatUniform("uSize", size.width, size.height)
            shader.setFloatUniform("uRadius", if (cornerRadiusPx < 0f) size.height / 2f else cornerRadiusPx)
            shader.setFloatUniform("uBand", bandPx)
            shader.setFloatUniform("uDisp", dispPx)
            shader.setFloatUniform("uFresnel", 0.10f)
            renderEffect = RenderEffect
                .createRuntimeShaderEffect(shader, "content")
                .asComposeRenderEffect()
            clip = false
        }
    } else {
        Modifier.clip(shape)
    }

    val glass = if (hazeState != null) {
        Modifier
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeMaterials.ultraThin(containerColor = palette.background)
            ) {
                this.blurRadius = blurRadius
                noiseFactor = .02f
            }
    } else {
        Modifier.background(Brush.linearGradient(palette.cardColors), shape)
    }

    Box(modifier.then(refraction).then(glass))
}

/**
 * 高光描边层：不进折射 shader，保持 1px 锐利。高光铺满全高（上强下弱），
 * 避免只照上半部把中下段夹成一条灰带（实机踩坑）。
 */
@Composable
fun LiquidGlassHighlight(
    shape: Shape,
    modifier: Modifier = Modifier
) {
    val borderBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.75f),
            Color.White.copy(alpha = 0.16f)
        )
    )
    val sheenBrush = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.10f),
        0.55f to Color.White.copy(alpha = 0.08f),
        1f to Color.White.copy(alpha = 0.09f)
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(sheenBrush)
            .border(1.dp, borderBrush, shape)
    )
}
