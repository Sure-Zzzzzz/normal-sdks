package io.github.surezzzzzz.sdk.template.doc.exception;

import io.github.surezzzzzz.sdk.template.doc.constant.ErrorCode;
import io.github.surezzzzzz.sdk.template.doc.constant.ErrorMessage;
import lombok.Getter;

/**
 * Template Render Exception
 *
 * @author surezzzzzz
 */
@Getter
public class TemplateRenderException extends SimpleDocTemplateException {

    private static final long serialVersionUID = 1L;

    public TemplateRenderException(String errorCode, String message) {
        super(errorCode, message);
    }

    public TemplateRenderException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    /**
     * 输出策略未注册
     *
     * @param formatCode 输出格式代码
     * @return 异常实例
     */
    public static TemplateRenderException outputHandlerNotFound(String formatCode) {
        return new TemplateRenderException(
                ErrorCode.OUTPUT_HANDLER_NOT_FOUND,
                String.format(ErrorMessage.OUTPUT_HANDLER_NOT_FOUND, formatCode)
        );
    }

    /**
     * 输出格式不匹配
     *
     * @param expected 期望格式
     * @param actual   实际格式
     * @return 异常实例
     */
    public static TemplateRenderException formatMismatch(String expected, String actual) {
        return new TemplateRenderException(
                ErrorCode.OUTPUT_FORMAT_MISMATCH,
                String.format(ErrorMessage.OUTPUT_FORMAT_MISMATCH, expected, actual)
        );
    }

    /**
     * 渲染器类型不匹配
     *
     * @param expected 期望类型
     * @param actual   实际类型
     * @return 异常实例
     */
    public static TemplateRenderException rendererTypeMismatch(String expected, String actual) {
        return new TemplateRenderException(
                ErrorCode.RENDERER_TYPE_MISMATCH,
                String.format(ErrorMessage.RENDERER_TYPE_MISMATCH, expected, actual)
        );
    }

    /**
     * 页眉/页脚中不支持 chart 占位符
     *
     * @param position 位置描述
     * @return 异常实例
     */
    public static TemplateRenderException chartInHeaderFooter(String position) {
        return new TemplateRenderException(
                ErrorCode.CHART_IN_HEADER_FOOTER,
                String.format(ErrorMessage.CHART_IN_HEADER_FOOTER, position)
        );
    }

    /**
     * 写出失败
     *
     * @param cause 原始异常
     * @return 异常实例
     */
    public static TemplateRenderException writeFailed(Throwable cause) {
        return new TemplateRenderException(
                ErrorCode.OUTPUT_WRITE_FAILED,
                String.format(ErrorMessage.OUTPUT_WRITE_FAILED, cause.getMessage()),
                cause
        );
    }

    /**
     * 写出失败
     *
     * @param reason 失败原因
     * @return 异常实例
     */
    public static TemplateRenderException writeFailed(String reason) {
        return new TemplateRenderException(
                ErrorCode.OUTPUT_WRITE_FAILED,
                String.format(ErrorMessage.OUTPUT_WRITE_FAILED, reason)
        );
    }

    /**
     * 模板渲染失败
     *
     * @param message 失败原因
     * @param cause   原始异常
     * @return 异常实例
     */
    public static TemplateRenderException renderFailed(String message, Throwable cause) {
        return new TemplateRenderException(
                ErrorCode.RENDER_FAILED,
                String.format(ErrorMessage.RENDER_FAILED, message),
                cause
        );
    }
}