# compose-liquid-glass

[English](README.md) | **繁體中文**

在 Jetpack Compose 做出 **Liquid Glass 風格**（液態玻璃）UI——真背景模糊、邊緣折射、
按住可拖的果凍藥丸——打包成 [Claude Code skill](https://docs.anthropic.com/en/docs/claude-code)。

這是對 iOS 26 Liquid Glass 的風格致敬（近似實作），不是 Apple 材質系統的複刻——
涵蓋範圍見下方誠實聲明。

- **真背景模糊玻璃面板**（[Haze](https://github.com/chrisbanes/haze)）＋ AGSL 邊緣折射／Fresnel 高光
- **半透明漸層藥丸滑動切換**——前緣快後緣慢的雙彈簧，行進中拉伸的果凍感
- **內容透鏡**——藥丸底下的文字被放大、邊緣拉伸塗抹、跨過玻璃邊界時真 RGB 色散
- **按住拖曳**——抬起放大＋浮起影＋色散全開、1:1 跟手、跨格觸覺、放開吸附（120Hz 實機打磨）
- **雷區診斷表**——深灰髒塊、中段陰影帶、120Hz 拖曳凍結等實戰踩過的坑，附症狀→真兇→修法

## 安裝（Claude Code）

```bash
git clone https://github.com/kizaki-R/compose-liquid-glass ~/.claude/skills/compose-liquid-glass
```

裝好之後，在任何 Compose 專案裡跟 Claude 說「做一個液態玻璃底部導覽列」之類的需求，
skill 會自動觸發。

## 不用 Claude 也能用

`assets/` 裡是四個**可直接編譯**的 Kotlin 檔，複製進專案、改個 package 就能用：

| 檔案 | 內容 |
|---|---|
| `assets/PillLensShader.kt` | 內容透鏡 AGSL shader |
| `assets/LiquidGlassSurface.kt` | 折射 shader＋玻璃容器（Backdrop / Highlight / Container） |
| `assets/LiquidGlassPillSwitcher.kt` | 完整切換器（點擊＋按住拖曳＋透鏡＋觸覺） |
| `assets/LiquidSegmented.kt` | 輕量分段滑動指示（三件套 modifier） |

接線方式與設計解說見 [`SKILL.md`](SKILL.md) 與 [`references/`](references/)。

## 跟 Apple 原版的差距（誠實聲明）

| | Apple iOS 26 Liquid Glass | 本專案 |
|---|---|---|
| 折射 | 即時光學透鏡，背景在整個玻璃體內被扭曲 | 模糊＋**邊緣帶**取樣位移（近似） |
| 高光 | 隨陀螺儀動態游走的鏡面反光 | 固定偏頂部的 Fresnel（靜態） |
| 適應性 | 即時分析背景內容調整明暗/染色 | 淺色/深色兩套固定配方 |
| 形變 | 元件間液態融合/分裂（morphing） | 藥丸滑移拉伸（果凍感） |
| 文字 | 自動 vibrancy 保對比 | 固定色 |

目標是用兩支 AGSL shader 的成本拿到觀感的絕大部分，而不是完整複刻系統級材質引擎。

## 需求

- Jetpack Compose（runtime 1.7+）＋ [Haze](https://github.com/chrisbanes/haze) 1.7.2
- AGSL 效果需要 Android 13（API 33+）；minSdk 26 起可用（自動降級成無折射的半透明玻璃）

## License

[MIT](LICENSE)
