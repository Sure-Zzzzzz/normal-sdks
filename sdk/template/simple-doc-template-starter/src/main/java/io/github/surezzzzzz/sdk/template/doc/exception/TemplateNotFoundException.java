package io.github.surezzzzzz.sdk.template.doc.exception;

import io.github.surezzzzzz.sdk.template.doc.constant.ErrorCode;
import io.github.surezzzzzz.sdk.template.doc.constant.ErrorMessage;

/**
 * Template Not Found Exception
 *
 * @author surezzzzzz
 */
public class TemplateNotFoundException extends SimpleDocTemplateException {

    private static final long serialVersionUID = 1L;

    public TemplateNotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }

    public TemplateNotFoundException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public static TemplateNotFoundException notFound(String templateLocation) {
        return new TemplateNotFoundException(
            ErrorCode.TEMPLATE_NOT_FOUND,
            String.format(ErrorMessage.TEMPLATE_NOT_FOUND, templateLocation)
        );
    }

    public static TemplateNotFoundException rendererNotFound(String suffix) {
        return new TemplateNotFoundException(
            ErrorCode.RENDERER_NOT_FOUND,
            String.format(ErrorMessage.RENDERER_NOT_FOUND, suffix)
        );
    }
}