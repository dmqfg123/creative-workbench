# CHANGELOG

## 0.3.0（2026-11-24）UI 位置与动效调整

按同类写作 App 的通用位置重排，并补上页面/按压/状态动画；数据层与编辑逻辑未改。

- 全局：NavHost 加转场——同级页（书架/笔记/更多）淡入+微缩放，目录/编辑器从右侧滑入、返回滑出（320 ms emphasized 曲线）；动效时长与曲线集中在 ui/theme/Motion.kt。
- 底部导航图标由“书/记/…”文字换成 Home/Edit/Settings 矢量图标，保留文字标签。
- 书架：新建入口从顶部文字按钮改为右下 ExtendedFAB「新作品」（单手可达，同 Notion/Keep 位置）；「继续创作」整卡可点 + 按下 0.98 缩放 + 「接着写 ›」；作品卡按下缩放、⋯ 改为 48dp 图标按钮；空态改成图标+主按钮；作品操作与删除确认改走底部动作面板。
- 目录：返回改图标按钮；顶部标题带章数/字数；「继续写作」独立大卡；当前章高亮用 300 ms 颜色动画 + 左侧色条；章节条目增删有 animateItem 位移动画；章节菜单改底部动作面板；新增「新章节」右下 FAB。
- 编辑器：返回/⋯ 改图标按钮（48dp 触控）；正文标题不重复出现在顶栏；底部工具条改成贴键盘的圆角悬浮条（撤销/重做/新段/段落/收起，图标+小字，禁用态淡出），进入时从底部滑入；「段落」操作改底部面板；点非活动段有涟漪反馈；字数变化 420 ms 数字滚动，保存状态 Crossfade。
- 更多页：改为分组卡片（模块/外观/数据/关于），主题与字号换成 SegmentedButton（不再写 15/27 这类实现细节）。
- 版本 0.3.0（versionCode 4）。

## 0.2.1（2026-11-24）交互与界面重做

- 编辑器改成“文档感”：顶部只留返回/作品名/章节名/⋯；撤销、重做、＋段、段落、收起移到底部工具条。
- 去掉每段的「⋮」手柄和「点击编辑」提示；点正文任意位置按落点定位光标（不再总是跳到段尾）。
- 章节标题改为正文内标题；字数/保存状态在标题下；空文档只提示一次“点击正文开始写”。
- 软键盘回车拆段、段首退格合并双路径保留；聚焦块最小滚动 + imePadding 保留。
- 书架加「继续创作」大卡（作品名/章数/字数/最近编辑 + 接着写）；顶部加「＋ 新作品」。
- 目录页加作品头卡（继续写作/新建章节）、当前章高亮、章节字号加粗；移除右下角悬浮按钮。
- 合并到上一段后光标落在拼接点；打开目录时记录当前章。

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
