# 项目开发与迭代文档 (Vitality Compose)

---

## 迭代记录：2026-10-02 - 集成 compose-liquid-glass Skill

### 1. 【本次修改范围】
- **新增目录与文件**：
  - `.agents/skills/compose-liquid-glass/`：安装并集成 `compose-liquid-glass` 专属 Skill。
    - `SKILL.md`：Skill 的核心定义与调度配置（包含 YAML Frontmatter 元数据：名称、触发条件、iOS 26 Liquid Glass 风格配方心智模型、使用指引）。
    - `README.md` / `README.zh-TW.md` / `LICENSE`：说明文档及开源许可。
    - `assets/`：
      - `LiquidGlassPillSwitcher.kt`：全功能液态玻璃药丸滑动切换器（点击、拖曳跟手、双弹簧果冻感、透镜色散）。
      - `LiquidGlassSurface.kt`：液态玻璃表面容器（折射 AGSL + 背景模糊 + 高光描边）。
      - `LiquidSegmented.kt`：轻量级分段指示器修饰符体系。
      - `PillLensShader.kt`：内容透镜放大与色散 AGSL Shader。
    - `references/`：
      - `glass-surface.md`：玻璃面板/悬浮 Bar 接入指南与参数说明。
      - `pill-switcher.md`：药丸切换器接入与手势状态机解析。
      - `pitfalls.md`：踩坑与视觉排错 SOP（脏色块、120Hz 冻结、阴影异常等）。
      - `shaders.md`：AGSL Shader 完整 Uniforms 喂法与实现细节。
  - `docs/DEV_LOG.md`：初始化项目开发沉淀日志体系。

### 2. 【架构与设计变更】
- **Agent Skill 体系集成**：
  - 在项目根目录创建标准 `.agents/skills/` 规范目录，接入针对 Jetpack Compose + AGSL 的液态玻璃渲染与交互 Skill。
  - 该 Skill 使 AI Agent 在后续进行 UI 研发、视觉重构、毛玻璃/折射拟态效果开发时能够自动按需加载配方，指导高保真玻璃视觉实现。
- **分层渲染心智模型**：
  - 玻璃背景层：`Haze` 实时背景模糊 + AGSL 边缘折射/Fresnel 高光。
  - 高光描边层：1px 渐变描边与 Sheen 光泽，独立于折射 Shader 保持锐利。
  - 交互内容层：药丸容器 + 标签/图标 + 独立内容透镜 Shader (`PILL_LENS_SHADER`)。

### 3. 【开发遇到的问题 & 踩坑记录】
- **Git 子模块与嵌套仓库冲突**：
  - **问题**：直接在项目子目录下 `git clone` 容易将远端仓库的 `.git` 元数据带入，导致主仓库将其识别为未受控的子模块（submodule）。
  - **解决方案**：在拉取远端 Skill 内容后，自动清理其内部 `.git` 目录，使其作为纯净的 Workspace Skill 文件树纳入主项目的代码版本控制中。

### 4. 【关键决策理由】
- **安装位置选型（Workspace 级 `.agents/skills/`）**：
  - 为什么选择项目级 `.agents/skills/` 而非全局目录 `~/.gemini/config/`：
    - Vitality 是专注于 Compose 现代 UI 的 Android 应用，将此 Skill 随项目代码一起版本化管理，有助于团队协作和项目级专属上下文复用。
- **完整保留 assets/ 与 references/ 子目录**：
  - 避免仅保留单一 `SKILL.md`，通过完整保留打磨完毕的 `.kt` 源码和故障排查清单，方便在后续开发中直接引用经过 120Hz 调优的手势状态机与 AGSL 源码。

---

## 迭代记录：2026-10-02 - 全面重构 UI 为苹果 iOS 26 液态玻璃风格 (Liquid Glass)

