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
     * PDF 链式输出暂不支持
     *
     * @return 异常实例
     */
    public static TemplateRenderException pdfOutputNotSupported() {
        return new TemplateRenderException(
                ErrorCode.PDF_OUTPUT_NOT_SUPPORTED,
                String.format(ErrorMessage.PDF_OUTPUT_NOT_SUPPORTED, ErrorMessage.PDF_OUTPUT_RECOMMENDED_ENTRY)
        );
    }

    /**
     * Markdown 不支持的能力
     *
     * @param feature 能力描述
     * @return 异常实例
     */
    public static TemplateRenderException markdownUnsupportedFeature(String feature) {
        return new TemplateRenderException(
                ErrorCode.MARKDOWN_UNSUPPORTED_FEATURE,
                String.format(ErrorMessage.MD_UNSUPPORTED_FEATURE, feature)
        );
    }

    /**
     * Markdown 模板渲染失败
     *
     * @param message 失败原因
     * @return 异常实例
     */
    public static TemplateRenderException markdownRenderFailed(String message) {
        return new TemplateRenderException(
                ErrorCode.MARKDOWN_RENDER_FAILED,
                String.format(ErrorMessage.MD_RENDER_FAILED, message)
        );
    }

    /**
     * Markdown 模板渲染失败
     *
     * @param message 失败原因
     * @param cause   原始异常
     * @return 异常实例
     */
    public static TemplateRenderException markdownRenderFailed(String message, Throwable cause) {
        return new TemplateRenderException(
                ErrorCode.MARKDOWN_RENDER_FAILED,
                String.format(ErrorMessage.MD_RENDER_FAILED, message),
                cause
        );
    }

    /**
     * Markdown 转 HTML 失败
     *
     * @param message 失败原因
     * @return 异常实例
     */
    public static TemplateRenderException markdownToHtmlFailed(String message) {
        return new TemplateRenderException(
                ErrorCode.MARKDOWN_TO_HTML_FAILED,
                String.format(ErrorMessage.MD_TO_HTML_FAILED, message)
        );
    }

    /**
     * Markdown 转 HTML 失败
     *
     * @param message 失败原因
     * @param cause   原始异常
     * @return 异常实例
     */
    public static TemplateRenderException markdownToHtmlFailed(String message, Throwable cause) {
        return new TemplateRenderException(
                ErrorCode.MARKDOWN_TO_HTML_FAILED,
                String.format(ErrorMessage.MD_TO_HTML_FAILED, message),
                cause
        );
    }

    /**
     * Markdown 转 PDF 失败
     *
     * @param message 失败原因
     * @return 异常实例
     */
    public static TemplateRenderException markdownToPdfFailed(String message) {
        return new TemplateRenderException(
                ErrorCode.MARKDOWN_TO_PDF_FAILED,
                String.format(ErrorMessage.MD_TO_PDF_FAILED, message)
        );
    }

    /**
     * Markdown 转 PDF 失败
     *
     * @param message 失败原因
     * @param cause   原始异常
     * @return 异常实例
     */
    public static TemplateRenderException markdownToPdfFailed(String message, Throwable cause) {
        return new TemplateRenderException(
                ErrorCode.MARKDOWN_TO_PDF_FAILED,
                String.format(ErrorMessage.MD_TO_PDF_FAILED, message),
                cause
        );
    }

    /**
     * Markdown 安全校验拒绝
     *
     * @param message 失败原因
     * @return 异常实例
     */
    public static TemplateRenderException markdownSecurityRejected(String message) {
        return new TemplateRenderException(
                ErrorCode.MARKDOWN_SECURITY_REJECTED,
                String.format(ErrorMessage.MD_SECURITY_REJECTED, message)
        );
    }

    /**
     * HTML/XHTML 转 PDF 失败
     *
     * @param message 失败原因
     * @param cause   原始异常
     * @return 异常实例
     */
    public static TemplateRenderException htmlToPdfFailed(String message, Throwable cause) {
        return new TemplateRenderException(
                ErrorCode.HTML_TO_PDF_FAILED,
                String.format(ErrorMessage.HTML_TO_PDF_FAILED, message),
                cause
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