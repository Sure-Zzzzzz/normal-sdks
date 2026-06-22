# Changelog

## [1.1.0] - 2026-06-22

### Feature

- **新增 PDF 输出能力（Java 自闭环）**
  - 新增 `PdfOutputHandler`，支持将 DOCX 渲染结果转换为 PDF 输出
  - 新增 `TemplateEngine.renderToPdf(...)` 快捷入口
  - 新增 `TemplateEngine.renderForPdf(...)` 链式入口，返回 `PdfRenderResult`
  - 支持 `toBytes()`、`toStream(OutputStream)`、`toFile(...)` 多种 PDF 输出方式
  - PDF 渲染保持 Java 进程内完成，不依赖 wkhtmltopdf 等外部 CLI

- **新增 DOCX → XHTML → PDF 转换链路**
  - 新增 `WordToXhtmlConverter`，基于 Apache POI 将 DOCX 内容转换为 XHTML
  - 支持正文段落、表格、页眉、页脚、图片、段落缩进、行距、字体、字号、加粗、斜体、颜色等常用 Word 样式
  - 支持 Word 页眉以 running element 方式进入 PDF 页面顶部
  - 支持 Word 页脚转换为 PDF `@bottom-center` 内容，页码字段映射为 CSS counter
  - 对 PDF footer 中暂不支持的复杂元素（表格、列表、图片）提供明确异常提示

- **PDF 字体能力增强**
  - PDF 字体来源统一为 `SimpleDocTemplateProperties.fontPaths`
  - 支持从配置的字体文件或目录注册字体
  - 新增 `EmbeddedFontExtractor`，支持提取 DOCX 内嵌 ODTTF 字体并解混淆
  - 新增 `FontNotFoundException`、`EmbeddedFontParseException`，字体缺失和内嵌字体解析失败时返回明确错误

- **PDF 图表能力增强**
  - 新增 `OoxmlChartParser`，解析 DOCX 中的 OOXML 图表定义
  - 新增 `ChartToPngRenderer`，将图表渲染为 PNG 供 PDF 输出使用
  - PDF 输出时将 Word 图表占位转换为图片，避免 PDF 中图表丢失
  - 支持折线图、柱状图、饼图的基础样式渲染
  - 支持系列颜色、数据标签、图例位置、Y 轴范围、主刻度、柱间距、平滑曲线等图表配置

- **新增 Chart 转 PNG 对外能力**
  - 新增 `ChartPngHelper`，业务可直接将 `Chart` 模型渲染为 PNG 字节数组
  - 支持使用 `Chart` 自身宽高，也支持通过方法重载指定输出像素宽高
  - 字体来源与 PDF 输出保持一致，统一使用 `SimpleDocTemplateProperties.fontPaths`

- **Word 样式解析能力增强**
  - 新增 `WordStyleResolver`，统一解析段落样式、run 样式、docDefaults、样式继承链等 Word 样式来源
  - 新增 `NumberingProcessor`，支持解析 `numbering.xml` 并生成列表编号文本
  - 支持 decimal、中文计数、罗马数字、英文字母、bullet 等常见编号格式

### Refactor

- **按后缀语义重组包结构**
  - `processor/condition/**` 重组为 `condition/**`
  - `engine/RendererRegistry` 重组为 `renderer/RendererRegistry`
  - `engine/OutputHandlerRegistry` 重组为 `handler/OutputHandlerRegistry`
  - `engine/TemplateRenderResult` 重组为 `result/TemplateRenderResult`
  - 保持基础包名 `io.github.surezzzzzz.sdk.template.doc` 不变

- **Spring 注入方式规范化**
  - 主代码移除字段注入 `@Autowired`
  - `TemplateEngine`、`TagHelper`、`DocxConditionHandler`、`ConditionHandlerRegistry`、`RendererRegistry`、`OutputHandlerRegistry`、`PdfOutputHandler`、`WordRenderer` 改为 Lombok `@RequiredArgsConstructor` + `final` 字段构造器注入

- **渲染结果结构调整**
  - 新增 `PdfRenderResult`，专用于 PDF 输出链路
  - `TemplateRenderResult` 移入 `result` 包，职责聚焦 DOCX/通用输出结果

- **输出格式枚举清理**
  - 移除未实现 Handler 的 `OutputFormat.HTML` 预留项
  - 移除 `SimpleDocTemplateConstant.FORMAT_HTML`，避免暴露无实际能力的输出格式

### Bug Fix

- **修复 JDK 1.8 兼容问题**
  - 移除 `InputStream.readAllBytes()` 使用，改为 `StreamUtils.copyToByteArray(...)`
  - 主代码和测试代码复扫确认无 JDK 9+ 禁用 API 命中

- **修复直接抛出 JDK 通用异常的问题**
  - `WordDocument.toXWPFDocument()` 不再直接抛 `RuntimeException`，改用 `TemplateRenderException.renderFailed(...)`
  - PDF footer 不支持复杂元素时不再抛 `UnsupportedOperationException`，改用 `PdfFooterUnsupportedException`
  - 内嵌字体 GUID 解析失败不再使用泛化参数异常，改用 `EmbeddedFontParseException`
  - 输出路径参数为空时统一走 `TemplateRenderException.writeFailed(...)`

- **修复 PDF 图表在页眉/页脚中的不支持场景提示**
  - `WordRenderer` 对页眉/页脚中的 chart 占位符明确抛出 `TemplateRenderException.chartInHeaderFooter(...)`
  - 避免后续 PDF 渲染阶段静默丢失或产生不稳定输出

