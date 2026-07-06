package io.github.surezzzzzz.sdk.template.doc.constant;

/**
 * Error Message Constants
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 渲染相关 ====================

    /**
     * 模板渲染失败，参数：异常信息
     */
    public static final String RENDER_FAILED = "模板渲染失败: %s";

    /**
     * 页眉/页脚中不支持 chart 占位符，参数：位置（页眉/页脚）
     */
    public static final String CHART_IN_HEADER_FOOTER = "%s中不支持 chart 占位符，请将 chart 放到正文段落中";

    // ==================== 模板相关 ====================

    /**
     * 模板文件未找到，参数：模板路径
     */
    public static final String TEMPLATE_NOT_FOUND = "模板文件未找到: %s";

    /**
     * 渲染策略未注册，参数：文件后缀
     */
    public static final String RENDERER_NOT_FOUND = "不支持的模板后缀: %s，尚未注册对应的渲染策略";

    // ==================== 输出相关 ====================

    /**
     * 输出策略未注册，参数：输出格式代码
     */
    public static final String OUTPUT_HANDLER_NOT_FOUND = "不支持的输出格式: %s，尚未注册对应的输出策略";

    /**
     * 格式不匹配，参数：期望格式，实际格式
     */
    public static final String OUTPUT_FORMAT_MISMATCH = "输出格式不匹配：期望 %s，实际 %s";

    /**
     * 渲染器类型不匹配，参数：期望类型，实际类型
     */
    public static final String RENDERER_TYPE_MISMATCH = "渲染器类型不匹配：期望 %s，实际 %s";

    /**
     * 写出失败，参数：错误原因
     */
    public static final String OUTPUT_WRITE_FAILED = "文档写出失败: %s";

    /**
     * WordDocument 字节解析失败
     */
    public static final String WORD_DOCUMENT_PARSE_FAILED = "WordDocument 字节解析失败";

    /**
     * 输出路径参数为空
     */
    public static final String OUTPUT_PATH_ARGUMENT_EMPTY = "dir and fileName must not be null";

    /**
     * 内嵌字体解析错误，参数：具体错误描述
     */
    public static final String EMBEDDED_FONT_PARSE_ERROR = "内嵌字体解析失败: %s";

    /**
     * 内嵌字体 GUID 格式错误，参数：GUID
     */
    public static final String EMBEDDED_FONT_GUID_INVALID = "Invalid GUID format: %s";

    /**
     * PDF footer 不支持某元素，参数：元素描述
     */
    public static final String PDF_FOOTER_UNSUPPORTED = "%s不支持放入 PDF footer，请简化 footer 或改用 DOCX 输出（OutputFormat.DOCX）";

    /**
     * 输出 PDF 格式暂不支持链式 output，参数：推荐入口说明
     */
    public static final String PDF_OUTPUT_NOT_SUPPORTED = "TemplateRenderResult.output(OutputFormat.PDF) 暂不支持。请使用 %s";

    /**
     * Markdown 不支持的能力，参数：能力描述
     */
    public static final String MD_UNSUPPORTED_FEATURE = "Markdown 不支持的能力: %s";

    /**
     * Markdown 模板渲染失败，参数：原因
     */
    public static final String MD_RENDER_FAILED = "Markdown 模板渲染失败: %s";

    /**
     * Markdown 转 HTML 失败，参数：原因
     */
    public static final String MD_TO_HTML_FAILED = "Markdown 转 HTML 失败: %s";

    /**
     * Markdown 转 PDF 失败，参数：原因
     */
    public static final String MD_TO_PDF_FAILED = "Markdown 转 PDF 失败: %s";

    /**
     * Markdown 安全校验拒绝，参数：原因
     */
    public static final String MD_SECURITY_REJECTED = "Markdown 安全校验拒绝: %s";

    /**
     * HTML/XHTML 转 PDF 失败，参数：原因
     */
    public static final String HTML_TO_PDF_FAILED = "HTML/XHTML 转 PDF 失败: %s";

    /**
     * 推荐的 PDF 输出入口说明
     */
    public static final String PDF_OUTPUT_RECOMMENDED_ENTRY = "TemplateEngine.renderToPdf(...)、DocxHelper.renderPdf(...) 或 MdHelper.renderPdf(...)";

    /**
     * MdHelper 后缀错误
     */
    public static final String MD_HELPER_SUFFIX_ONLY = "MdHelper 仅支持 .md 模板";

    /**
     * DocxHelper 后缀错误
     */
    public static final String DOCX_HELPER_SUFFIX_ONLY = "DocxHelper 仅支持 .docx 模板";

    // ==================== SDK 条件块相关 ====================

    /**
     * 条件块标记不匹配，参数：prefix，start key，prefix，end key
     */
    public static final String CONDITION_BLOCK_MISMATCH = "条件块 [%s.start:%s] 与 [%s.end:%s] 的 key 不匹配";

    /**
     * 条件块嵌套，参数：嵌套的 key
     */
    public static final String CONDITION_BLOCK_NESTED = "条件块不允许嵌套，检测到嵌套：[%s]";

    /**
     * 条件块处理失败，参数：异常信息
     */
    public static final String CONDITION_PROCESS_FAILED = "条件块处理失败: %s";

    // ==================== Markdown 循环相关（1.2.1）====================

    /**
     * 孤立的 endfor 标签，参数：标签原文
     */
    public static final String MD_LOOP_ORPHAN_ENDFOR = "孤立的 endfor 标签: %s";

    /**
     * 循环项必须是 Map，参数：循环 key
     */
    public static final String MD_LOOP_ITEM_NOT_MAP = "循环项必须是 Map: %s";

    /**
     * 循环标签 key 不匹配，参数：for 的 key，endfor 的 key
     */
    public static final String MD_LOOP_KEY_MISMATCH = "循环标签 key 不匹配: %s / %s";

    /**
     * 缺少 endfor 标签，参数：循环 key
     */
    public static final String MD_LOOP_MISSING_ENDFOR = "缺少 endfor 标签: %s";

    // ==================== PDF 相关（1.1.0）====================

    /**
     * DOCX → PDF 转换失败，参数：异常信息
     */
    public static final String PDF_CONVERSION_FAILED = "DOCX 转 PDF 失败: %s";

    /**
     * POI 加载 DOCX 失败，参数：异常信息
     */
    public static final String PDF_POI_DOCX_LOAD_FAILED = "POI 加载 DOCX 失败: %s";

    /**
     * PDF 文件写出失败，参数：文件路径
     */
    public static final String PDF_FILE_WRITE_FAILED = "PDF 文件写出失败: %s";

    /**
     * PDF 流写出失败
     */
    public static final String PDF_STREAM_WRITE_FAILED = "PDF 流写出失败";

    /**
     * DOCX 缺少关键 XML 或 rels 文件
     */
    public static final String PDF_DOCX_CORE_PART_MISSING = "DOCX 缺少 document.xml 或 rels 文件";

    /**
     * chart 图片替换失败，参数：异常信息
     */
    public static final String PDF_CHART_IMAGE_REPLACE_FAILED = "chart 图片替换失败: %s";

    /**
     * 字体不存在，参数：缺失字体名称
     */
    public static final String PDF_FONT_NOT_FOUND = "系统缺少中文字体，PDF 输出可能异常，请安装 %s";

    /**
     * Chart PNG 生成失败，参数：chart key，异常信息
     */
    public static final String PDF_CHART_PNG_FAILED = "chart [%s] PNG 生成失败: %s";

    /**
     * PDF 库加载/初始化失败，参数：库名，异常信息
     */
    public static final String PDF_LIB_LOAD_FAILED = "PDF 库 [%s] 加载失败: %s";

    // ==================== 图片资源相关（1.2.0）====================

    /**
     * 图片对象为空
     */
    public static final String IMAGE_NULL = "图片不能为空";

    /**
     * 图片路径为空
     */
    public static final String IMAGE_SRC_EMPTY = "图片路径不能为空";

    /**
     * 原生 Markdown 图片不支持绝对 URI，参数：src
     */
    public static final String IMAGE_ABSOLUTE_URI_REJECTED = "原生 Markdown 图片不支持绝对 URI: %s";

    /**
     * 不允许的图片 scheme，参数：scheme
     */
    public static final String IMAGE_SCHEME_REJECTED = "不允许的图片 scheme: %s";

    /**
     * 图片路径逃逸，参数：src
     */
    public static final String IMAGE_PATH_TRAVERSAL = "图片路径逃逸: %s";

    /**
     * 缺少 baseUri 无法解析相对图片，参数：src
     */
    public static final String IMAGE_BASE_URI_MISSING = "缺少 baseUri，无法解析相对图片: %s";

    /**
     * 无效的 baseUri，参数：baseUri
     */
    public static final String IMAGE_BASE_URI_INVALID = "无效的 baseUri: %s";

    /**
     * classpath baseUri 不应走文件解析，参数：src
     */
    public static final String IMAGE_CLASSPATH_BASE_MISUSE = "classpath baseUri 不应走文件解析: %s";

    /**
     * 无效的 file URI，参数：src
     */
    public static final String IMAGE_FILE_URI_INVALID = "无效的 file URI: %s";

    /**
     * 图片不存在，参数：路径
     */
    public static final String IMAGE_NOT_FOUND = "图片不存在: %s";

    /**
     * 读取图片失败，参数：路径
     */
    public static final String IMAGE_READ_FAILED = "读取图片失败: %s";

    /**
     * 图片 data URI 仅支持 base64
     */
    public static final String IMAGE_DATA_URI_BASE64_ONLY = "图片 data URI 仅支持 base64";

    /**
     * data URI 必须是 image MIME
     */
    public static final String IMAGE_DATA_URI_MIME_INVALID = "data URI 必须是 image MIME";

    /**
     * 图片 data URI base64 非法
     */
    public static final String IMAGE_DATA_URI_BASE64_INVALID = "图片 data URI base64 非法";
}