### 1. 【本次修改范围】
- **修改文件**：
  - `app/src/main/java/com/jiang/vitality/ui/LiquidGlass.kt`：
    - 新增 `LIQUID_GLASS_SHADER` (AGSL)：实现 SDF 胶囊圆角裁切、平滑边缘抗锯齿 (1.2px)、向心折射偏移 (向内位移与二次衰减)、RGB 真实色散通道分离、Fresnel 顶部偏向光与 1-2px 贴边高光。
    - 新增 `PILL_LENS_SHADER` (AGSL)：针对交互药丸与内容层实现各向异性透镜放大与边缘真色散效果。
    - 新增/重写 `LiquidGlassBackdrop`、`LiquidGlassHighlight`、`LiquidGlassContainer`、`LiquidGlassSurface` 与 `GlassCard`，支持实时 Haze 背景模糊与 RenderEffect Shader 折射；
    - 重写 `GlassButton` 与 `GlassOutlinedButton`：摆脱传统 Material 实色按键设计，改用半透明垂直渐变底色 (Primary 0.22 → 0.13)、顶部强白光渐变边框 (White 0.90 → Primary 0.16)、主题色前景文本与物理弹性按压响应。
    - 升级 `glassTextFieldColors` 与玻璃弹窗样式。
  - `app/src/main/java/com/jiang/vitality/ui/navigation/JiangNavigationBar.kt`：
    - 导航外壳全面接入 `LiquidGlassShell` 动态模糊与折射；
    - 选中项升级为苹果液态玻璃药丸 (Liquid Glass Pill)：采用前硬 (damping 0.62, stiffness 900) 后软 (damping 0.85, stiffness 340) 双弹簧驱动，滑动时产生自然的果冻拉伸形变；
    - 导航文字与图标层接入 `PILL_LENS_SHADER`，在 Android 13+ 上经过玻璃药丸时产生真实的光学放大与 RGB 色散。
  - `app/src/main/java/com/jiang/vitality/MainActivity.kt`：
    - 优化背景渲染层级：将 `hazeSource(hazeState)` 明确绑定在包含动态几何背景 `DynamicGeometryBackground` 与 `GlassBackdropSource` 的底层容器上，杜绝因捕获空内容而产生的深灰色预乘脏块。
  - `app/src/main/java/com/jiang/vitality/ui/HomeScreen.kt`：
    - 引入 `border` 修饰符，将 Live Vitality / Recovery 模式指示器升级为微型液态玻璃胶囊。
- **新增文件**：
  - `app/src/main/java/com/jiang/vitality/ui/LiquidSegmented.kt`：轻量级液态分段指示器修饰符体系与全局坐标换算状态机。
  - `app/src/main/java/com/jiang/vitality/ui/LiquidGlassPillSwitcher.kt`：全功能独立液态玻璃药丸切换器（含 120Hz 逐帧指数逼近、长按 160ms 抬起放大 1.18x、触觉反馈与色散透镜）。

### 2. 【架构与设计变更】
- **分层渲染模型实现 (Three-Layer Stacking Architecture)**：
  - **层 1：玻璃背景层 (Glass Backdrop)**：`Haze` 实时背景模糊采样 + AGSL `LIQUID_GLASS_SHADER` 边缘折射与 Fresnel 边光。浅色模式下 `glassTint` 设为 `Color.White.copy(alpha = 0.45f)`，避免中段出现夹灰暗带。
  - **层 2：高光描边层 (Specular Highlight)**：1px 垂直渐变描边与 Sheen 光泽，**刻意不进入折射 Shader**，确保边框与光斑维持 1px 极细锐利度。
  - **层 3：交互内容透镜层 (Content Lens)**：独立于背景的 `PILL_LENS_SHADER`，仅作用于药丸与标签文字，随药丸位置动态改变 Shader Uniforms。
- **动效手感升级 (Liquid Jelly Springs & 120Hz Tracking)**：
  - 弃用传统的单一动画过渡，采用成对的非对称弹簧（前缘 Hard Spring 冲刺、后缘 Soft Spring 拖拽跟随），在位移中拉伸、在落定点回弹；
  - 统一全 App 药丸动效物理常数，保持手感一致性。

### 3. 【开发遇到的问题 & 踩坑记录】
- **1. Haze 模糊源捕获顺序与深灰脏块 (Muddy Dark Gray Blocks)**：
  - **问题**：若 `hazeSource` 放在 `background` 之后或包裹了透明背景，Shader 采样到透明像素，AGSL 将 Premultiplied Alpha 采样转换为不透明深灰脏块。
  - **解决**：调整 `MainActivity.kt` 布局结构，确保底层 `Box` 包含底色与背景动态图形后再交由 `hazeSource` 捕获。
- **2. 高光边框中段阴影带问题 (Fresnel Shadow Band Trap)**：
  - **问题**：当高光仅照射顶部或 Fresnel 系数过大时，在浅色模式下对比拉伸会导致玻璃中下段看起来像夹层阴影。
  - **解决**：高光层采用三段式垂直渐变全高铺满（上强 0.10、中微弱 0.08、下 0.09），Fresnel 增益压低至 0.10f。
- **3. BoxWithConstraints 中 maxWidth 隐式接收者与 GraphicsLayerScope.shape 冲突**：
  - **问题**：在嵌套的 Compose 作用域内，`maxWidth` 与外部 `val shape` 产生作用域遮蔽与编译报错。
  - **解决**：改用 `this@BoxWithConstraints.maxWidth` 显式解构，并在 `graphicsLayer` 内部使用 `this.shape = pillShape` 准确赋值。

### 4. 【关键决策理由】
- **纯正 Liquid Glass 材质替代传统实色 Material 按钮**：
  - 玻璃的核心在于半透明穿透与光线折射，将所有操作按钮重构成透亮渐变底 + 高光描边 + 主题色文字，避免了“色块式”的廉价感。
- **API 33+ AGSL 渐进增强 + 低版本优雅降级 (Graceful Degradation)**：
  - 在 Android 13+ (API 33) 充分发挥 `RuntimeShader` 性能实现实时边缘折射与透镜真色散；
  - 在 API < 33 上自动降级为标准 Clip 裁切 + 高光层描边，保证在低版本 Android 设备上同样拥有通透精致的磨砂玻璃视觉。

