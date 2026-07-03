# Changelog

## [1.2.0] - 2026-07-03

### Feature

- **新增 Markdown 模板渲染能力**
  - 新增 `MdRenderer`，支持 `[suredt.var:key]` 变量替换、`[suredt.for:key]` 循环展开、`[suredt.img:key]` 图片占位
  - 变量值做 Markdown 特殊字符转义，表格行内额外转义 `|`
  - 循环按行块展开，支持 `List<Map>`；不支持嵌套循环并抛出明确异常
  - 代码块内变量仍会被替换

- **新增 Markdown → PDF 链路**
  - `TemplateEngine.renderToPdf(...)` 支持按后缀分发：`.docx` 走 DOCX PDF 链路，`.md` 走 Markdown → XHTML → PDF 链路
  - 新增 `MarkdownXhtmlRenderer`，基于 CommonMark AST 输出 SDK 白名单 XHTML-like HTML
  - 新增 `XhtmlWriter`，统一控制 text/tag/attribute 转义
  - 新增 `SafeUrlSanitizer`，对链接和图片 src 做 scheme 收紧校验
  - 新增 `HtmlPdfRenderEngine`，作为 DOCX PDF / Markdown PDF 共用的底层 XHTML → PDF 渲染引擎
  - 新增 `PdfFontRegistry`，统一生成 PDF 字体注册计划和 CSS 字体族链，DOCX PDF 与 Markdown PDF 共用，避免中文渲染为井号

- **新增 `MdHelper` 业务入口**
  - `render(...)` / `renderToStream(...)` / `renderToFile(...)`：Markdown 模板渲染为 Markdown bytes/流/文件
  - `renderPdf(...)` / `renderPdfToStream(...)` / `renderPdfToFile(...)`：Markdown 模板渲染为 PDF bytes/流/文件
  - 所有 public 方法先做 `.md` 后缀校验，拒绝 `.docx` / `.txt` / 无后缀

- **图片资源安全闭环**
  - 新增 `ImageResourceResolver`，统一解析 classpath / file / data URI 图片，远程图片默认拒绝
  - SDK 图片（业务显式传入）支持 `classpath:` / `file:` 绝对 URI 和相对路径
  - 原生 Markdown 图片只接受相对路径（按 baseUri 受控解析）和 `data:image/*;base64,...`，拒绝 `http` / `https` / `file` / `classpath` / `jar` 等绝对 URI
  - file 模板相对图片用 `toRealPath()` 做 path-traversal 边界校验
  - classpath 模板相对图片走 ResourceLoader 解析，禁止 `..` 逃逸
  - Markdown → PDF 时 `<img src>` 只允许 resolver 生成的受控 data URI，不残留内部 token

- **受限读取统一**
  - 新增 `LimitedInputStreamHelper`，替代无上限的 `copyToByteArray` / `readAllBytes`
  - `PdfConvertHelper` 全部读取路径改走受限读取
  - 模板读取和图片读取均受 `maxTemplateBytes` / `maxImageBytes` 限制

### Refactor

- **`PdfConvertHelper` 语义收紧**
  - JavaDoc 收紧为“仅支持已有 DOCX 转 PDF”，不再暗示可扩展其他来源

- **`DocxOutputHandler.toDocxBytes` 补 null 守卫**
  - document 为 null 时抛 `formatMismatch`，避免 NPE

- **`TemplateEngine.buildBaseUri` 去硬编码**
  - 新增 `SimpleDocTemplateConstant.URL_SCHEME_CLASSPATH_PREFIX` 常量
  - classpath / file 路径解析统一引用常量

- **字体注册职责下沉**
  - `PdfOutputHandler` 字体注册逻辑迁到 `PdfFontRegistry`
  - DOCX → XHTML → PDF 的最终渲染步骤迁到 `HtmlPdfRenderEngine`
  - DOCX 链路对外 API 和功能语义不变

### Bug Fix

- **修复 data URI 图片 MIME 校验逻辑错误**
  - `ImageResourceResolver.validateDataUri` 中 `mime.startsWith(DATA_IMAGE_PREFIX)` 永远为 false，导致所有 `data:image/*;base64,...` 图片被错误拒绝
  - 改为 `mime.startsWith(IMAGE_MIME_PREFIX)`（`mime` 已去掉 `data:` 前缀）

### 代码质量

- **硬编码收敛**
  - 新增 `IMAGE_MIME_PREFIX`、`URL_SCHEME_CLASSPATH_PREFIX` 等常量，去除 `data:` / `image/` 字面量
  - 图片资源安全相关错误文案统一收敛到 `ErrorMessage`

- **异常体系补齐**
  - `ErrorMessage` 新增 14 个图片资源安全相关常量（`IMAGE_NULL` / `IMAGE_PATH_TRAVERSAL` / `IMAGE_DATA_URI_MIME_INVALID` 等）

- **文档同步**
  - README 首页更新为支持 Word 和 Markdown 模板渲染
  - README 依赖版本更新为 `1.2.0`
  - 新增 `MdHelper` 章节、Markdown 模板语法章节、Markdown → PDF 输出说明
  - 入口选择心智表和最佳实践补充 `MdHelper`
  - 明确 `HtmlPdfRenderEngine` 为内部引擎，1.2.0 不提供 `HtmlHelper`，不支持 HTML 模板变量渲染

### Internal

- 新增 `MdRenderer`、`MdOutputHandler`、`MdConditionHandler`、`MdDocument`、`MdImageReference`、`MarkdownPdfContext`
- 新增 `MarkdownXhtmlRenderer`、`XhtmlWriter`、`SafeUrlSanitizer`
- 新增 `HtmlPdfRenderEngine`、`PdfFontRegistry`、`PdfFontRegistrationPlan`
- 新增 `ImageResourceResolver`、`LimitedInputStreamHelper`
- 新增 `MdHelper`
- `TemplateEngine` 新增 `.md` 分发、`renderMdToPdf(...)` 私有链路
- 新增 `MdRendererTest`
    - 覆盖循环展开、变量转义、缺失变量替换为空、嵌套循环异常、SDK 图片 visible vs internal token、代码块变量替换
- 新增 `MarkdownSecurityTest`
    - 覆盖 raw HTML 转义、`javascript` / `file` / 混合大小写 scheme 拦截、远程图片拒绝、合法 data URI 通过、MD → PDF 内部 token 不泄露
- 更新 `MarkdownTemplateTest`
    - 生成文件用例补齐文件存在、内容、PDF 头和大小断言
    - MD → PDF 用例从“非空 / %PDF”升级为 PDFBox 提取文本断言标题、循环数据、代码块变量
- 更新 `DocxHelperTest`、`PdfConvertHelperTest`、`PdfOutputHandlerTest` 复用 PDFBox 文本断言

### Compatibility

- 不改变 DOCX 主链路的对外 API 和业务语义
- 不新增 Word 模板语法
- `PdfConvertHelper` 收紧为“已有 DOCX 转 PDF”，不新增 Markdown 入口
- `TemplateRenderResult.output(OutputFormat.PDF)` 首版不支持，抛明确引导异常

### Validation

- `:sdk:template:simple-doc-template-starter:compileJava`：BUILD SUCCESSFUL
- `:sdk:template:simple-doc-template-starter:compileTestJava`：BUILD SUCCESSFUL
- `:sdk:template:simple-doc-template-starter:test`：BUILD SUCCESSFUL（122 tests，Gradle 8.5 + Java 11 + `--no-daemon`）

---
