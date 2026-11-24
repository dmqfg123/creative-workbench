# 创作工作台

个人 Android 写作 App：手机原生、AI 辅助、数据只存本机。当前是 S1 骨架 + S2 编辑器原型 + S3 目录原型；AI/备份尚未实现。

## 状态

- 版本：0.2.0（S1 + S2 + S3 原型，含同类源码优化）
- 构建：AGP 8.5.2 / Gradle 8.9 / Kotlin 2.0.21 / compileSdk 34 / minSdk 29
- 未装机验证（当前没有接 adb 真机）；单元测试 13 条通过；lint 0 error。

## 构建

需要 JDK 17 + Android SDK 34。当前开发机使用固定工具链，调用 DSH 的 build_android_apk：

~~~text
build_android_apk(
  project="E:/AIbendi/work/creative-workbench",
  variant="debug",
  downloadDir="E:/AIbendi/work/android-toolchain",
  apkOutputDir="E:/AIbendi/work/creative-workbench/apk"
)
~~~

命令行等价：

~~~powershell
$env:JAVA_HOME="E:\AIbendi\work\android-toolchain\jdk\jdk-17.0.21.1+2"
$env:ANDROID_HOME="E:\AIbendi\work\android-toolchain\android-sdk"
$env:GRADLE_USER_HOME="E:\AIbendi\work\android-toolchain\gradle-user-home"
.\gradlew.bat :app:assembleDebug
~~~

项目带 Gradle wrapper（8.9）；也可以直接用工具链里的 Gradle。debug 签名优先用本机 keystore/debug.keystore（不进 git）；新克隆没有该文件时回退到系统 debug keystore。

## 结构

~~~text
app/src/main/java/com/creativeworkbench/writer/
  core/data        Room 实体/DAO/数据库、章节文件、写队列、revision
  core/editor      EditCommand / EditEngine
  core/ai          AiActionSpec / 上下文预算 / token 估算（S4 接网络）
  core/nav         WorkbenchModule / ModuleRegistry / NotesModule
  core/settings    主题/字号/模块开关
  core/backup      BackupBundle v1
  ui/              书架 / 目录 / 笔记 / 更多 / 分块编辑器
app/schemas/       Room schema（升级时必须写 Migration）
~~~

产品文档（PRD v4.0、决策记录、S1-S5 工作流）在 E:\AIbendi\文学创作平台项目，与代码仓库分开放置，后续可合并为 monorepo。

## 设计约束

- 书架与 AI 在内核，不可关；唯一可关模块是「设定笔记」。
- 不做运行时插件；导入包只含 AI 动作与提示词。
- 每章 revision；首行缩进用 paddingStart；block 只有 {id, text}。
- 正文用 EditText 分块编辑器（活动段单个 EditText）；S2 原型已接数据层，待真机 IME/性能验证。

## License

GPL-3.0，见 LICENSE。如果你希望 AGPL-3.0，告诉我再换。