---

## 迭代记录：2026-10-02 - 清理导航栏重复图层并校准卡片 Shader 圆角

### 1. 【本次修改范围】
- **修改文件**：
  - `app/src/main/java/com/jiang/vitality/ui/navigation/JiangNavigationBar.kt`：清理了多余的重叠药丸 `Box`，解决新旧药丸双层叠合与视觉覆盖问题，完整保留原有的 `LiquidLightSource` 光源流动体系与 `NavigationItem`。
  - `app/src/main/java/com/jiang/vitality/ui/LiquidGlass.kt`：动态提取 `shape.topStart.toPx(size, density)` 作为 AGSL Shader 的精确 `uRadius`，防止矩形卡片被误裁切为胶囊或出现双层轮廓。

### 2. 【架构与设计变更】
- **图层清理与层级合一**：
  - 移除了在导航栏中额外叠加的药丸容器，避免覆盖原有的 `LiquidLightSource`（动态流光与液滴高光）与 `LiquidBubble`（收缩呼吸气泡）。
  - 修复卡片与面板的 Shader SDF 尺寸匹配模型，确保任意矩形与胶囊圆角均能精准计算边缘折射。

### 3. 【开发遇到的问题 & 踩坑记录】
- **多层高光与药丸叠加冲突**：
  - **问题**：在导航栏注入新药丸时保留了原有 `LiquidLightSource`，导致两套位移与形状图层重叠显示。
  - **解决**：清理额外图层，恢复原有单一架构驱动。
- **卡片边缘 Shader SDF 裁切错位**：
  - **问题**：Shader 原本直接取 `size.height * 0.5f` 导致非正胶囊卡片发生形变。
  - **解决**：改为动态读取 Compose `Shape` 的实际圆角像素 `shape.topStart.toPx(size, density)`。

### 4. 【关键决策理由】
- **无侵入式材质升级**：
  - 尊重原有界面的组件流与状态设计，将液态玻璃材质以无侵入、底层的材质增强方式注入，而非在已有组件上盲目叠盖新图层。

---

## 迭代记录：2026-10-02 - 全面参考 Kyant0/AndroidLiquidGlass 重构液态玻璃引擎与 Backdrop 架构

### 1. 【本次修改范围】
- **新增模块与文件**：
  - `app/src/main/java/com/jiang/vitality/ui/backdrop/`：完整接入 Kyant0 `AndroidLiquidGlass` 的核心渲染与交互架构。
    - `Shaders.kt`：Kyant0 高保真 AGSL Shader 体系：
      - `RoundedRectSDF`（SDF 距离场计算 `sdRoundedRect`、法线梯度场 `gradSdRoundedRect`、4 角独立圆角半径 `radiusAt`）
      - `RoundedRectRefractionWithDispersionShaderString`（真实光学球面透镜映射 `circleMap(x) = 1.0 - sqrt(1.0 - x * x)`、7 波段真实光谱色散采样 Red/Orange/Yellow/Green/Cyan/Blue/Purple、向心折射法线计算与深度拉伸）
      - `DefaultHighlightShaderString`（基于法线点积的定向边缘高光与指数衰减幂次）
      - `AmbientHighlightShaderString`（基于法线台阶函数的环境漫反射高光）
      - `TouchSpotlightShaderString`（基于触控坐标的平滑聚光光斑）
    - `RuntimeShader.kt`：Android 13+ (Tiramisu) `RuntimeShader` 包装与高效全局 Shader 缓存池 (`RuntimeShaderCacheImpl`)。
    - `Backdrop.kt`：`Backdrop` 协议、`LayerBackdrop`（基于 Compose 独立 `GraphicsLayer` 的背景像素采样与相对坐标逆变换 `InverseLayerScope`）、`CombinedBackdrop`、`rememberLayerBackdrop()`、`LocalBackdrop` 组合局部注入。
    - `BackdropEffectScope.kt`：效果链域：`lens()` 透镜折射与 7 色散开关、`blur()` 动态模糊、`vibrancy()` 鲜活度光照滤镜、`RenderEffect.chain()` 链式合成。
    - `DrawBackdropModifier.kt`：核心 `Modifier.drawBackdrop` 与 `Modifier.layerBackdrop` 修饰符节点，实现 Offscreen 离屏合成、视口相对坐标对齐与多层管线渲染（DrawBehind → BackdropLayer → DrawSurface → Content → DrawFront）。
    - `Highlight.kt`：`Highlight`、`HighlightStyle`（`Default`、`Ambient`、`Plain`）与法线高光绘制节点。
    - `Shadow.kt`：`Shadow` 外阴影、`InnerShadow` 内阴影（带反向裁切图形与平滑高斯模糊）。
    - `Shapes.kt`：`Capsule`（物理胶囊形状）与 Compose 任意 `Shape` 圆角尺寸动态解析。
    - `InteractiveHighlight.kt` & `DampedDragAnimation.kt`：触控跟随聚光高光与带速度感知/正切形变的阻尼弹簧拖曳物理动画。
