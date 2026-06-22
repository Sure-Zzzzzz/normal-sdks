package io.github.surezzzzzz.sdk.template.doc.constant;

/**
 * Error Code Constants
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    private ErrorCode() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 渲染相关 ====================

    /**
     * 模板渲染失败
     */
    public static final String RENDER_FAILED = "RENDER_001";

    // ==================== 模板相关 ====================

    /**
     * 模板文件未找到
     */
    public static final String TEMPLATE_NOT_FOUND = "TEMPLATE_001";

    /**
     * 渲染策略未注册
     */
    public static final String RENDERER_NOT_FOUND = "TEMPLATE_002";

    // ==================== 输出相关 ====================

    /**
     * 输出策略未注册
     */
    public static final String OUTPUT_HANDLER_NOT_FOUND = "OUTPUT_001";

    /**
     * 格式不匹配（如把 MD Document 交给 DOCX Handler）
     */
    public static final String OUTPUT_FORMAT_MISMATCH = "OUTPUT_002";

    /**
     * 渲染器类型不匹配
     */
    public static final String RENDERER_TYPE_MISMATCH = "OUTPUT_004";

    /**
     * 写出失败
     */
    public static final String OUTPUT_WRITE_FAILED = "OUTPUT_003";

    /**
     * 内嵌字体解析错误（如 GUID 格式非法）
     */
    public static final String EMBEDDED_FONT_PARSE_ERROR = "FONT_001";

    /**
     * PDF footer 不支持某元素（表格/列表/图片）
     */
    public static final String PDF_FOOTER_UNSUPPORTED = "PDF_005";

    /**
     * 页眉/页脚中不支持 chart 占位符
     */
    public static final String CHART_IN_HEADER_FOOTER = "RENDER_002";

    // ==================== SDK 条件块相关 ====================

    /**
     * 条件块标记不匹配（start/end key 不一致）
     */
    public static final String CONDITION_BLOCK_MISMATCH = "COND_001";

    /**
     * 条件块嵌套（不允许嵌套条件块）
     */
    public static final String CONDITION_BLOCK_NESTED = "COND_002";

    /**
     * 条件块处理失败（DOCX 解析异常等）
     */
    public static final String CONDITION_PROCESS_FAILED = "COND_003";

    // ==================== PDF 相关（1.1.0）====================

    /**
     * DOCX → PDF 转换失败
     */
    public static final String PDF_CONVERSION_FAILED = "PDF_001";

    /**
     * 字体不存在（PDF/Chart 渲染所需字体缺失）
     */
    public static final String PDF_FONT_NOT_FOUND = "PDF_002";

    /**
     * Chart PNG 生成失败
     */
    public static final String PDF_CHART_PNG_FAILED = "PDF_003";

    /**
     * PDF 库加载/初始化失败
     */
    public static final String PDF_LIB_LOAD_FAILED = "PDF_004";
}