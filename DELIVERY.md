# 阶段性交付说明（0.2.1）

> 日期：2026-11-24 ｜ 代码：E:\AIbendi\work\creative-workbench（tag v0.2.1-ux）｜ 文档：E:\AIbendi\文学创作平台项目
> 状态：可构建、可安装的 S1-S3 原型；**未真机验证**。

## 1. 产物

| 文件 | 大小 | SHA-256 |
|---|---|---|
| apk/创作工作台_0.2.1_debug.apk | 27,611,858 B | EA6B2A0B4FD4D7075B5CC9D66BDD28EA3DC7C65A1B1FF3CC8010BC36BB79D822 |
| apk/创作工作台_0.2.1_release.apk | 20,857,465 B | E45178363231C88E54E2C5CF3354AF39D01131E726DD6AF4058C8962A345C1C2 |

- package：com.creativeworkbench.writer
- versionCode 3 / versionName 0.2.1
- minSdk 29 / targetSdk 34 / compileSdk 34
- 两个 APK 目前都用项目内 debug keystore 签名（CN=Android Debug）；release 不是正式发布签名。

## 2. 已实现

### S1 数据层
- Room v1：works / chapters / notes / settings / modules / snapshots；schema 导出到 app/schemas。
- 章节 JSON 原子写 + fsync + .bak；journal NDJSON + crc32 + 恢复。
- 单一写队列 + 每章 revision + UpdateSource；保存冲突返回 SaveResult.Conflict。
- 文件 revision 领先 DB 时以文件为准修复 DB 与作品总字数；删除先 DB 后文件。
- 损坏/保存失败不再用空章节覆盖，也不吞异常。

### S2 编辑器原型（0.2.1 交互重做）
- 文档感布局：顶部只留返回/作品名/章节名/⋯；底部工具条放撤销/重做/＋段/段落/收起。
- 去掉每段「⋮」手柄与「点击编辑」提示；点正文任意位置按落点定位光标。
- 章节标题在正文内，字数/保存状态在标题下；空文档只提示一次。
- LazyColumn 分块 + 单个 EditText 活动段；其余段落只读。
- 回车拆分：硬件键走 onKeyDown；软键盘走 TextWatcher 去掉 \n 并拆段。
- 段首退格：OnKeyListener + InputConnectionWrapper（deleteSurroundingText / deleteSurroundingTextInCodePoints）双路径，菜单保留「合并到上一段」兜底。
- 自动保存 1.5s / 10s 双触发；journal 批量写入；撤销/重做；字数增量维护。
- imePadding + 聚焦块最小滚动（可见不动、被裁滚 delta、未组合才 scrollToItem）。
- 字号 15/27、17/31、19/35 生效。

### S3 书架与目录
- 书架加「继续创作」大卡 + 顶部「＋ 新作品」。
- 目录页加作品头卡（继续写作/新建章节）、当前章高亮；移除悬浮按钮。
- 新建作品（只填书名）、删除确认、作品目录入口。
- 目录页新建/改名/删除章节、章节摘要（100-200 字）、进入编辑器。
- 编辑器「目录」入口，切换前 flush。

### 工程与开源
- GPL-3.0、README、CHANGELOG、Gradle wrapper 8.9、GitHub Actions。
- 单测 13 条通过；lint 0 error（仅有 app icon、allowBackup 等 warning）。
- Room schema 已导出；已移除 fallbackToDestructiveMigration。

## 3. 未完成 / 风险

- **S2 没真机验证**：5 万字、三种输入法、p95、键盘遮挡、软键盘退格 5% 边界都未测。
- S4 设定笔记 + AI、S5 备份/主题/签名未开始；PRD 22 条 AC 只覆盖了少量基础路径。
- LazyColumn 未做高度缓存，5 万字滚动仍可能跳变。
- 笔记页是占位；AI 配置/历史/动作包未实现。
- Release 是 debug 证书；R8 未开，20.8 MB 可继续降。
- 代码仓库与文档仓库仍分开；开源发布前建议合并 docs/。
- 没有 instrumentation / UI 截图测试；Paparazzi/Roborazzi 未接入。

## 4. 安装与验证

~~~powershell
E:\AIbendi\work\adb.cmd install -r -t E:\AIbendi\work\creative-workbench\apk\app-debug.apk
~~~

没有 adb 时把 APK 传到手机，允许未知来源安装。首次打开：书架 → 新建作品 → 写正文 → 返回/目录 → 杀进程重开。

## 5. 下一阶段建议

1. 有真机：先跑 S2 验收（5 万字 / 26 键三输入法 / p95 33 ms / 键盘遮挡 / 回车退格）。
2. 无真机：继续 S4（Note CRUD/速记/别名、AIConfig、上下文预算、3 个动作、历史 undoPatch、动作包导入）。
3. 再做 S5（导出导入/SAF/清空/主题/崩溃日志/签名），最后跑 22 条 AC。

## 6. 参考的同类源码（只借思路，不复制代码）

- cascade-editor（MIT）：聚焦块最小滚动、imePadding、退格检测思路。
- 字隅 3Cne/ziyu（无许可证）：软键盘 TextWatcher 回车、InputConnection 退格、章节历史「先留底再写」。
- hammer-editor（MIT）：revision / UpdateSource / buffer 持久化；沿用我们已有设计。
- Aria（无许可证）：PromptTemplate / JSON 输出 / 连续性检查，留到 S4。
- Quillpad（GPL-3.0）：Room schema 导出、迁移测试、备份服务，S5 参考。
- SillyTavern（AGPL-3.0）：世界书预算/激活，只读思路，不复制代码。
