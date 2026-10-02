# 活力 2 · Vitality Compose

**Android Studio 导入：** 解压 ZIP，选择 `File → Open`，打开包含 `settings.gradle.kts`、`gradlew.bat`、`app` 的 `VitalityCompose` 文件夹。不要新建空白项目，也不要打开旧的 4 文件 `Vitality_Compose_Final.zip`。首次 Gradle 同步要下载 Gradle 8.11.1 和依赖；同一版本下载成功后复用缓存。安装 Android SDK Platform 35 和 JDK 17。

在 `app/src/main/java/com/jiang/vitality/ui/HomeScreen.kt` 打开 `HomeScreenPreview`，同步完成后点右上角 **Split / Design** 即可看首页预览；还有 `RecoveryPreview`。Preview 显示静态示例值，提醒和桌面小组件需运行到手机或模拟器验证。

功能：本地状态记录与备注、七日历史、状态建议、可增删改停用的每日提醒、桌面小组件快捷 ±5、自定义休息转盘、Call it a day 休息锁定（次日 06:00 解锁，每满一小时 +2）。状态上限 100。定时通知点击可打开记录弹窗。小组件锁定期间不能修改状态。

第一次启动授权通知；如需尽量准时提醒，在设置页开启“精确提醒”。ColorOS 的后台限制可能使非精确提醒推迟。重启和时区变化后会重新安排提醒。

当前包名 `com.jiang.vitality.compose`，可以与旧 Java 原型共存；二者数据不互通。所有数据仅保存在本机。界面采用柔和渐变、半透明与高光边缘的玻璃视觉；系统桌面小组件不支持对桌面壁纸实时采样模糊。

Android Studio 的 **Run** 会自动构建和部署。命令行在本目录运行 `gradlew.bat assembleDebug`，APK 位于 `app/build/outputs/apk/debug/`。