- **修复 PDF 图表图片替换稳定性问题**
  - `PdfOutputHandler` 替换 chart marker 时修正 `a:graphicData` URI，使用 DrawingML picture 命名空间
  - 写入 chart PNG 时确保 `[Content_Types].xml` 包含 `png -> image/png` 默认内容类型
  - 表格单元格中的 chart 占位符在 PDF 路径也会被收集并替换为 PNG
  - 饼图 PNG 渲染在分类数量多于数值数量时跳过缺失值，避免越界异常

- **修复 PDF 转换并发状态串扰风险**
  - `PdfOutputHandler` 不再复用带文档级 mutable state 的 `WordToXhtmlConverter` 实例
  - 每次 PDF 转换使用独立 `WordToXhtmlConverter`，避免并发请求互相覆盖字体、样式、编号和页面宽度状态

- **修复 `renderForPdf` 渲染器缺失时的空指针风险**
  - 找不到 `.docx` renderer 时先抛 `TemplateNotFoundException.rendererNotFound(...)`
  - 避免 `renderer.getClass()` 在 renderer 为 null 时触发 NPE

### 代码质量

- **硬编码收敛**
  - 新增并复用 PDF、字体、图表、Word 样式、临时文件、业务描述等常量
  - `SimpleDocTemplateProperties.enable` 默认值改为引用 `SimpleDocTemplateConstant.DEFAULT_ENABLE`
  - PDF/字体/输出相关错误文案统一收敛到 `ErrorMessage`
  - PDF/字体/输出相关错误码统一收敛到 `ErrorCode`

- **异常体系规范化**
  - 新增 `DocxToPdfFailedException`
  - 新增 `PdfLibLoadException`
  - 新增 `PdfFooterUnsupportedException`
  - 新增 `FontNotFoundException`
  - 新增 `EmbeddedFontParseException`
  - 新增 `ChartPngGenerationException`
  - 移除泛化、含糊的异常命名和使用方式，错误语义按具体业务场景表达

- **Lombok 规范化**
  - 异常类补齐 `@Getter`
  - 测试 helper 中手写构造器改为 `@AllArgsConstructor`
  - 依赖注入类改为 `@RequiredArgsConstructor`
  - `WordStyleResolver` 的 docGrid 只读属性改为 Lombok `@Getter`

- **ChartPngHelper 初始化优化**
  - `ChartPngHelper` 在 Bean 初始化阶段创建 `ChartToPngRenderer`
  - 避免每次 `toPng(...)` 重复扫描字体路径和解析字体文件

- **公共方法注释补齐**
  - `OutputFormat`、`Image.ImageType`、`TemplateRenderException`、`TemplateNotFoundException` 补齐公共方法 JavaDoc
  - `WordStyleResolver`、`NumberingProcessor`、`TagHelper`、`TemplateRenderResult.OutputTarget` 补齐公共方法/构造器 JavaDoc

- **文档完善**
  - README 依赖版本更新为 `1.1.0`
  - README 补充 PDF 输出、`PdfOutputHandler.convertToPdf(...)`、字体配置、Chart PNG、异常说明

- **测试规范清理**
  - 测试类统一使用 `@Slf4j`，移除 `System.out.println`
  - `IndentDebugTest` 重命名并改造为 `WordStyleResolverTest`
  - 测试输出目录从 `src/test/resources/output` 改为 `build/test-output/**`，避免测试运行污染源码资源目录
  - `TestTemplateGenerator` 不再作为 JUnit 测试执行，避免测试阶段自动重写模板资源
  - 测试残留复扫确认无 `@Disabled`、`System.out`、`printStackTrace`、`Thread.sleep`、`TODO/FIXME`、旧 output 路径写入

### 内部变更（无 API 变化）

- `build.gradle`：新增 PDF 渲染、HTML 解析、图表处理等相关依赖
- `SimpleDocTemplateConstant`：新增 PDF、Word 样式、图表、临时文件、业务描述等常量
- `ErrorCode` / `ErrorMessage`：新增 PDF、字体、图表、输出相关错误码和错误文案
- `WordRenderer`：新增 PDF 专用渲染入口 `renderForPdf(...)`，输出 `PdfRenderResult`
- `PdfOutputHandler`：新增 DOCX 到 PDF 输出处理实现
- `WordToXhtmlConverter`：新增 DOCX 到 XHTML 转换实现
- `WordStyleResolver`：新增 Word 样式解析实现
- `NumberingProcessor`：新增 Word 编号解析实现
- `OoxmlChartParser`：新增 OOXML 图表解析实现
- `ChartToPngRenderer`：新增图表 PNG 渲染实现
- `ChartPngHelper`：新增对外 Chart 转 PNG 辅助能力
- `SmoothLineAndShapeRenderer`：新增平滑折线渲染实现
- `EmbeddedFontExtractor`：新增 DOCX 内嵌字体提取实现
- 新增 `PdfOutputHandlerTest`
- 新增 `WordStyleResolverTest`
- 新增 `ChartPngHelperTest`
- 更新 `TemplateEngineRenderTest`、`TagPrefixTest`、`ChartDataHelper`、`TestTemplateGenerator`

### Validation

- `:sdk:template:simple-doc-template-starter:testClasses`：BUILD SUCCESSFUL
- `:sdk:template:simple-doc-template-starter:test`：BUILD SUCCESSFUL

---
