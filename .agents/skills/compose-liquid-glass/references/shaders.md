# 兩支 AGSL Shader 全文與餵法

兩支都是 Android 13+（API 33）的 `RuntimeShader`。**吃的東西不同**：
`LIQUID_GLASS_SHADER` 吃 Haze 畫好的模糊背景；`PILL_LENS_SHADER` 吃元件自己的內容層。

## LIQUID_GLASS_SHADER（玻璃背景折射）

對 Haze 模糊背景做三件事：SDF 膠囊裁切＋1.2px 邊緣抗鋸齒（取代 Compose clip）、
邊緣帶內把取樣座標往中心位移（越靠邊越強、平方衰減）→ 厚玻璃透鏡感＋RGB 三通道
位移微差 → 邊緣輕微色散、Fresnel 邊緣增亮（偏頂部）＋貼邊 1~2px 高光細線。

```kotlin
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
```

### 餵法（掛在 hazeEffect 的同一個 Box 上）

```kotlin
val shader = remember { RuntimeShader(LIQUID_GLASS_SHADER) }
val bandPx = with(density) { 20.dp.toPx() }   // 折射帶寬
val dispPx = with(density) { 9.dp.toPx() }    // 最大位移
// 【雷】淺色 fresnel 必須壓低到 0.10：邊光只照得到貼邊 ~20dp、中段照不到，
// 太強會在玻璃中段夾出一條全寬羽化「陰影帶」（像素取證追過四輪的真兇）
val fresnel = if (isDark) 0.22f else 0.10f
Modifier.graphicsLayer {
    shader.setFloatUniform("uSize", size.width, size.height)
    shader.setFloatUniform("uRadius", size.height * 0.5f)  // 全膠囊
    shader.setFloatUniform("uBand", bandPx)
    shader.setFloatUniform("uDisp", dispPx)
    shader.setFloatUniform("uFresnel", fresnel)
    renderEffect = RenderEffect
        .createRuntimeShaderEffect(shader, "content")
        .asComposeRenderEffect()
    clip = false   // 裁切交給 shader 的 SDF（有抗鋸齒），不要雙重裁
}
```

## PILL_LENS_SHADER（內容透鏡）

藥丸（uCenter/uHalf 每幀由 graphicsLayer 餵）範圍內的內容做厚玻璃透鏡：

- 各向異性放大（橫向 uZoom 全量、縱向 0.45×）——由邊到心遞增，邊緣帶自然形成拉伸塗抹
- rim 帶 RGB 各採不同放大率 → 內容跨過玻璃邊緣時**真色散**（不是假的顏色疊層）
- 藥丸外原樣通過；靜止時 uZoom 很小、拖曳抬起時全開

```kotlin
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
```

### 餵法（掛在「標籤/圖示 Row」的 graphicsLayer 上）

```kotlin
Modifier.graphicsLayer {
    val liftScale = 1f + 0.18f * liftValue
    val stretch = ((r - l) / pillWidthPx).coerceIn(0.75f, 1.5f)
    lensShader.setFloatUniform("uCenter", (l + r) / 2f, size.height / 2f)
    lensShader.setFloatUniform(
        "uHalf",
        pillWidthPx / 2f * stretch * liftScale,
        size.height / 2f * liftScale,
    )
    // 靜止微放大、拖曳抬起放大＋色散全開
    lensShader.setFloatUniform("uZoom", 0.10f + 0.22f * liftValue)
    lensShader.setFloatUniform("uChroma", liftValue)
    renderEffect = RenderEffect
        .createRuntimeShaderEffect(lensShader, "content")
        .asComposeRenderEffect()
}
```

## 降級（兩支 shader 通用）

```kotlin
// 【雷】SDK_INT 條件必須「直接寫在 if 裡」：lint 的 NewApi 檢查看不懂
// 「shader 非空蘊含 API 33」，抽出去會多報一排 error
val lensShader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    remember { RuntimeShader(PILL_LENS_SHADER) }
} else {
    null
}
val effectModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && lensShader != null) {
    Modifier.graphicsLayer { /* 餵 uniforms + renderEffect */ }
} else {
    Modifier            // 透鏡直接省略；玻璃背景那支則退成 Modifier.clip(shape)
}
```
