---
name: compose-liquid-glass
description: 在 Jetpack Compose 做出 iOS 26 風格的 Liquid Glass（液態玻璃）UI——真背景模糊玻璃面板、邊緣折射與 Fresnel 高光、半透明漸層藥丸滑動切換（雙彈簧果凍感）、內容透鏡放大與真 RGB 色散、按住拖曳 1:1 跟手。只要使用者提到 liquid glass、液態玻璃、玻璃擬態（glassmorphism）、毛玻璃/磨砂玻璃模糊、iOS 風格玻璃 bar、滑動藥丸切換、frosted glass、backdrop blur，或想在 Android/Compose 重現 iOS 的玻璃質感，就用這個 skill——即使他們沒有明講「玻璃」而只是形容「半透明會折射的高級感面板」也適用。內含完整可複製的 AGSL shader 與元件程式碼，以及實戰踩過的雷區（深灰髒塊、中段陰影帶、120Hz 拖曳凍結等）的診斷法。
---

# Compose Liquid Glass

在 Jetpack Compose 做出 iOS 26 Liquid Glass「風格」的實用配方——是風格致敬的近似實作
（模糊＋邊緣折射＋透鏡＋果凍藥丸），不是 Apple 材質引擎的複刻（沒有陀螺儀反光、
背景自適應、元件 morphing）。所有程式碼都經過實機打磨（含 120Hz 裝置），可直接複製使用。

## 依賴與相容性

```kotlin
// libs.versions.toml
haze = "1.7.2"   // dev.chrisbanes.haze:haze —— 即時背景模糊
```

- **Haze 1.7.2** 直接相容 Compose runtime 1.7+（BOM 版號是空頭支票，看實際傳遞依賴）。
- **AGSL RuntimeShader 需要 Android 13（API 33）**。所有 shader 效果都必須寫降級路徑：
  無 shader → `clip(shape)` ＋高光層撐場；Haze 無法模糊 → `fallbackTint` 實色。
- minSdk 26 起可用（降級後仍是好看的半透明玻璃，只是少了折射與透鏡）。

## 核心心智模型：三層疊

玻璃感不是一個效果，是三層各司其職疊出來的。缺一層就只是「會動的半透明色塊」：

```
┌─ 3. 內容層 ─────────────────────────┐   藥丸 + 標籤/圖示 + 內容透鏡 shader
├─ 2. 高光描邊 ───────────────────────┤   1px 漸層描邊 + sheen，「刻意不進折射 shader」保持銳利
└─ 1. 玻璃背景 ───────────────────────┘   Haze 背景模糊 + AGSL 邊緣折射/Fresnel
```

**兩支 shader 吃的東西不同，永遠別搞混**：

| Shader | 吃什麼 | 做什麼 | 何時需要 Haze |
|---|---|---|---|
| `LIQUID_GLASS_SHADER` | Haze 畫好的**模糊背景** | SDF 膠囊裁切＋邊緣折射＋RGB 微色散＋Fresnel | 需要 |
| `PILL_LENS_SHADER` | 元件**自己的內容** | 藥丸範圍內放大拉伸＋rim 真色散 | **不需要** |

→ shader 全文與 uniforms 餵法：**[references/shaders.md](references/shaders.md)**

## 先複製 assets/，不要從 markdown 抄程式碼

`assets/` 有四個**打磨完成、可直接編譯**的 .kt 檔。實作時把需要的檔案整個複製進
專案（建議放獨立的 ui/glass/ 或 library module），只改檔頭的 package，其餘不要重打
——手抄 shader 與手勢狀態機是抄寫錯誤的最大來源：

| 檔案 | 內容 |
|---|---|
| `assets/PillLensShader.kt` | 內容透鏡 AGSL（`PILL_LENS_SHADER`） |
| `assets/LiquidGlassSurface.kt` | 折射 AGSL＋`LiquidGlassBackdrop`/`Highlight`/`Container` |
| `assets/LiquidGlassPillSwitcher.kt` | 完整切換器（點擊＋按住拖曳＋透鏡＋觸覺） |
| `assets/LiquidSegmented.kt` | 輕量分段指示三件套（含漸層 Brush 版） |

references/ 是解說與接線指南；只有在需要「改動配方本身」時才對照它們理解每一段為什麼這樣寫。

## 選型：場合 → 配方

