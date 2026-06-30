# Changelog

## [1.1.1] - 2026-06-26

### Enhancement

- **新增 `DocxHelper` 作为业务推荐 DOCX 模板入口**
    - `render(...)` / `renderToStream(...)` / `renderToFile(...)`：DOCX 模板渲染为 DOCX bytes/流/文件
    - `renderPdf(...)` / `renderPdfToStream(...)` / `renderPdfToFile(...)`：DOCX 模板渲染为 PDF bytes/流/文件
    - 封装 `TemplateEngine`，面向高频业务场景，无需理解 `TemplateRenderResult` 等内部概念

- **新增 `PdfConvertHelper` 作为 PDF 转换入口**
    - `fromDocx(byte[])` / `fromDocx(InputStream)` / `fromDocx(Path)` / `fromDocx(File)`：已有 DOCX 转 PDF bytes
    - `fromDocx(... , OutputStream)`：已有 DOCX 转 PDF 流
    - `fromDocx(Path, Path)` / `fromDocx(File, File)`：已有 DOCX 转 PDF 文件
    - 底层委托 `PdfOutputHandler`，支持多种输入来源

- **`PdfOutputHandler.convertToPdf(...)` 补强 InputStream 重载**
    - 新增 `convertToPdf(InputStream)`：支持直接传入上传流等场景

- **README 新增 Helper 快捷入口章节**
    - 明确 `DocxHelper` / `PdfConvertHelper` / `ChartPngHelper` 适用场景
    - 提供入口选择心智表格
    - 补充 Helper 使用最佳实践，明确模板渲染、已有 DOCX 转 PDF、流式输出、文件输出、字体配置和异常处理建议

### Compatibility

- 不改变 `TemplateEngine`、`PdfOutputHandler` 既有方法语义
- 不新增模板语法
- 不改变 DOCX/PDF 核心转换链路
- `DocxHelper` / `PdfConvertHelper` 不替代现有底层 API，按需选用

### Internal

- 新增 `DocxHelper`
- 新增 `PdfConvertHelper`
- 新增 `DocxHelperTest`
    - 覆盖 DOCX bytes/流/文件输出、PDF bytes/流/文件输出
    - 覆盖模板不存在异常场景
- 新增 `PdfConvertHelperTest`
    - 覆盖 byte[] / InputStream / Path / File 输入和 OutputStream / Path / File 输出
    - 覆盖无效 DOCX 字节、文件不存在异常场景

### Validation

- `:sdk:template:simple-doc-template-starter:compileJava`：BUILD SUCCESSFUL
- `:sdk:template:simple-doc-template-starter:compileTestJava`：BUILD SUCCESSFUL
- `:sdk:template:simple-doc-template-starter:test`：BUILD SUCCESSFUL

---
