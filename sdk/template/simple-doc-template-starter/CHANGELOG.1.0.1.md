# Changelog

## [1.0.1] - 2026-06-03

### 代码质量

- **移除 `WordRenderer.processConditionBlocks`**
  - 条件块已在 `ConditionProcessor` → `DocxConditionHandler` 字节级处理完毕，`WordRenderer` 中的 POI 级重复处理为冗余代码
  - 消除两遍条件块处理带来的认知负担和潜在不一致风险

- **统一真值判断逻辑**
  - 新增 `support/BooleanHelper`，包含 `isTrue(Object)` 方法
  - `DocxConditionHandler` 改用注入的 `BooleanHelper`
  - 消除 `isTruthy` 两处重复实现

### 内部变更（无 API 变化）

- 新增 `BooleanHelper.java`
- `DocxConditionHandler` 注入 `BooleanHelper`
- `WordRenderer` 移除 `processConditionBlocks` 和 `isTruthy` 方法

---

## [1.0.0] - 2026-05-28

### 首发版本

- 基于 Apache POI 直连的 Word 文档模板渲染 SDK
- 模板语法：`[suredt.var:key]`、`[suredt.img:key]`、`[suredt.chart:key]`、`[suredt.start:key]/[suredt.end:key]`、`[suredt.for:key]/[suredt.endfor:key]`
- 支持文本变量、图片（PNG/JPEG/GIF）、Word 原生可编辑图表（折线图/柱状图/饼图）、条件块、表格行循环
- 链式 API + 快捷方法
- `templateLocation` 配置自动拼接路径
- `tagPrefix` 可配置
- Spring Boot Starter 自动装配