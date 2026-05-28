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
     * 写出失败
     */
    public static final String OUTPUT_WRITE_FAILED = "OUTPUT_003";

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
}