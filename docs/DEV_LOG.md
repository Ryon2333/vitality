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
