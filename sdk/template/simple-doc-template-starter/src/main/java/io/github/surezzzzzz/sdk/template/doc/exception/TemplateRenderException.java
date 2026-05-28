package io.github.surezzzzzz.sdk.template.doc.exception;

import io.github.surezzzzzz.sdk.template.doc.constant.ErrorCode;
import io.github.surezzzzzz.sdk.template.doc.constant.ErrorMessage;

/**
 * Template Render Exception
 *
 * @author surezzzzzz
 */
public class TemplateRenderException extends SimpleDocTemplateException {

    private static final long serialVersionUID = 1L;

    public TemplateRenderException(String errorCode, String message) {
        super(errorCode, message);
    }

    public TemplateRenderException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public static TemplateRenderException outputHandlerNotFound(String formatCode) {
        return new TemplateRenderException(
            ErrorCode.OUTPUT_HANDLER_NOT_FOUND,
            String.format(ErrorMessage.OUTPUT_HANDLER_NOT_FOUND, formatCode)
        );
    }

    public static TemplateRenderException formatMismatch(String expected, String actual) {
        return new TemplateRenderException(
            ErrorCode.OUTPUT_FORMAT_MISMATCH,
            String.format(ErrorMessage.OUTPUT_FORMAT_MISMATCH, expected, actual)
        );
    }

    public static TemplateRenderException writeFailed(Throwable cause) {
        return new TemplateRenderException(
            ErrorCode.OUTPUT_WRITE_FAILED,
            ErrorMessage.OUTPUT_WRITE_FAILED,
            cause
        );
    }

    public static TemplateRenderException renderFailed(String message, Throwable cause) {
        return new TemplateRenderException(
            ErrorCode.RENDER_FAILED,
            String.format(ErrorMessage.RENDER_FAILED, message),
            cause
        );
    }
}