package io.github.surezzzzzz.sdk.template.doc.exception;

import io.github.surezzzzzz.sdk.template.doc.constant.ErrorCode;
import io.github.surezzzzzz.sdk.template.doc.constant.ErrorMessage;
import lombok.Getter;

/**
 * 字体不存在异常（PDF/Chart 渲染所需字体缺失）
 *
 * @author surezzzzzz
 */
@Getter
public class FontNotFoundException extends TemplateRenderException {

    private static final long serialVersionUID = 1L;

    public FontNotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }

    public FontNotFoundException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    /**
     * 系统缺少中文字体
     *
     * @param fontNames 缺失的字体名称（如 Microsoft YaHei / PingFang SC / WenQuanYi Micro Hei / SimHei）
     */
    public static FontNotFoundException unsupported(String fontNames) {
        return new FontNotFoundException(
                ErrorCode.PDF_FONT_NOT_FOUND,
                String.format(ErrorMessage.PDF_FONT_NOT_FOUND, fontNames)
        );
    }
}
