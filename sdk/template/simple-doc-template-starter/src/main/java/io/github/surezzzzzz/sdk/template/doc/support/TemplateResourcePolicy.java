package io.github.surezzzzzz.sdk.template.doc.support;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.configuration.SimpleDocTemplateProperties;
import io.github.surezzzzzz.sdk.template.doc.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateRenderException;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

/**
 * Template Resource Policy
 *
 * <p>统一资源安全策略。
 *
 * @author surezzzzzz
 */
@SimpleDocTemplateComponent
@RequiredArgsConstructor
public class TemplateResourcePolicy {

    private final SimpleDocTemplateProperties properties;
    private final TemplateLocationHelper locationHelper;

    /**
     * 校验模板资源是否允许读取。
     *
     * @param location 模板 location
     */
    public void validateTemplateLocation(String location) {
        if (isRemote(location) && !properties.isAllowRemoteResource()) {
            throw TemplateRenderException.markdownSecurityRejected(
                    String.format(ErrorMessage.REMOTE_TEMPLATE_DISABLED, location));
        }
    }

    /**
     * 校验图片资源是否允许读取。
     *
     * @param src 图片路径
     */
    public void validateImageSource(String src) {
        if (isRemote(src) && !properties.isAllowRemoteResource()) {
            throw TemplateRenderException.markdownSecurityRejected(
                    String.format(ErrorMessage.REMOTE_IMAGE_DISABLED, src));
        }
    }

    /**
     * 判断是否为远程资源。
     *
     * @param location 资源路径
     * @return true 表示远程资源
     */
    public boolean isRemote(String location) {
        String scheme = schemeOf(location);
        return SimpleDocTemplateConstant.URL_SCHEME_HTTP.equals(scheme)
                || SimpleDocTemplateConstant.URL_SCHEME_HTTPS.equals(scheme);
    }

    /**
     * 获取 scheme。
     *
     * @param location 资源路径
     * @return 小写 scheme，无 scheme 返回空字符串
     */
    public String schemeOf(String location) {
        if (location == null || locationHelper.isWindowsAbsolutePath(location)) {
            return SimpleDocTemplateConstant.EMPTY;
        }
        int colon = location.indexOf(':');
        if (colon <= 0) {
            return SimpleDocTemplateConstant.EMPTY;
        }
        String scheme = location.substring(0, colon).toLowerCase(Locale.ROOT);
        return scheme.matches("[a-z][a-z0-9+.-]*") ? scheme : SimpleDocTemplateConstant.EMPTY;
    }
}
