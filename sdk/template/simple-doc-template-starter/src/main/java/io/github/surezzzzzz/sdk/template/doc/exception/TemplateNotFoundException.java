package io.github.surezzzzzz.sdk.template.doc.exception;

import io.github.surezzzzzz.sdk.template.doc.constant.ErrorCode;
import io.github.surezzzzzz.sdk.template.doc.constant.ErrorMessage;
import lombok.Getter;

/**
 * Template Not Found Exception
 *
 * @author surezzzzzz
 */
@Getter
public class TemplateNotFoundException extends SimpleDocTemplateException {

    private static final long serialVersionUID = 1L;

    public TemplateNotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }

    public TemplateNotFoundException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    /**
     * 模板文件未找到
     *
     * @param templateLocation 模板路径
     * @return 异常实例
     */
    public static TemplateNotFoundException notFound(String templateLocation) {
        return new TemplateNotFoundException(
                ErrorCode.TEMPLATE_NOT_FOUND,
                String.format(ErrorMessage.TEMPLATE_NOT_FOUND, templateLocation)
        );
    }

    /**
     * 渲染策略未注册
     *
     * @param suffix 文件后缀
     * @return 异常实例
     */
    public static TemplateNotFoundException rendererNotFound(String suffix) {
        return new TemplateNotFoundException(
                ErrorCode.RENDERER_NOT_FOUND,
                String.format(ErrorMessage.RENDERER_NOT_FOUND, suffix)
        );
    }
}