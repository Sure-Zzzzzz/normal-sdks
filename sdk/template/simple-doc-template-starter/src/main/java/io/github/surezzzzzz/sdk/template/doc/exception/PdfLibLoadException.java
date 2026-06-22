package io.github.surezzzzzz.sdk.template.doc.exception;

import io.github.surezzzzzz.sdk.template.doc.constant.ErrorCode;
import io.github.surezzzzzz.sdk.template.doc.constant.ErrorMessage;
import lombok.Getter;

/**
 * PDF 库加载/初始化失败异常
 *
 * @author surezzzzzz
 */
@Getter
public class PdfLibLoadException extends TemplateRenderException {

    private static final long serialVersionUID = 1L;

    public PdfLibLoadException(String errorCode, String message) {
        super(errorCode, message);
    }

    public PdfLibLoadException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    /**
     * PDF 库加载/初始化失败
     *
     * @param libName 库名（如 openhtmltopdf）
     * @param cause   原始异常
     */
    public static PdfLibLoadException loadFailed(String libName, Throwable cause) {
        return new PdfLibLoadException(
                ErrorCode.PDF_LIB_LOAD_FAILED,
                String.format(ErrorMessage.PDF_LIB_LOAD_FAILED, libName, cause.getMessage()),
                cause
        );
    }
}
