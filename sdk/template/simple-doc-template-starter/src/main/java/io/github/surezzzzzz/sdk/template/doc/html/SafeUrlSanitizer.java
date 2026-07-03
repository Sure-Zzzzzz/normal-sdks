package io.github.surezzzzzz.sdk.template.doc.html;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateRenderException;

import java.util.Locale;

/**
 * Safe URL Sanitizer
 *
 * @author surezzzzzz
 */
@SimpleDocTemplateComponent
public class SafeUrlSanitizer {

    /**
     * 校验链接 href。
     *
     * @param href href
     * @return 安全 href
     */
    public String sanitizeLinkHref(String href) {
        if (href == null) {
            return SimpleDocTemplateConstant.EMPTY;
        }
        String trimmed = href.trim();
        rejectControl(trimmed);
        String scheme = scheme(trimmed);
        if (scheme.isEmpty()
                || SimpleDocTemplateConstant.URL_SCHEME_HTTP.equals(scheme)
                || SimpleDocTemplateConstant.URL_SCHEME_HTTPS.equals(scheme)) {
            return trimmed;
        }
        throw TemplateRenderException.markdownSecurityRejected("不允许的链接 scheme: " + scheme);
    }

    /**
     * 校验图片 src。
     *
     * @param src 图片 src
     * @return 安全 src
     */
    public String sanitizeImageSrc(String src) {
        if (src == null) {
            return SimpleDocTemplateConstant.EMPTY;
        }
        String trimmed = src.trim();
        rejectControl(trimmed);
        String scheme = scheme(trimmed);
        if (scheme.isEmpty() || SimpleDocTemplateConstant.URL_SCHEME_DATA.equals(scheme)) {
            return trimmed;
        }
        throw TemplateRenderException.markdownSecurityRejected("不允许的图片 scheme: " + scheme);
    }

    private void rejectControl(String url) {
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                throw TemplateRenderException.markdownSecurityRejected("URL 包含控制字符");
            }
        }
    }

    private String scheme(String url) {
        int colon = url.indexOf(':');
        if (colon <= 0) {
            return SimpleDocTemplateConstant.EMPTY;
        }
        String scheme = url.substring(0, colon).toLowerCase(Locale.ROOT);
        return scheme.matches("[a-z][a-z0-9+.-]*") ? scheme : SimpleDocTemplateConstant.EMPTY;
    }
}
