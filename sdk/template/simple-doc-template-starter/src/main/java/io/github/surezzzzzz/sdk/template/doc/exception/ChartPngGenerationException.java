package io.github.surezzzzzz.sdk.template.doc.exception;

import io.github.surezzzzzz.sdk.template.doc.constant.ErrorCode;
import io.github.surezzzzzz.sdk.template.doc.constant.ErrorMessage;
import lombok.Getter;

/**
 * Chart PNG 生成失败异常
 *
 * @author surezzzzzz
 */
@Getter
public class ChartPngGenerationException extends TemplateRenderException {

    private static final long serialVersionUID = 1L;

    public ChartPngGenerationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ChartPngGenerationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    /**
     * Chart PNG 生成失败
     *
     * @param chartKey chart 占位符 key
     * @param message  失败原因
     * @param cause    原始异常
     */
    public static ChartPngGenerationException failed(String chartKey, String message, Throwable cause) {
        return new ChartPngGenerationException(
                ErrorCode.PDF_CHART_PNG_FAILED,
                String.format(ErrorMessage.PDF_CHART_PNG_FAILED, chartKey, message),
                cause
        );
    }
}
