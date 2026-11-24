# CHANGELOG

## 0.1.0（2026-11-24）S1 骨架

- Android 工程：AGP 8.5.2 / Gradle 8.9 / Kotlin 2.0.21 / compileSdk 34 / targetSdk 34 / minSdk 29。
- Compose 单 Activity + Navigation；书架 / 笔记 / 更多 3 Tab；笔记模块关闭后 2 Tab。
- Room v1：works / chapters / notes / settings / modules / snapshots；WAL。
- 章节文件：JSON 原子写 + fsync + .bak；journal NDJSON + crc32 + 恢复。
- 单一写队列（Mutex）+ 每章 revision + UpdateSource；SaveResult.Conflict。
- ModuleRegistry / WorkbenchModule / ModuleHost + Disposable；NotesModule 是唯一可关模块。
- EditCommand + EditEngine：Move / Remove / Split / Merge / Insert；单元测试 5 条通过。
- 设置：主题、字号、笔记开关、明文 HTTP、帧率显示；SharedPreferences。
- 书架：空态、新建作品（只填书名）、卡片、删除确认；编辑器为 S2 占位页。
- 构建：debug APK 成功；未装机（无 adb 真机）。
- 已知：APK 体积偏大（debug，未压缩/未 R8）；编辑器、AI、备份未实现。
