# 雷區表（每一條都是實機踩出來的）

按「症狀」查。改 code 前先取證（截圖/錄影抽格＋對比拉伸＋像素剖面），
用瑕疵的**幾何形狀**指認圖層，不要肉眼猜。

## 症狀 → 真兇 → 修法

### 玻璃是不透明深灰髒塊
- **真兇**：`hazeSource` 鏈位錯——`.background().hazeSource()` 的 background 在捕捉範圍外，
  Haze 捕到空內容 → 玻璃拿到透明 → `LIQUID_GLASS_SHADER` 把透明黑 premultiply 成近黑。
- **修法**：`.hazeSource().background()`；或把 hazeSource 掛在「會自己畫內容的元件」外層。
- **判別法**：同一台裝置上別的玻璃元件正常、只有這顆深灰 → 一定是來源接線，不是 shader。

### 玻璃中段一條全寬羽化「陰影帶」
- **真兇**：淺色模式 Fresnel 太強。邊光只照得到貼邊 ~20dp，中段照不到，
  被上下亮帶對比成暗帶。
- **修法**：淺色 fresnel 壓到 0.10（深色可以 0.22）。

### 玻璃中下段像夾了一層灰（亮度三明治 255→247→255）
- **真兇**：sheen 高光只鋪上半部；或玻璃 tint 太低讓模糊背景的灰藍透出來。
- **修法**：sheen 鋪滿全高（上強下弱：0.10 → 0.08 → 0.09）；淺色 tint 用純白 0.45。

### 玻璃 bar 底下像多墊了一層深色容器
- **真兇**：半透明玻璃把「自己的陰影」透出來了。
- **修法**：玻璃 bar 本體不畫陰影；藥丸只在拖曳抬起時投影（`shadowElevation × lift`）。
  真的要 bar 陰影：獨立投影層下移＋內縮，並把膠囊輪廓從陰影 clip 掉。

### 拖曳時藥丸凍住不動、然後瞬移（只在實機出現，模擬器正常）
- **真兇**：每個觸控事件取消並重啟彈簧。120Hz 下事件比彈簧第一幀密，彈簧永遠跑不完。
- **修法**：手勢層只寫目標值（`dragCenter`），移動交給單一 `withFrameNanos` 逐幀迴圈，
  每幀 `snapTo` 一次。

### 拖曳/滑動時整條 bar 卡頓
- **真兇**：動畫值在 composition 讀（每幀重組整條 bar）。
- **修法**：`leftEdge`/`rightEdge`/`lift` 只准在 `graphicsLayer` / `drawBehind` 的 lambda 讀。

### 分段指示藥丸整體偏移（左上偏一截）
- **真兇**：選項回報座標在 padding「之後」的內縮空間、繪製在 padding「之前」的外框空間。
- **修法**：`container.localBoundingBoxOf(item, clipBounds = false)` 精準換算，
  不要用 `positionInParent`。

### 玻璃底下的捲動內容「只有 bar 裡看得到、周圍死白」
- **真兇**：在 bar 底下鋪了不透明漸層 scrim，把模糊的來源蓋掉了。
- **修法**：拿掉 scrim，分隔交給模糊本身。

### lint 報一排 NewApi error
- **真兇**：`RuntimeShader` 的 API 33 條件抽成變數，lint 看不懂「shader 非空蘊含 API 33」。
- **修法**：`if (Build.VERSION.SDK_INT >= TIRAMISU && shader != null)` 直接寫在使用處。

### 選中藥丸看起來像 Material 按鈕、沒有玻璃感
- **真兇**：藥丸畫成實心主題色＋白字。
- **修法**：半透明漸層（primary 0.22→0.13）＋白高光描邊；**文字變主題色**而不是放白字。

### 兩個玻璃元件手感不一致、顯得廉價
- **真兇**：彈簧/LIFT 常數各調各的。
- **修法**：全 App 共用同一組常數（lead 0.62/900、trail 0.85/340、追手 42/16、
  LIFT_HOLD 160ms、LIFT_SCALE 0.18）；調參數時所有用到的元件一起看。

### 元件掛了 Modifier.shadow 之後，描邊/外框莫名消失或變淡
- **真兇**：`shadow()` 會建立自己的裁切圖層，把之後畫的 border 外緣吃掉。
- **修法**：常態 `shadowElevation = 0`，只在需要時（抬起/按壓）才給值；
  或把描邊畫在 shadow 圖層之外（分開兩層）。判別法：把 shadow 拿掉描邊就回來 → 就是它。

### 捲動列表的最後一項永遠被玻璃 bar 壓住
- **真兇**：內容沒有為懸浮 bar 預留空間（bar 是 overlay，不佔版面）。
- **修法**：列表底部加 `Spacer`/contentPadding ≈ bar 高度＋間距（例如 bar 46dp → 留 96dp）。
  注意這跟「不要鋪 scrim」是同一組版面問題的兩半：**留 padding、不擋模糊**。

## 取證工具

```bash
# 錄影抽格（8fps 足夠看清滑移過程）
ffmpeg -i input.mp4 -vf "fps=8" frames/frame_%04d.jpg
# 裁出 bar 區域拼接對比表
ffmpeg -i input.mp4 -vf "fps=10,crop=in_w:200:0:<bar_y>,tile=5x16" -frames:v 1 sheet.png
```

對比拉伸看幾何：全寬羽化帶＝光照層、貼邊暈＝描邊/陰影、均勻深灰＝來源沒接到。
