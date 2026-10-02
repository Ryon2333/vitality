# 藥丸切換器（完整手勢版）＋ 輕量分段指示

> 兩個元件的可編譯全文都在 `assets/`（`LiquidGlassPillSwitcher.kt`、`LiquidSegmented.kt`），
> 直接複製；本文是逐段解說，改配方時才需要細讀。

## LiquidGlassPillSwitcher（點擊＋按住拖曳＋透鏡，完整版）

互動狀態機：快點＝切換；按住 160ms 或滑過 touchSlop＝抬起（放大＋浮起影＋色散全開）
→ 1:1 跟手 → 跨格觸覺 tick → 放開吸附最近選項。單一 pointerInput 全包，選項不掛 clickable。

```kotlin
private const val LIFT_HOLD_MS = 160L
private const val PILL_WIDTH_RATIO = 0.82f
private const val LIFT_SCALE = 0.18f

@Composable
fun LiquidGlassPillSwitcher(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val haptics = LocalHapticFeedback.current

        val barWidthPx = with(density) { maxWidth.toPx() }
        val itemWidthPx = barWidthPx / items.size
        val pillWidthPx = itemWidthPx * PILL_WIDTH_RATIO
        fun slotCenter(index: Int) = itemWidthPx * index + itemWidthPx / 2f
        fun indexAt(x: Float) = (x / itemWidthPx).toInt().coerceIn(0, items.size - 1)
        val minCenter = pillWidthPx / 2f
        val maxCenter = barWidthPx - pillWidthPx / 2f

        val leftEdge = remember { Animatable(slotCenter(selectedIndex) - pillWidthPx / 2f) }
        val rightEdge = remember { Animatable(slotCenter(selectedIndex) + pillWidthPx / 2f) }
        var dragging by remember { mutableStateOf(false) }
        var pressedIndex by remember { mutableIntStateOf(-1) }
        // 手指目標中心：手勢層只寫這個值，實際移動由逐幀追蹤迴圈消化
        var dragCenter by remember { mutableFloatStateOf(slotCenter(selectedIndex)) }
        val lift = animateFloatAsState(
            targetValue = if (dragging) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = 480f),
            label = "pill-lift",
        )

        // 非拖曳：跟隨 selectedIndex 液態滑移（前緣硬彈簧先衝、後緣軟彈簧拖行 → 拉伸回彈）。
        // dragging 也是 key：放開手指（含落回原選項）由這裡統一收攏，別另開 settle 動畫互搶。
        LaunchedEffect(selectedIndex, dragging, itemWidthPx) {
            if (dragging) return@LaunchedEffect
            val targetLeft = slotCenter(selectedIndex) - pillWidthPx / 2f
            val targetRight = targetLeft + pillWidthPx
            val movingRight = targetLeft > leftEdge.value
            val lead = spring<Float>(dampingRatio = 0.62f, stiffness = 900f)
            val trail = spring<Float>(dampingRatio = 0.85f, stiffness = 340f)
            launch { leftEdge.animateTo(targetLeft, if (movingRight) trail else lead) }
            launch { rightEdge.animateTo(targetRight, if (movingRight) lead else trail) }
        }

        // 拖曳中：單一逐幀追蹤迴圈——前緣快、後緣慢指數逼近手指，每幀只 snapTo 一次。
        // 【效能鐵則】不是「每事件重啟彈簧」——那個在 120Hz 實機會凍住再瞬移。
        LaunchedEffect(dragging) {
            if (!dragging) return@LaunchedEffect
            var lastNanos = withFrameNanos { it }
            while (dragging) {
                var newLeft = leftEdge.value
                var newRight = rightEdge.value
                withFrameNanos { now ->
                    val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    lastNanos = now
                    val targetLeft = dragCenter - pillWidthPx / 2f
                    val targetRight = dragCenter + pillWidthPx / 2f
                    val movingRight = targetLeft + targetRight > leftEdge.value + rightEdge.value
                    val fast = 1f - exp(-dt * 42f)
                    val slow = 1f - exp(-dt * 16f)
                    newLeft = leftEdge.value +
                        (targetLeft - leftEdge.value) * (if (movingRight) slow else fast)
                    newRight = rightEdge.value +
                        (targetRight - rightEdge.value) * (if (movingRight) fast else slow)
                }
                leftEdge.snapTo(newLeft)
                rightEdge.snapTo(newRight)
            }
        }

        // ---- 藥丸（半透明漸層＋白高光邊；動畫值只在 graphicsLayer 讀）----
        val primary = MaterialTheme.colorScheme.primary
        val pillShape = RoundedCornerShape(percent = 50)
        val pillBrush = remember(isDark, primary) {
            Brush.verticalGradient(
                colors = if (isDark) {
                    listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.10f))
                } else {
                    listOf(primary.copy(alpha = 0.22f), primary.copy(alpha = 0.13f))
                },
            )
        }
        val pillBorder = remember(isDark, primary) {
            Brush.verticalGradient(
                colors = if (isDark) {
                    listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.05f))
                } else {
                    listOf(Color.White.copy(alpha = 0.90f), primary.copy(alpha = 0.16f))
                },
            )
        }
        val liftGlowColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.28f)
        val pillWidthDp = with(density) { pillWidthPx.toDp() }
        val shadowSpot = Color.Black.copy(alpha = 0.30f)
        val shadowAmbient = Color.Black.copy(alpha = 0.10f)

        Box(
            modifier = Modifier
                .width(pillWidthDp)
                .fillMaxHeight()
                .graphicsLayer {
                    val l = leftEdge.value
                    val r = rightEdge.value
                    val liftValue = lift.value
                    translationX = (l + r) / 2f - pillWidthPx / 2f
                    val stretch = ((r - l) / pillWidthPx).coerceIn(0.75f, 1.5f)
                    val liftScale = 1f + LIFT_SCALE * liftValue
                    scaleX = stretch * liftScale
                    scaleY = liftScale
                    // 靜止不投影（半透明玻璃會透出來變灰底），抬起才有浮起影
                    shadowElevation = 14.dp.toPx() * liftValue
                    spotShadowColor = shadowSpot
                    ambientShadowColor = shadowAmbient
                    shape = pillShape
                    clip = true
                },
        ) {
            Box(Modifier.fillMaxSize().background(pillBrush).border(1.dp, pillBorder, pillShape))
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = lift.value }.background(liftGlowColor))
        }

        // ---- 標籤層（Android 13+ 掛內容透鏡，shader 全文見 shaders.md）----
        val lensShader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            remember { RuntimeShader(PILL_LENS_SHADER) }
        } else {
            null
        }
        val lensModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && lensShader != null) {
            Modifier.graphicsLayer {
                val l = leftEdge.value
                val r = rightEdge.value
                val liftValue = lift.value
                val liftScale = 1f + LIFT_SCALE * liftValue
                val stretch = ((r - l) / pillWidthPx).coerceIn(0.75f, 1.5f)
                lensShader.setFloatUniform("uCenter", (l + r) / 2f, size.height / 2f)
                lensShader.setFloatUniform(
                    "uHalf",
                    pillWidthPx / 2f * stretch * liftScale,
                    size.height / 2f * liftScale,
                )
                lensShader.setFloatUniform("uZoom", 0.10f + 0.22f * liftValue)
                lensShader.setFloatUniform("uChroma", liftValue)
                renderEffect = RenderEffect
                    .createRuntimeShaderEffect(lensShader, "content")
                    .asComposeRenderEffect()
            }
        } else {
            Modifier
        }
        Row(
            modifier = Modifier.fillMaxSize().zIndex(1f).then(lensModifier),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, label ->
                SwitcherItem(
                    label = label,
                    selected = index == selectedIndex,
                    pressed = index == pressedIndex && !dragging,
                    onSelect = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ---- 手勢層（蓋最上面）----
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f)
                .pointerInput(items.size, itemWidthPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val slop = viewConfiguration.touchSlop
                        pressedIndex = indexAt(down.position.x)
                        var lifted = false
                        var lastHovered = -1
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val x = change.position.x
                                if (!lifted &&
                                    (change.uptimeMillis - down.uptimeMillis >= LIFT_HOLD_MS ||
                                        abs(x - down.position.x) > slop)
                                ) {
                                    lifted = true
                                    lastHovered = indexAt(x)
                                    dragCenter = x.coerceIn(minCenter, maxCenter)
                                    dragging = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                if (lifted && change.positionChanged()) {
                                    dragCenter = x.coerceIn(minCenter, maxCenter)
                                    val hovered = indexAt(dragCenter)
                                    if (hovered != lastHovered) {
                                        lastHovered = hovered
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    change.consume()
                                }
                                if (change.changedToUpIgnoreConsumed()) {
                                    if (lifted) {
                                        val target = indexAt(dragCenter)
                                        dragging = false
                                        if (target != selectedIndex) onSelect(target)
                                    } else {
                                        onSelect(indexAt(down.position.x))
                                    }
                                    break
                                }
                            }
                        } finally {
                            pressedIndex = -1
                            // 手勢被系統攔走：不切換，dragging 復位讓滑移 effect 收回原位
                            if (dragging) dragging = false
                        }
                    }
                },
        )
    }
}

@Composable
private fun SwitcherItem(
    label: String,
    selected: Boolean,
    pressed: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "item-press-scale",
    )
    // 觸控由上層手勢層獨佔，TalkBack 靠 semantics 補
    val isSelected = selected
    Box(
        modifier = modifier
            .fillMaxHeight()
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = isSelected
                onClick { onSelect(); true }
            }
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
        )
    }
}
```

