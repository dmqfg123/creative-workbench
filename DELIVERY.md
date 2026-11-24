# 阶段性交付说明（0.10.0）

> 日期：2026-11-24 ｜ 代码：E:\AIbendi\work\creative-workbench（tag v0.10.0）｜ 文档：E:\AIbendi\文学创作平台项目
> 状态：S1–S5 落地；PRD 缺口已补；真机回归通过；release 已开 R8。
> 逐版本改动见本仓库 CHANGELOG.md；为什么这么做、踩过什么坑见 项目\复盘_0.4.9到0.10.0_20261124.md。

## 产物

| 文件 | 大小 | SHA-256 |
|---|---|---|
| apk/雨刮模拟器_0.10.0_release.apk | 3,690,510 B | 5AFE582EB522B7CC78020BD790582FABBE507852B9BDD9113FC7BFAE172A7653 |
| apk/雨刮模拟器_0.10.0_debug.apk | 28,864,551 B | 78DB6EF854ACAFF4452BB22F6C13A17A17A65EE7042A7656E83EE5F8CF216D2E |
| apk/mapping_0.10.0.txt | 33,984,687 B | B035E27BF6B2D69F8A8F19895C4C44A5181439226A21CF31304A48FE054253D9 |

- package：com.creativeworkbench.writer；versionCode 21 / versionName 0.10.0；minSdk 29 / targetSdk 34 / compileSdk 34。
- 签名：项目内 keystore/debug.keystore（固定，可一直覆盖安装、数据保留）。**不是商店发布签名**；要换正式签名需「导出备份 → 卸载 → 装新签名 → 导入」一次，之后固定用它。
- 真机：Redmi Note 11 5G（Android 13 / HyperOS / 默认 60Hz）。0.9.0 → 0.10.0 覆盖安装，作品/章节/笔记/设置全保留；设备内 APK 哈希与上表一致。
- release 开了 R8，崩溃栈里的自家类名与行号已保留；混淆后要还原库类名，用 mapping_0.10.0.txt。

## 本轮改动（0.10.0，按真机数据做性能优化）

- release 开 R8（minify + shrinkResources）：**APK 21.7 → 3.5 MB**，冷启动 450–486 → **359–368 ms**，PSS 64.3 → **53.7 MB**。
- WB_PERF 打点只在 debug：连续输入 p95 **18 → 12 ms**，janky 帧 **11.9% → 0%**。
- 启动备份只构建一次（本机 + Download 共用一份文本），并推迟 1.5 s 等首帧；字数抽检推迟 2.5 s。
- 混淆 mapping 存档；R8 后书架/编辑器/笔记/AI 预览/备份导出逐屏复跑通过。

## 实测（0.10.0 release / 真机，30 章 29.2 万字）

- 冷启动 359–368 ms（首次 437 ms 含 dexopt，三次取后两次）。
- PSS 53.7 MB；编辑器滚动 373 帧 0% jank、p95 16 ms。
- 连续输入 60 次：0% janky、p95 12 ms、p99 13 ms（预热后测）。
- 升级：0.9.0 → 0.10.0 覆盖安装，数据完整；R8 包内序列化（备份/迁移包）、Room、AI 上下文预览均正常。

## 已知问题 / 未验

- 无待修 P2/P3。BUG-06/07 有结论不改（见项目 验收/BUG日志）。
- 未验：AC-06 端到端输入延迟（要真人打字，且 release 已无打点，需装 debug 包）、AC-07 Gboard/百度（手机只装搜狗）、AC-10 系统选择菜单里的「AI 改写」、AC-12 素材类勾选进上下文、清空全部数据（需输入中文「清空」+ SAF 保存框）。
- 口径：每日备份落点改 Download（已定）；检查 >1.5 万字只查光标块 ±2（PRD 内部冲突，取窄口径）。
- 详见项目 交接说明.md、验收/AC状态_20261124.md、验收/PRD实现核对_20261124.md、参考/真机性能剖析_20261124.md。