| 場合 | 用什麼 | 複製哪個檔 | 解說 |
|---|---|---|---|
| 玻璃面板/懸浮 bar（要背景模糊） | `LiquidGlassContainer`（或自己疊 Backdrop＋Highlight） | `LiquidGlassSurface.kt`＋`PillLensShader.kt` | [glass-surface.md](references/glass-surface.md) |
| 完整切換器（點擊＋按住拖曳＋透鏡） | `LiquidGlassPillSwitcher` | 上面兩個＋`LiquidGlassPillSwitcher.kt` | [pill-switcher.md](references/pill-switcher.md) |
| 輕量分段切換（等寬/不等寬選項、只要滑動指示） | `LiquidSegmented` 三件套 modifier | `LiquidSegmented.kt` | [pill-switcher.md](references/pill-switcher.md) 後半 |
| 玻璃壞掉了（深灰髒塊、陰影帶、拖曳凍住…） | 先查雷區表再改 code | — | [pitfalls.md](references/pitfalls.md) |

## 快速上手（玻璃 bar 三步）

```kotlin
val hazeState = rememberHazeState()

// 1. 餵模糊來源。【雷】hazeSource 只捕捉 modifier 鏈上比它「內層」的繪製：
//    .hazeSource(state).background(bg)   ✓ 背景被捕進玻璃
//    .background(bg).hazeSource(state)   ✗ 捕到空內容 → 玻璃拿到透明 →
//                                          AGSL 把透明黑 premultiply 成不透明深灰髒塊
Box(Modifier.fillMaxSize().hazeSource(hazeState).background(bg))          // 背景 z0
Column(Modifier.fillMaxSize().hazeSource(hazeState, zIndex = 1f) /*…*/)  // 內容 z1

// 2. 玻璃容器（模糊＋折射＋高光一次到位）
LiquidGlassContainer(hazeState = hazeState, isDark = isDark) {
    // 3. 互動元件
    LiquidGlassPillSwitcher(items = listOf("A", "B"), selectedIndex = i, onSelect = { … })
}
```

## 藥丸的皮（別做成 Material 按鈕）

新手最常見的錯誤是把選中藥丸畫成**實心主題色＋白字**——那是 Material 按鈕，不是玻璃。
正確配方（淺色模式）：

- 藥丸填色：`verticalGradient(primary 0.22 → primary 0.13)`（半透明，背景透得過去）
- 藥丸描邊：`verticalGradient(White 0.90 → primary 0.16)`（上亮下淡的高光邊）
- 深色模式：填色 `White 0.20 → 0.10`、描邊 `White 0.32 → 0.05`
- **選中內容變主題色**（primary），未選 `onSurface 0.72`——文字顏色變、不是放白字
- **靜止不投影**：半透明玻璃會把自己的陰影透出來變灰底；只有拖曳抬起時才給
  `shadowElevation`（乘上 lift 值）

## 動效手感（數字都是實機調出來的，成對出現）

- **滑移**：前緣硬彈簧（damping 0.62 / stiffness 900）先衝、後緣軟彈簧（0.85 / 340）拖行
  → 行進中藥丸自然拉長、落定回彈。
- **拖曳跟手**：逐幀指數逼近——前緣 `1-exp(-dt·42)`、後緣 `1-exp(-dt·16)`，
  每幀 `snapTo` 一次。
- **抬起**：按住 160ms 或滑過 touchSlop → scale 1.18×、shadowElevation 14dp×lift、
  透鏡 uZoom/uChroma 全開；跨格觸覺 tick；放開吸附最近格。
- 同一個 App 裡有多個藥丸元件時，**這些常數要全域一致**——兩種手感並存會顯得廉價。

## 效能鐵則（違反的症狀是 120Hz 實機凍住瞬移，模擬器測不出來）

1. **動畫值只准在 `graphicsLayer` / `drawBehind` 的 lambda 裡讀**。
   動畫幀只走繪製階段——零重組、零排版。把寬度/位移算在 composition 裡會每幀重組整條 bar。
2. **拖曳禁止「每事件重啟彈簧」**。120Hz 下觸控事件比彈簧第一幀還密，彈簧永遠跑不完
   就被取消重啟 → 畫面凍住然後瞬移。正解：手勢層只寫目標值（`dragCenter`），
   移動交給單一 `withFrameNanos` 逐幀迴圈消化。
3. 手勢用**單一 `pointerInput` 狀態機**蓋在最上層，選項本身不掛 `clickable`
   （TalkBack 用 semantics `onClick` 補）。

## 視覺除錯 SOP

玻璃的視覺 bug 用肉眼猜圖層會猜錯（實戰盲修過四輪）。順序：

1. **先取證再改 code**：截圖或錄影抽格（ffmpeg），對爭議區域做對比拉伸＋像素剖面掃描。
2. 看**幾何形狀**認圖層：全寬羽化帶＝光照/漸層層；貼邊暈＝描邊或陰影；
   均勻深灰＝模糊來源沒接到（見 pitfalls 第一條）。
3. 對照 [references/pitfalls.md](references/pitfalls.md) 的症狀表，每一條都附真兇與修法。
