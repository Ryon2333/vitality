# 玻璃面板：Haze 接線 + Backdrop / Highlight / Container

完整可複製。shader 常數見 [shaders.md](shaders.md)。package 名稱自行替換。

## Haze 接線規則（最重要的一段）

`hazeSource` **只捕捉 modifier 鏈上比它「內層」的繪製**（Compose modifier 越後面越內層）：

```kotlin
Box(Modifier.hazeSource(state).background(bg))   // ✓ background 在內層 → 被捕進玻璃
Box(Modifier.background(bg).hazeSource(state))   // ✗ background 在外層 → 捕到空 Box
```

錯的那種症狀：玻璃變成**不透明深灰髒塊**——Haze 拿到透明，`LIQUID_GLASS_SHADER`
把透明黑 premultiply 成近黑輸出，再被高光層提亮成深灰。看到深灰第一件事就是查鏈位。

標準畫面結構（背景 z0、內容 z1，玻璃 bar 是之後的 sibling）：

```kotlin
val hazeState = rememberHazeState()
Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().hazeSource(hazeState).background(bg))          // z0
    Column(
        Modifier
            .fillMaxSize()
            .hazeSource(hazeState, zIndex = 1f)   // 在 scroll 與 padding「之前」
            .verticalScroll(scrollState)
            .padding(…),
    ) { /* 捲動內容 */ }
    GlassBar(Modifier.align(Alignment.BottomCenter)) // hazeEffect 在這裡面
}
```

不要在玻璃 bar 底下再鋪不透明漸層 scrim——那會讓模糊沒東西可糊，變成
「只有 bar 裡看得到內容、周圍一片死白」。內容與 bar 的分隔交給模糊本身。

## LiquidGlassBackdrop（模糊＋折射）

```kotlin
@Composable
fun LiquidGlassBackdrop(
    hazeState: HazeState,
    shape: RoundedCornerShape,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surface
    // 【雷】淺色用純白 0.45 提亮玻璃體——tint 太低時模糊背景的灰藍透出來，
    // 被上下高光邊一對比，玻璃中段看起來像夾了深色層
    val glassTint = if (isDark) surface.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.45f)
    val fallback = surface.copy(alpha = if (isDark) 0.80f else 0.86f)
    val density = LocalDensity.current

    val refractionModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { RuntimeShader(LIQUID_GLASS_SHADER) }
        val bandPx = with(density) { 20.dp.toPx() }
        val dispPx = with(density) { 9.dp.toPx() }
        val fresnel = if (isDark) 0.22f else 0.10f   // 淺色壓低，見 shaders.md 的雷
        Modifier.graphicsLayer {
            shader.setFloatUniform("uSize", size.width, size.height)
            shader.setFloatUniform("uRadius", size.height * 0.5f)
            shader.setFloatUniform("uBand", bandPx)
            shader.setFloatUniform("uDisp", dispPx)
            shader.setFloatUniform("uFresnel", fresnel)
            renderEffect = RenderEffect
                .createRuntimeShaderEffect(shader, "content")
                .asComposeRenderEffect()
            clip = false
        }
    } else {
        Modifier.clip(shape)   // 降級：一般裁切，玻璃感交給高光層撐場
    }

    Box(
        modifier = modifier
            .then(refractionModifier)
            .hazeEffect(state = hazeState) {
                blurRadius = 22.dp
                noiseFactor = if (isDark) 0.08f else 0.02f
                tints = listOf(HazeTint(glassTint))
                fallbackTint = HazeTint(fallback)
            },
    )
}
```

## LiquidGlassHighlight（高光描邊——刻意獨立一層）

**不進折射 shader**：描邊要維持 1px 銳利，進了 shader 會被折射扭糊。

```kotlin
@Composable
fun LiquidGlassHighlight(
    shape: RoundedCornerShape,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.05f))
        } else {
            listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.16f))
        },
    )
    // 【雷】sheen 必須鋪滿全高（上強下弱）：只照上半部會把中下段夾成一條灰帶，
    // 被上下亮邊對比得像「底部陰影」（像素實測 255→247→255 三明治）
    val sheenBrush = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (isDark) 0.07f else 0.10f),
        0.55f to Color.White.copy(alpha = if (isDark) 0.02f else 0.08f),
        1f to Color.White.copy(alpha = if (isDark) 0.05f else 0.09f),
    )
    Box(modifier.clip(shape).background(sheenBrush).border(1.dp, borderBrush, shape))
}
```

## LiquidGlassContainer（現成容器）

一般用途直接用這個；要自訂疊層（例如導覽列還要塞拖曳藥丸）就自己拿上面兩塊疊。

```kotlin
@Composable
fun LiquidGlassContainer(
    hazeState: HazeState,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(percent = 50),
    contentPadding: PaddingValues = PaddingValues(4.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        LiquidGlassBackdrop(hazeState, shape, isDark, Modifier.matchParentSize())
        LiquidGlassHighlight(shape, isDark, Modifier.matchParentSize())
        Box(Modifier.padding(contentPadding), content = content)
    }
}
```

## 陰影原則

- **玻璃 bar 本體不畫陰影**（iOS 玻璃 bar 近乎無影；半透明面板的陰影會從玻璃裡透出來變灰底）。
- 真的要影：獨立投影層（下移＋內縮、把膠囊輪廓從陰影 clip 掉），不要直接 `Modifier.shadow`。
- 藥丸只在拖曳抬起時投影（`shadowElevation = 14.dp.toPx() * lift`）。
