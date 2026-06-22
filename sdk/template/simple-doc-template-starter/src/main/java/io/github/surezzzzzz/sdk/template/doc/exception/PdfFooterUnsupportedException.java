package io.github.surezzzzzz.sdk.template.doc.exception;

import io.github.surezzzzzz.sdk.template.doc.constant.ErrorCode;
import io.github.surezzzzzz.sdk.template.doc.constant.ErrorMessage;
import lombok.Getter;

/**
 * PDF Footer 不支持某元素异常
 *
 * @author surezzzzzz
 */
@Getter
public class PdfFooterUnsupportedException extends SimpleDocTemplateException {

    private static final long serialVersionUID = 1L;

    public PdfFooterUnsupportedException(String errorCode, String message) {
        super(errorCode, message);
    }

    public PdfFooterUnsupportedException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    /**
     * footer 不支持某元素
     *
     * @param element 元素描述，如"表格"、"列表"、"图片"
     * @return 异常实例
     */
    public static PdfFooterUnsupportedException unsupported(String element) {
        return new PdfFooterUnsupportedException(ErrorCode.PDF_FOOTER_UNSUPPORTED,
                String.format(ErrorMessage.PDF_FOOTER_UNSUPPORTED, element));
    }
}
