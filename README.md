# 创作工作台

个人 Android 写作 App（launcher 名《雨刮模拟器》）：手机原生、AI 辅助、数据只存本机。

## 状态

- 版本：**0.11.0**（versionCode 22，未打 tag）；S1–S5 全部落地。
- 0.11.0 状态：按使用反馈重做 AI 参数 / 假按钮 / 编辑器交互；**只编译过（compileReleaseKotlin），未构建 APK、未装机、未跑单测**。回归清单见 项目\待办_0.11.0真机回归_20261125.md。
- 上一个可安装版本：0.10.0（versionCode 21，tag v0.10.0，APK 在 apk/）。
- 构建：AGP 8.5.2 / Gradle 8.9 / Kotlin 2.0.21 / compileSdk 34 / minSdk 29；release 已开 R8。
- APK：release **3.5 MB** / debug 28.9 MB；已多次覆盖安装到 Redmi Note 11 5G（Android 13），数据保留。
- 单测：**67 条全通过**（`gradlew testDebugUnitTest`）。
- 性能（真机 60Hz / 30 章 29.2 万字）：冷启动 359–368 ms、PSS 53.7 MB、滚动 0% jank、输入 p95 12 ms。
- 验收：22 条 AC 18 通过 / 4 部分 / 0 未验（见 项目\验收\AC状态_20261124.md）。

## 构建

需要 JDK 17 + Android SDK 34。开发机用固定工具链，走 DSH 的 build_android_apk：

~~~text
build_android_apk(
  project="E:/AIbendi/work/creative-workbench",
  variant="release",
  downloadDir="E:/AIbendi/work/android-toolchain",
  apkOutputDir="E:/AIbendi/work/creative-workbench/apk"
)
~~~

命令行等价（Windows 下必须显式给这三个环境变量，否则 gradlew 会去中文用户目录找工作目录而失败）：

~~~powershell
$env:JAVA_HOME="E:\AIbendi\work\android-toolchain\jdk\jdk-17.0.21.1+2"
$env:ANDROID_HOME="E:\AIbendi\work\android-toolchain\android-sdk"
$env:GRADLE_USER_HOME="E:\AIbendi\work\android-toolchain\gradle-user-home"
.\gradlew.bat :app:assembleRelease
~~~

项目带 Gradle wrapper（8.9）。签名用项目内 keystore/debug.keystore（固定，自用，非发布签名）；新克隆没有该文件时回退到系统 debug keystore —— 那会导致覆盖安装失败，先补 keystore。

## 结构

~~~text
app/src/main/java/com/creativeworkbench/writer/
  core/data        Room 实体/DAO/数据库、章节文件、journal、revision、快照
  core/editor      EditCommand / EditEngine / 续位
  core/ai          动作与提示词、上下文装配（SettingInjector/PromptBudget）、JSON 容错、usage 记账、动作包（PackArchive）
  core/backup      备份包、迁移包 zip、每日备份、崩溃日志
  core/nav         模块注册表（唯一可关模块：设定笔记）
  core/settings    主题/字号/模块开关
  ui/              书架 / 目录 / 笔记 / 更多 / 分块编辑器（EditText）
app/schemas/       Room schema（升级时必须写 Migration）
~~~

产品文档（PRD v4.0、决策记录、工作流、验收、复盘）在 `E:\AIbendi\文学创作平台项目`，与代码仓库分开。

## 设计约束

- 书架与 AI 在内核，不可关；唯一可关模块是「设定笔记」。
- 不做运行时插件；导入包只含 AI 动作与提示词。
- 每章 revision；首行缩进用 paddingStart；block 只有 {id, text}。
- 正文用 EditText 分块编辑器（同一时刻只有一段可编辑），不用 Compose TextField。
- release 开 R8；自家类名与行号保留（崩溃日志可读），库类名还原用 `apk/mapping_*.txt`。

## License

GPL-3.0，见 LICENSE。
