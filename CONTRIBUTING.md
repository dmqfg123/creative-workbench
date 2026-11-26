# 参与

这是一个个人项目，但欢迎提 Issue / PR。

## 提交前

1. 单元测试必须全绿：`./gradlew :app:testDebugUnitTest`（124 条）。
2. 不要提交：`local.properties`、`keystore/`、`apk/`、`shots/`、任何真实稿件或 API Key。
3. 改 AI 参数/提示词时，附一小段前后对比（输出长度、是否触发拒写）。

## 代码约定

- 正文只有一个 Editable：外部改文本用 `editable.replace()`，**禁止 setText**。
- 写库走 repository 的 suspend 接口，别在 UI 线程碰文件。
- 新功能先在 Issue 里说清楚「谁在什么场景下用」，再动手。

## 提交信息

`<版本>: <一句话>`，正文写「为什么」而不是「改了什么」。
