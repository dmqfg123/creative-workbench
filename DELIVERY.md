# 阶段性交付说明（0.4.9）

> 日期：2026-11-24 ｜ 代码：E:\AIbendi\work\creative-workbench（tag v0.4.9）｜ 文档：E:\AIbendi\文学创作平台项目
> 状态：S1–S5 已落地，已多次覆盖安装到真机；22 条 AC：18 通过 / 4 部分 / 0 未验。

## 产物

| 文件 | 大小 | SHA-256 |
|---|---|---|
| apk/雨刮模拟器_0.4.9_release.apk | 21,585,042 B | E57940FD8B30E025A539881FAB7D5D50644D68A88F9E77EEEDF56E187C5ADE2D |
| apk/雨刮模拟器_0.4.9_debug.apk | 28,684,331 B | 9D93F90F3F44BD0D42D4A9179BCA450A3BDF086A2FB3FAF4F3F870348B2F811B |

- package：com.creativeworkbench.writer；versionCode 15 / versionName 0.4.9；minSdk 29 / targetSdk 34 / compileSdk 34。
- 两个 APK 目前都用项目内 debug keystore 签名（CN=Android Debug）；release 不是正式发布签名。
- 真机安装的就是上表 release（Redmi Note 11 5G / Android 13），覆盖安装保留数据。

## 本轮改动（0.4.8 → 0.4.9）

- BUG-04：目录页提示条抬到「新章节」FAB 上方（SnackbarHost 移进内容层，底 88dp），提示期间点 FAB 不再被吃。
- BUG-05：新增 ChipRow，chip 行左右边缘渐隐（draw 阶段画，绕开 LazyColumn 无界高度约束）；笔记列表与笔记详情统一使用。
- BUG-06/07：有结论不改（撤销只在当前页；键盘打开时返回先收键盘属系统行为）。
- 动作包校验抽成 PackArchive（纯 JVM），安全用例做成 15 条单测；解压改分块读，防压缩炸弹。

## 已实现（摘要）

- 书架/目录/编辑器/笔记/AI/动作包/备份/设置全部落地；应用名《雨刮模拟器》+ 自适应图标。
- 编辑器：分块 EditText、自动保存双触发 + journal、撤销/重做、拆段/合并、续位、备注、笔记抽屉插入。
- AI：OpenAI 兼容配置（Keystore 加密）、3 内置动作 + 动作包动作、上下文预算/预览、写入确认、快照、历史撤销。
- 备份：本机 3 份 + 每日 Download/雨刮模拟器备份/、SAF 导出/导入、单作品 Markdown/TXT、清空、崩溃日志。

## 实测

- 5 万字 / 500 段：打开到正文 159–207 ms；滚动 p95 29 ms；输入 p95 20 ms（Redmi Note 11 5G / 90Hz）。
- 单测 36 条通过；0.4.9 构建通过（release + debug）。
- 0.4.9 真机回归：提示条/FAB、chip 渐隐、200% 字号、飞行模式启动、AI 上下文（mock 请求体含设定与摘要）、覆盖安装保数据。

## 已知问题与未验

- 无待修 P2/P3；BUG-06/07 有结论不改，见项目 验收/BUG日志_20261124.md。
- 未验：AC-06 端到端输入延迟、AC-07 Gboard/百度、AC-10 系统选择菜单里的「AI 改写」、AC-12 素材类勾选进上下文、清空数据实跑。
- 详见项目 交接说明.md 与 验收/AC状态_20261124.md。
