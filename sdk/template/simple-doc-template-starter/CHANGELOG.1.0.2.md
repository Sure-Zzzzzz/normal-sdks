# Changelog

## [1.0.2] - 2026-06-08

### Bug Fix

- **表格行循环展开行丢失单元格格式（`copyCellProperties`）**
  - 症状：`[suredt.for:key]` 循环展开时，新增行单元格的宽度、背景色、边框、垂直对齐均丢失
  - 原因：`copyRowStructure` 只复制文本内容，未复制 `CTTcPr`（单元格属性）
  - 修复：新增 `WordRenderer.copyCellProperties(XWPFTableCell src, XWPFTableCell dst)`，在 `copyRowStructure` 中调用，拷贝 `TcW`（宽度）、`Shd`（背景色）、`TcBorders`（边框）、`VAlign`（垂直对齐）、`NoWrap`

### Feature

- **页眉/页脚支持模板占位符**
  - `WordRenderer.processHeadersAndFooters`：遍历 `doc.getHeaderList()` 和 `doc.getFooterList()`，对每个段落执行 `processParagraphText`（变量替换）和 `replaceImgPlaceholder`（图片替换）
  - `DocxConditionHandler.processHeaderFooterXml`：在字节级 ZIP 处理阶段，对 `word/header*.xml` 和 `word/footer*.xml` 执行条件块删除，逻辑与 `processDocumentXml` 一致
  - 兼容 OOXML 命名空间声明：opening tag 含 `xmlns:w="..."` 时改用 closing tag 定位内容边界，避免 `indexOf("<w:hdr>")` 误匹配

### 代码质量

- **`Chart.Series` 内部类补齐 `@Getter`**：Lombok 自动生成 `getName()`、`getValues()`、`getColor()`，移除冗余手写方法
- **`Chart.LegendPosition` 枚举规范化**：补充 `description` 字段、`fromCode()`、`isValid()`、`getAllCodes()`、`toString()`，符合 SDK 枚举规范
- **`DocxConditionHandler` 提取 OOXML 字符串常量**：`"<w:hdr>"` / `"</w:hdr>"` / `"<w:ftr>"` / `"</w:ftr>"` 改为 `private static final` 常量，消除字面量硬编码
- **`WordRenderer` 步骤注释纠正**：`render()` 方法中注释编号从 `// 3./4./5./6.` 纠正为 `// 1./2./3./4./5.`
- **`WordRenderer` 提取 OOXML 命名空间和关系类型常量**：`NS_RELATIONSHIPS`、`NS_WP`、`NS_A`、`NS_C`、`NS_R`、`REL_TYPE_CHART`、`REL_TYPE_PACKAGE`、`CT_CHART`、`CT_XLSX` 提取到 `SimpleDocTemplateConstant`，消除硬编码
- **`TestTemplateGenerator` 测试辅助类规范**：移除 `System.out.println`，改用 `@Slf4j` + `log.info`，符合 SDK 测试规范

### 内部变更（无 API 变化）

- `SimpleDocTemplateConstant`：新增 OOXML 命名空间和关系类型常量（NS_RELATIONSHIPS、NS_WP、NS_A、NS_C、NS_R、REL_TYPE_CHART、REL_TYPE_PACKAGE、CT_CHART、CT_XLSX）
- `WordRenderer`：新增 `copyCellProperties`、`processHeadersAndFooters` 私有方法；OOXML 命名空间常量改为引用 `SimpleDocTemplateConstant`
- `DocxConditionHandler`：新增 `isHeaderOrFooter`、`processHeaderFooterXml` 私有方法；`processInternal` 增加 header/footer ZIP 条目处理分支
- 新增测试模板 `header-footer-template.docx`、`cell-format-template.docx`
- 新增测试用例 `renderHeaderFooterVariablesAndCondition`、`renderHeaderConditionBlockFalse`、`renderTableLoopPreservesCellBackgroundColor`

---