- **修改文件**：
  - `app/src/main/java/com/jiang/vitality/ui/LiquidGlass.kt`：
    - 升级 `LiquidGlassSurface`、`GlassCard`、`GlassButton`、`LiquidGlassBackdrop` 接入 Backdrop 采样引擎与 Kyant0 7-band 色散透镜 Shader；
    - 在 Android 12 及以下保留 Haze + 渐变高光自适应降级；
    - `GlassButton` 接入基于正切衰减 `tanh()` 的触控拖曳拉伸形变与接触点聚光 Shader。
  - `app/src/main/java/com/jiang/vitality/MainActivity.kt`：
    - 在顶层背景容器注入 `rememberLayerBackdrop()` 并通过 `CompositionLocalProvider(LocalBackdrop provides wallpaperBackdrop)` 为全应用玻璃组件提供零开销底图采样源。
  - `app/src/main/java/com/jiang/vitality/ui/navigation/LiquidHighlight.kt`：
    - 升级导航栏游移光源层，在 Android 13+ 上增加 7 波段真实光谱色散透镜与速度拉伸形变 (`scaleX /= 1f - (vel * 0.75f)`, `scaleY *= 1f - (vel * 0.25f)`)。

### 2. 【架构与设计变更】
- **Kyant0 Backdrop 离屏采样架构接入**：
  - 由传统“各组件自画模糊”升级为**全局 Backdrop 图层分发模型**：顶层壁纸与动态气泡绘制在 `LayerBackdrop` 中，上层悬浮的玻璃面板、滑动药丸、滑块 Thumb 通过相对视口坐标 (`localPositionOf`) 无缝采样并应用 AGSL 透镜与色散。
- **物理光学公式与 Shader 计算升级**：
  - **透镜弧度**：采用光学真曲面公式 `circleMap(x) = 1.0 - sqrt(1.0 - x * x)` 替代传统的二次或三次多项式衰减，呈现厚玻璃凸透镜边缘的高级折射感；
  - **真实光谱色散**：由简单的 RGB 3 分色升级为 7 波段连续色散采样（Red/Orange/Yellow/Green/Cyan/Blue/Purple 对应波长权重采样），彻底消除边缘泛灰与塑料感；
  - **法线高光**：基于 SDF 边缘梯度与法线点积 `dot(grad, normal)^falloff` 计算受光面与背光面高光。
- **物理手势反馈与形变**：
  - 引入 Kyant0 `DampedDragAnimation`，在滑动时产生与滑动速度关联的正切形变与接触点聚光高光。

### 3. 【开发遇到的问题 & 踩坑记录】
- **1. Compose `Paint.asFrameworkPaint()` 在 Android 原生 Compose 编译解析报错**：
  - **问题**：在直接使用 `paint.asFrameworkPaint()` 时，因 Kotlin 包导入遮蔽导致无法解析。
  - **解决**：正确引入 `androidx.compose.ui.graphics.*` 并在内部直接调用 framework paint 桥接方法。
- **2. `GraphicsLayerScope` 的 Compose BOM 版本兼容性差异**：
  - **问题**：Compose 部分新版本中 `GraphicsLayerScope` 未直接暴露 `blendMode` 与 `colorFilter` 作为抽象属性，导致 override 报错。
  - **解决**：调整 `InverseLayerScope` 属性修饰，将非接口强制属性作为普通成员处理，保持与 Compose BOM 2025.04.01 的完全兼容。
- **3. `layerBackdrop` 中 ContentDrawScope 闭包调用参数错配**：
  - **问题**：`onDraw: ContentDrawScope.() -> Unit` 在传递时传入了冗余参数。
  - **解决**：统一为无参接收者函数调用 `this@drawWithContent.onDraw()`。

### 4. 【关键决策理由】
- **源码级集成 Kyant0 Backdrop 引擎而非仅添加远程库依赖**：
  - 直接将打磨好的纯 Compose + AGSL 模块植入项目 `com.jiang.vitality.ui.backdrop`，确保 100% 编译稳定性、零外部不可控依赖冲突，并针对 Vitality 项目的动态背景与 Haze 进行针对性混合渲染优化。
- **渐进增强 + 100% 向后兼容**：
  - 在 Android 13+ (API 33+) 启用极致的 7 色散 AGSL + 动态法线高光 + 离屏 Backdrop 采样；在较低版本上无缝回退至 Haze 磨砂玻璃 + 精细描边，保证全版本体验平稳。

---

## 迭代记录：2026-10-02 - 底部导航栏重叠修复与全量 UI 按照 Kyant0/AndroidLiquidGlass 标准组件替换

