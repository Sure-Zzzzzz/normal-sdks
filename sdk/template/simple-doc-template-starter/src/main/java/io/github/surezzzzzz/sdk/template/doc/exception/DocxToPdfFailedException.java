package io.github.surezzzzzz.sdk.template.doc.exception;

import io.github.surezzzzzz.sdk.template.doc.constant.ErrorCode;
import io.github.surezzzzzz.sdk.template.doc.constant.ErrorMessage;
import lombok.Getter;

/**
 * DOCX → PDF 转换失败异常
 *
 * @author surezzzzzz
 */
@Getter
public class DocxToPdfFailedException extends TemplateRenderException {

    private static final long serialVersionUID = 1L;

    public DocxToPdfFailedException(String errorCode, String message) {
        super(errorCode, message);
    }

    public DocxToPdfFailedException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    /**
     * DOCX → PDF 转换失败
     *
     * @param message 失败原因
     * @param cause   原始异常
     */
    public static DocxToPdfFailedException conversionFailed(String message, Throwable cause) {
        return new DocxToPdfFailedException(
                ErrorCode.PDF_CONVERSION_FAILED,
                String.format(ErrorMessage.PDF_CONVERSION_FAILED, message),
                cause
        );
    }
}