## LiquidSegmented（輕量版：只要滑動指示、不需要拖曳）

**全文在 [assets/LiquidSegmented.kt](../assets/LiquidSegmented.kt)，直接複製使用**；
本節是設計解說。適合設定頁的分段開關、預設值膠囊列等。三件套 modifier：

1. 容器掛 `.liquidSegmentIndicator(seg, fill = …)`（畫在選項後面）
2. 每個選項掛 `.liquidSegmentItem(seg, index)`（回報座標）
3. `LiquidSegmentAnimation(seg, selectedIndex)` 驅動（-1＝無選中，指示淡出）

核心要點（完整實作直接照這些規則寫）：

- 狀態持有 `leftEdge`/`rightEdge` 兩個 `Animatable` ＋ `alpha`，
  選項位置用 `onGloballyPositioned` 實測——**選項寬度不必相等**。
- 【雷】選項座標一律用 `container.localBoundingBoxOf(item)` 換算到容器座標系：
  選項回報的是 padding「之後」的內縮空間、繪製發生在 padding「之前」的外框空間，
  直接拿 `positionInParent` 會差一個內距（症狀：指示藥丸整體偏移）。
- 滑移彈簧與完整版同一組：lead 0.68/900、trail 0.85/380。
- 指示畫在 `drawBehind`（動畫值只在繪製階段讀）；漸層藥丸用 `drawRoundRect(brush, alpha = a)`。
- 首次出現（或從無選中回來）：`snapTo` 直接就位再淡入，不要從外太空滑進來。