### 1. 【本次修改范围】
- **彻底解决底部导航栏 UI 重叠问题，全量按照 Kyant0 开源库标准架构重构**：
- **修改与重构文件**：
  - `app/src/main/java/com/jiang/vitality/ui/backdrop/LiquidBottomTabs.kt` & `LiquidBottomTab.kt`：
    - 完整实现 Kyant0 标准 `LiquidBottomTabs` 与 `LiquidBottomTab` 容器。
    - 采用标准三层结构：
      1. 底部玻璃轨道：`drawBackdrop(backdrop)` 渲染 Capsule 胶囊外壳（24dp 透镜折射 + 8dp 模糊 + 动态压感反馈）；
      2. 隐藏着色记录层：`alpha(0f)` + `layerBackdrop(tabsBackdrop)` + `drawWithContent { drawContent(); drawRect(accentColor, blendMode = BlendMode.SrcAtop) }`，将所有 Tab 的 Icon 与文字统一着色并录制到 `tabsBackdrop`；
      3. 悬浮活动透镜滑块：通过 `rememberCombinedBackdrop(backdrop, tabsBackdrop)` 组合采样底图与着色层，在选中 Tab 移动时以 7 波段光谱色散透镜 (`chromaticAberration = true`) + 真实速度拉伸形变 (`scaleX /= 1 - vel*0.75`, `scaleY *= 1 - vel*0.25`) + 内部法线高光与内外阴影，动态透出选中项的高清主题色。
  - `app/src/main/java/com/jiang/vitality/ui/navigation/JiangNavigationBar.kt`：
    - 彻底废除旧版手动在 Canvas 绘制彩色光斑、圆角矩形和多重 Box 导致的图层重叠问题；
    - 改为直接使用 `LiquidBottomTabs` + `LiquidBottomTab` 容器，配合 `navigationDestinations` 渲染；
    - 保留导航栏在滚动收起时平滑 Morph 变成单触点 `LiquidButton` 呼吸气泡的交互。
  - `app/src/main/java/com/jiang/vitality/ui/navigation/LiquidHighlight.kt`：
    - 删除冗余的旧版 `LiquidLightSource` 和 `LiquidHighlightCanvas`，彻底消除导致重叠的多重绘制代码。
  - `app/src/main/java/com/jiang/vitality/ui/SettingsScreen.kt`：
    - 全量替换：将系统原生 `Switch` 替换为标准 `LiquidToggle`（带 Backdrop 背景透镜、阻尼滑动手感、胶囊轨道与活动透镜）；
    - 将系统原生 `Slider` 替换为标准 `LiquidSlider`（带 Backdrop 轨道采样、速度感知形变、7 色散活动 Thumb）。
  - `app/src/main/java/com/jiang/vitality/ui/LiquidGlass.kt`：
    - 升级 `GlassOutlinedButton` 接入 Backdrop 动态采样与法线高光描边，统一全应用按钮在 Android 13+ 上的透镜色散与正切拖曳弹性形变。

### 2. 【架构与设计变更】
- **彻底消除重叠图层（Single Source of Truth 导航渲染模型）**：
  - **旧方案缺陷**：旧版导航栏同时存在 `LiquidGlassShell` 容器背景、`LiquidHighlightCanvas`（手动 Canvas 绘制多段高斯径向光斑与内壁描边）、以及额外的 `Box` 悬浮透镜，导致 3 个不同尺寸和坐标的圆/药丸重叠堆叠在激活项上，产生严重的视觉重叠冲突。
  - **Kyant0 标准架构**：
    - 只有一个外壳：`drawBackdrop(backdrop)`；
    - 只有一个活动透镜：通过局部 `tabsBackdrop` 将“未选中时灰色内容”与“通过透镜看到的彩色内容”进行光学分离。活动透镜移动到哪个 Tab，哪个 Tab 就通过透镜被光学放大、产生色散并显露出主题色，无需在外部额外叠加多个彩色指示器。
- **全量 UI 控件标准统一**：
  - 导航栏（`LiquidBottomTabs`）、开关（`LiquidToggle`）、滑块（`LiquidSlider`）、按钮（`LiquidButton` / `GlassButton` / `GlassOutlinedButton`）、面板（`LiquidGlassSurface` / `GlassCard`）全量统一运行在 `com.jiang.vitality.ui.backdrop` 引擎之上，所有组件共享统一的光学 Shader、法线高光模型、阻尼弹簧手感和 Backdrop 离屏采样树。

### 3. 【开发遇到的问题 & 踩坑记录】
- **1. Compose 版本中 `graphicsLayer(colorFilter = ...)` 参数不存在**：
  - **报错**：`No parameter with name 'colorFilter' found`。
  - **原因**：Kyant0 原库基于 Compose Multiplatform 1.7+，其 `graphicsLayer` 修饰符支持 `colorFilter` 参数；而在 Android Jetpack Compose BOM 体系中，`graphicsLayer` 是通过 lambda 作用域或者 `drawWithContent` 进行像素滤镜处理。
  - **解决方案**：在 `tabsBackdrop` 记录层使用 `Modifier.drawWithContent { drawContent(); drawRect(accentColor, blendMode = BlendMode.SrcAtop) }`，不仅完全兼容 Jetpack Compose 所有版本，而且利用 `SrcAtop` 精准把非透明内容着色为 `accentColor`，确保透镜采样完美呈现。
