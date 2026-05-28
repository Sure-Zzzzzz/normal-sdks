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
     * 写出失败
     */
    public static final String OUTPUT_WRITE_FAILED = "文档写出失败";

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
}