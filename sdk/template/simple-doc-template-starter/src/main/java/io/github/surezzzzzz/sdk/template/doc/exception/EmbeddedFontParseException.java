package io.github.surezzzzzz.sdk.template.doc.exception;

import io.github.surezzzzzz.sdk.template.doc.constant.ErrorCode;
import io.github.surezzzzzz.sdk.template.doc.constant.ErrorMessage;
import lombok.Getter;

/**
 * 内嵌字体解析异常
 *
 * @author surezzzzzz
 */
@Getter
public class EmbeddedFontParseException extends SimpleDocTemplateException {

    private static final long serialVersionUID = 1L;

    public EmbeddedFontParseException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 内嵌字体解析失败
     *
     * @param reason 具体错误描述
     * @return 异常实例
     */
    public static EmbeddedFontParseException failed(String reason) {
        return new EmbeddedFontParseException(ErrorCode.EMBEDDED_FONT_PARSE_ERROR,
                String.format(ErrorMessage.EMBEDDED_FONT_PARSE_ERROR, reason));
    }
}