- **2. 底部栏多层药丸冲突**：
  - **问题**：在用户截图截取处，今日/记录/休息导航栏内部有 3 圈错位的圆环和阴影。
  - **解决方案**：彻底删除 `LiquidHighlight.kt` 内旧的手动 Canvas 绘制函数，完全由 `LiquidBottomTabs` 负责单次渲染。

### 4. 【关键决策理由】
- **100% 采用 Kyant0 标杆实现而非拼接补丁**：
  - 之前的尝试试图在原有组件上打补丁叠加 Shader，导致图层叠加重叠。本次直接全量以 Kyant0 的 `LiquidBottomTabs`、`LiquidSlider`、`LiquidToggle`、`drawBackdrop` 作为标准实现，保证组件间的光学折射逻辑严密自洽。
- **色彩显露机制：通过透镜采样而非状态切换**：
  - 为什么 Kyant0 采用 `tabsBackdrop` + 隐藏着色层：传统导航栏是用 `if (selected) activeColor else inactiveColor` 做颜色切换，而液态玻璃在拖曳滑块经过时，透镜边缘会逐渐把下一个 Tab 的颜色“折射放大出来”，这种流体般的连续过渡是静态颜色切换无法比拟的。

---

## 迭代记录：2026-10-02 - 修复底部栏玻璃质感暗灰、点击不跟手与开关点击无法开启问题

### 1. 【本次修改范围】
- **修复文件**：
  - `app/src/main/java/com/jiang/vitality/ui/backdrop/LiquidBottomTabs.kt`：
    - **去除暗灰脏块，恢复常态通透玻璃质感**：移除滑块未按压时错误填充的 `Color.Black.copy(0.1f)` 脏灰图层；将透镜折射 `lens`、45° 定向法线高光 `Highlight.Default`、白色内发光 `InnerShadow` 和通透纯白底色（`Color.White.copy(0.30~0.45f)`）设为全生命周期常态生效，静止状态下同样呈现清澈通透的 7 色散凸透镜质感。
    - **修复点击 Tab 不触发滑块移动问题**：重构 `targetTabIndex` 响应逻辑，在 `LaunchedEffect(targetTabIndex)` 中直接触发 `dampedDragAnimation.animateToValue`，彻底解决 `remember(lambda)` 与 `drop(1)` 导致点击 Tab 时滑块不位移的 Bug。
    - **增强外壳玻璃视觉**：外壳添加 `Highlight.Ambient` 环境高光、20dp 实时模糊与 16dp 透镜折射。
  - `app/src/main/java/com/jiang/vitality/ui/backdrop/LiquidToggle.kt`：
    - **解决开关关闭状态下点击右侧无法开启问题**：将整个胶囊外壳包裹在 `Modifier.clip(Capsule()).clickable` 统一手势区域中，无论用户点击滑块本身还是点击轨道任意区域（左、中、右），均可瞬间触发切换并播放平滑物理动画；同时保留在滑块上的阻尼拖拽与速度感知形变。
    - **升级开关 Thumb 玻璃质感**：加入 7 波段光谱色散透镜折射、45° 法线高光与白色内壁高光。
  - `app/src/main/java/com/jiang/vitality/ui/LiquidGlass.kt`：
    - 在 `GlassButton` 与 `GlassOutlinedButton` 中显式注入 `CompositionLocalProvider(LocalContentColor provides contentColor)`，确保按钮文字在玻璃面板上保持高饱和主题色与清晰可读性。

### 2. 【架构与设计变更】
- **动态压感模型向常态化光学模型修正**：
  - **旧设计缺陷**：Kyant0 Demo 中将所有透镜参数与 `pressProgress`（按压深度）强绑定，导致在未按压的静态渲染状态下，组件透镜度为 0、高光为 0、内发光为 0，且降级为黑色遮罩，直接造成“暗灰塑料片”的视觉缺陷。
  - **新设计规范**：确立“**常态高保真 + 按压膨胀增强**”的光学渲染标准：
    - 静态未按压：基础折射深度 12dp/16dp，基础高光 0.92，基础内发光 0.40；
    - 动态按压/拖拽：折射深度随 `pressProgress` 动态膨胀至 16dp/24dp，伴随果冻拉伸与触点聚光。
- **手势与状态流（Gestures & State Flow）解耦与闭环**：
  - 将分立的“滑块手势”与“轨道手势”统一提升至组件外层容器，避免因滑块尺寸小于轨道而在极端位置出现“点击盲区”。

