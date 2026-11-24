# CHANGELOG

## 0.2.0（2026-11-24）同类源码拆解后的优化

- 参考 cascade-editor：LazyColumn `imePadding`；聚焦块最小滚动（可见不动、被裁只滚 delta、未组合才 scrollToItem），附 4 条纯函数单测。
- 参考 字隅（3Cne/ziyu）：软键盘回车用 TextWatcher 去掉 `\n` 并拆段；段首退格用 InputConnectionWrapper 拦截 `deleteSurroundingText` / `deleteSurroundingTextInCodePoints`，菜单保留兜底。
- 性能：字数改为增量维护，只算当前段；结构操作/撤销才全量刷新。
- 数据安全：文件 revision 领先 DB 时以文件为准修复 DB/作品总字数；删除先 DB 后文件；损坏/保存失败不再渲染空编辑器，也不吞异常。
- Room：导出 schema 到 app/schemas；移除 fallbackToDestructiveMigration，升级必须显式 Migration。
- 单测 13 条通过；lint 0 error。
- 参考但不照抄：Aria 的 PromptTemplate/JSON 输出留到 S4；Writeopia 的 AI 任务管理/流式不做；Quillpad 的备份/迁移测试留到 S5；hammer 的 revision/UpdateSource 继续沿用。

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
- 字号设置已接入编辑器正文（15/27、17/31、19/35）。
- 数据安全：journal 改为批量写入；拆分/合并/撤销等结构操作写全部受影响块；章节文件损坏时显式报错，不再用空章节覆盖。
- S3 补章节摘要：目录行菜单编辑 100–200 字摘要，AI 上下文（S4）直接使用。
- 单测扩到 9 条：EditEngine 5、TokenEstimator 3、BackupBundle 序列化 1。
- 工程：Gradle wrapper 8.9；GitHub Actions（单测 + assembleDebug + APK artifact）。
- 许可证：GPL-3.0（LICENSE）。
- 已知：S2 未做真机 IME/性能测试；APK 体积偏大（debug 59 MB / release 20.7 MB，未开 R8）；AI、备份未实现。
