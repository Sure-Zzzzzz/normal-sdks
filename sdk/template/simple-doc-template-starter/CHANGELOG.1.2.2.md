# Changelog

## [1.2.2] - 2026-07-07

### Bug Fix

- **MdOutputHandler 收到 null Document 的 ErrorCode 修正**
  - 1.2.1 及之前，`MdOutputHandler.toBytes(null)` 抛 `MD_002`（Markdown 模板渲染失败），与“渲染失败”语义不符
  - 1.2.2 改为抛 `OUTPUT_002`（Document 类型与 Handler 不匹配），与 `DocxOutputHandler` 等 Handler 处理 null Document 的方式对齐
  - 本次唯一的 ErrorCode 变更；捕获 `MD_002` 处理 null Document 的业务需改为捕获 `OUTPUT_002`

### 代码质量

- **错误消息零硬编码（规范 §8.1）**
  - 全模块硬编码错误消息收敛为 `ErrorMessage` 常量，新增 16 个 `ErrorMessage` 常量（按 Markdown 能力描述 / 图片变量 / PDF 字体配置 / PDF 读取 / URL 安全 / 远程资源 / 资源大小限制分组）
  - 新增 `SimpleDocTemplateConstant.FONT_PATHS_CONFIG_KEY`，字体路径配置 key 不再硬编码
  - 涉及 12 个文件：`WordRenderer`、`ChartToPngRenderer`、`MdRenderer`、`MdOutputHandler`、`PdfConvertHelper`、`PdfOutputHandler`、`MdConditionHandler`、`MarkdownXhtmlRenderer`、`SafeUrlSanitizer`、`TemplateLocationHelper`、`TemplateResourcePolicy`、`LimitedInputStreamHelper`
  - 除上述 null Document 的 ErrorCode 修正外，其余仅改消息文案，ErrorCode 不变

### Internal

- **新增 `MdConditionHandlerTest`**（6 个用例，覆盖关键异常分支）
  - 循环内条件块 `start` / `end` 标签抛 `MD_001`
  - 条件块在循环外：`true` 保留块内容、`false` 整块删除
  - `MdOutputHandler` 收到 null Document 抛 `OUTPUT_002`
  - `MdOutputHandler` 收到正常 `MdDocument` 原样返回

### Compatibility

- 不改变对外 API、注解语义、配置 key 语义
- 唯一 ErrorCode 变更：`MdOutputHandler` null Document 由 `MD_002` → `OUTPUT_002`
- 错误消息文案统一调整，均为用户面提示，不影响程序逻辑
- 远程模板/图片默认禁用、资源大小限制、路径逃逸校验等安全策略行为保留，仅消息文案常量化
- 向后兼容；除“捕获 `MD_002` 处理 null Document”这一非常规用法外，业务无需改动

### Validation

- `:sdk:template:simple-doc-template-starter:compileJava`：BUILD SUCCESSFUL
- `:sdk:template:simple-doc-template-starter:compileTestJava`：BUILD SUCCESSFUL
- `:sdk:template:simple-doc-template-starter:test`：BUILD SUCCESSFUL（135 tests，0 failures / 0 errors / 0 skipped，Gradle 8.5 + Java 11 + `--no-daemon`）

### Out of Scope

- 本版本为封版版本，模块此后进入冻结状态，不再新增功能

---