### 3. 【开发遇到的问题 & 踩坑记录】
- **1. Compose `remember(lambda)` 造成的状态重置与 Flow 丢帧**：
  - **问题**：在 Compose 中将参数作为 lambda `{ selectedIndex }` 传递时，每次重组都会生成新实例，导致内部 `remember(selectedTabIndex)` 频繁重新初始化状态；配合 `snapshotFlow.drop(1)` 会直接把真实的状态变更当作“初值”丢弃，导致点击无动画响应。
  - **解决**：在 Composable 顶层解构出数值 `val targetTabIndex = selectedTabIndex()`，并通过 `LaunchedEffect(targetTabIndex)` 进行确定性状态监听与动画驱动。
- **2. 开关控件点击穿透盲区**：
  - **问题**：`dampedDragAnimation.modifier` 仅挂载在 30dp 宽的 Thumb 上，轨道宽度为 60dp。当开关处于 OFF 状态时，用户点击右侧 30dp~60dp 区域没有任何响应。
  - **解决**：外层容器增加整体 `clickable`，并在内部同步更新 `fraction` 与 `animateToValue`。
- **3. `HighlightStyle.Ambient` 参数名类型错配**：
  - **问题**：`Ambient` 样式接收 `intensity: Float` 而非 `color: Color`。
  - **解决**：将 `color = ...` 改为 `intensity = ...`，恢复编译通过。

### 4. 【关键决策理由】
- **为什么不使用简单的实色或深色底来强调对比**：
  - 液态玻璃的本质是“**通过物理折射和边缘高光显形，而非靠不透明底色显形**”。去除暗黑色块，改用高透光白玻璃 + 7 色散透镜 + 45° 锐利法线高光，不仅契合 iOS 26 液态玻璃设计语言，而且在浅色与深色背景下都能呈现晶莹剔透的高级感。

---

## 迭代记录：2026-10-02 - 修复底部黑边与玻璃覆盖文字模糊/重影问题

### 1. 【本次修改范围】
- **修改文件**：
  - `app/src/main/java/com/jiang/vitality/ui/backdrop/Shaders.kt`：
    - 修复 `AmbientHighlightShaderString` 的背光面 Shader 渲染漏洞：将原先 `step(0.0, d)` 配合 `half4(t, t, t, 1.0)` 改为 `pow(max(0.0, d), falloff)` 与 Alpha 透明衰减，彻底杜绝在胶囊底部输出不透明黑色的问题，消除了底部黑边。
  - `app/src/main/java/com/jiang/vitality/ui/backdrop/LiquidBottomTabs.kt`：
    - **重构分层渲染架构，彻底根除文字模糊与重影**：
      1. 底层：`Box` 胶囊外壳（动态背景模糊 + 20dp/16dp 球面折射透镜 + 45° 晶莹高光描边）；
      2. 中层：`Box` 悬浮 7 波段真光谱色散液态玻璃药丸透镜，在 Tab 图标与文字下方滑动，折射底图与壁纸，具备动态果冻拉伸与白玻璃内发光；
      3. 顶层：`Row` 矢量 Tab 标签内容层。文字与图标直接置于最上层绘制，不再被上层 16px 色散透镜进行重复采样和色散通道分裂，彻底实现 **100% 矢量清晰、零模糊、零重影、零色差虚焦**。
  - `app/src/main/java/com/jiang/vitality/ui/navigation/JiangNavigationBar.kt`：
    - 激活 Tab 的图标与文字自动切换为亮眼的高饱和主题色（`accentColor`），非激活 Tab 保持沉稳优雅的 `palette.ink.copy(alpha = 0.62f)`，在液态玻璃滑块滑动经过时形成高对比度、清晰通透的苹果设计视觉。

### 2. 【架构与设计变更】
- **图层渲染分级重构（Separation of Optics & Typography）**：
  - **旧方案缺陷**：原先试图通过 AGSL 透镜直接作用在文字所在的整个 Row 上，并在多个 Backdrop 之间对文字像素进行色散折射。由于 7 色散算法会将像素强制在 ±16px 范围内向 7 个波长方向拉开距离，小字号字体（11sp）必然产生严重的多彩边缘色散重影（Chromatic Blur），导致文字发虚不可读。
  - **新架构规范**：
    - **光学层（Optics Layer）在下**：负责折射壁纸与应用背景，呈现厚重液态玻璃的折射、色散、光斑和果冻形变。
    - **排版层（Typography Layer）在上**：文字与图标处于最顶层，直接以抗锯齿矢量形式绘制在玻璃滑块上方。玻璃滑块从下方划过时烘托文字，文字本身保持 100% 锐利。

### 3. 【开发遇到的问题 & 踩坑记录】
- **1. Shader 背光面输出纯黑导致底部黑边（Backside Shader Output Trap）**：
  - **问题**：在法线高光 Shader 中使用了 `step(0.0, d)` 配合 `half4(t, t, t, 1.0)`，当法线夹角大于 90° 时 `t=0`，Shader 输出了 RGB=0、Alpha=1 的实心黑，导致整个下半圈边缘出现一圈黑线。
  - **解决**：改用法线夹角钳位 `max(0.0, d)`，仅对正向受光面叠加高光，背光面输出 Alpha=0 的透明像素。
