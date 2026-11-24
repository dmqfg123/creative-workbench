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
- S2 原型：LazyColumn 分块 + 单个 EditText 活动段；回车拆分、段首退格合并、手柄菜单上移/下移/插入/删除；1.5s/10s 自动保存；journal；撤销/重做；字数；返回前 flush。
- S3 原型：作品目录页；新建/改名/删除章节；书架卡片「作品目录」入口；编辑器「目录」入口（切换前 flush）。
- 工程：Gradle wrapper 8.9；GitHub Actions（单测 + assembleDebug + APK artifact）。
- 许可证：GPL-3.0（LICENSE）。
- 已知：S2 未做真机 IME/性能测试；APK 体积偏大（debug 59 MB / release 20.7 MB，未开 R8）；AI、备份未实现。