- **2. 强色散 Shader 叠加小字号文本产生色差虚焦（Chromatic Aberration on Small Text）**：
  - **问题**：强色散透镜（Chromatic Aberration）适用于大色块与背景模糊，一旦直接叠加在 11sp 的小字体上，RGB 通道分裂会将文字笔画拉出彩虹边缘，造成肉眼严重模糊。
  - **解决**：将文字与图标移至色散透镜之上，由玻璃滑块在下方滑动衬托，保证视觉质感与文字可读性两全其美。

### 4. 【关键决策理由】
- **文字可读性优先原则（Readability First in Liquid Design）**：
  - 苹果 iOS 的液态材质原则是“面板折射背景，前景文字清晰”。文字是 UI 的核心传达介质，绝不应为了盲目追求透镜折射而牺牲字体的清晰度。将透镜保留在滑块背景，文字浮于表面，既拥有 100% 的液态物理折射，又拥有极致清晰的字体排版。

---

## 迭代记录：2026-10-02 - 切换到官方 Backdrop 依赖

- 增加 Maven Central 依赖 `io.github.kyant0:backdrop:1.0.6`，核心 Backdrop、模糊、透镜、色彩增强、高光与阴影均改为调用官方 `com.kyant.backdrop` API，不再维护项目内的核心渲染分叉。
- 保留 Vitality 自己的高层组件（卡片、按钮、滑杆、开关和底部导航）；这些组件负责产品视觉与交互，底层光学效果交由 Backdrop 渲染。
- 将 Kotlin/Compose 编译插件升级到 `2.3.10`，`compileSdk` 升级到 36，并迁移到 `compilerOptions` DSL，以满足 Backdrop 1.0.6 的发布要求。
- 兼容策略：Android 13 及以上使用 Backdrop 的动态背景采样、色散透镜与法线高光；较低系统版本继续使用 Haze/透明渐变作为可读性良好的降级效果。
- 验证：`:app:assembleDebug` 与 `debugRuntimeClasspath` 依赖解析均通过，产出 `app-debug.apk`。

---

## 迭代记录：2026-10-02 - 全局高透明液态玻璃 UI

- 将卡片、页面标题、首页折叠标题、主次按钮、输入框、底部导航、开关和滑杆统一为高透明玻璃材质；主体填充透明度收敛到约 5%–20%，通过背景折射、高光和细边界维持层级。
- 新增通用 `GlassDialog` 与 `GlassActionButton`，替换所有 Material `AlertDialog`、`TextButton` 以及系统 `TimePickerDialog`；提醒时间改为在透明玻璃弹层内使用液态滑杆选择。
- 降低导航外壳、活动透镜、卡片降级渐变和输入框的白色覆盖，减少“白色塑料板”观感，同时保留足够的文字对比度。
- 照片查看器遮罩由 94% 降至 68%，首页滚动标题遮罩最高不再超过 46%，让动态背景在所有主要状态下保持可见。
- 验证：`:app:assembleDebug` 构建成功，`git diff --check` 通过。

---

## 迭代记录：2026-10-03 - 修正导航栏四层 Backdrop 采样顺序

- 将原先只记录动态壁纸的单一 Backdrop 拆分为两级：页面卡片继续采样纯动态背景；导航栏改为采样“动态背景 + 页面卡片/UI”的完整页面合成层。
- 右下角收起气泡与展开后的导航外壳位于页面合成层之上，因此透镜会正确折射被它覆盖的卡片内容，不再直接穿透到最底层壁纸。
- 导航外壳额外记录为独立 Backdrop；当前页面指示玻璃块组合采样页面合成层与导航外壳，形成真正的第四层折射。
- 层级顺序固定为：动态背景 → 页面卡片/UI → 导航外壳/气泡 → 当前页面指示透镜，同时避免页面卡片采样自身造成反馈循环。
- 验证：`:app:assembleDebug` 构建成功。

---

## 迭代记录：2026-10-02 - 导航液态交互与玻璃气泡

- 移除首页“状态是第一优先级”区域背后的大块玻璃卡片，恢复为悬浮排版，避免遮挡活力环与动态背景。
- 底栏收起气泡展开时不再简单交叉淡入：完整底栏从右下角气泡位置横向拉伸、纵向回弹并渐显；气泡同步拉伸、收缩和淡出，形成连续的液态形态过渡。
- 在整个底栏内容区域增加水平拖动手势。拖动时活动透镜实时跟手，超过 18% 单项宽度后松手切换相邻页面，并使用阻尼弹簧吸附。
- 底栏主体填充透明度降低至约 7.5%，活动透镜降至约 5%–11%；同时提高折射量、色散、高光与内壁反射，使玻璃更透明但轮廓更清晰。
- 休息页气泡在 Android 13+ 改用 Backdrop 实时采样背景，并应用色散透镜、定向高光、内壁反射与轻微色调；旧系统保留低透明渐变降级。
- 验证：`:app:assembleDebug` 构建成功，`git diff --check` 通过。
