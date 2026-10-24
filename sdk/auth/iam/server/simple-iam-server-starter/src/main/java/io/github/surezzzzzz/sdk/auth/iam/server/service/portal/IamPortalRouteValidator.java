package io.github.surezzzzzz.sdk.auth.iam.server.service.portal;

import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Portal 路由字段的读写共用校验器。
 *
 * <p>机器目录和管理写入口必须使用相同的 URL 边界，避免写入可保存但不可投影的配置。</p>
 */
public final class IamPortalRouteValidator {

    private static final int MAX_ROUTE_PREFIX_CODE_POINTS = 128;
    private static final int MAX_URL_CODE_POINTS = 512;

    private IamPortalRouteValidator() {
        // 工具类不允许实例化。
    }

    public static boolean isValidRoutePrefix(String value) {
        return isValidPath(value, 2) && !value.endsWith("/");
    }

    public static boolean isValidApiBase(String value) {
        return StringUtils.hasText(value) && isValidPath(value, 1);
    }

    /**
     * 校验浏览器可见的页面入口路径。
     */
    public static boolean isValidPublicEntry(String value) {
        return StringUtils.hasText(value) && isValidPath(value, 1);
    }

    public static boolean isValidPublicEntry(String routePrefix, String entry) {
        return isValidRoutePrefix(routePrefix) && isValidPublicEntry(entry)
                && (entry.equals(routePrefix) || entry.startsWith(routePrefix + "/"));
    }

    /**
     * 校验浏览器可见的 API 公共路径。API 不允许另登记一套机器 origin。
     */
    public static boolean isValidPublicApiBase(String value) {
        return StringUtils.hasText(value) && isValidPath(value, 1);
    }

    /**
     * 校验业务自有 Nginx 的唯一机器目标。机器目标只允许无路径的 HTTP origin。
     */
    public static boolean isValidTargetOrigin(String value) {
        if (!StringUtils.hasText(value) || value.codePointCount(0, value.length()) > MAX_URL_CODE_POINTS
                || value.indexOf('\\') >= 0 || hasUnsafeCharacters(value)) {
            return false;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            return uri.isAbsolute() && "http".equalsIgnoreCase(scheme)
                    && StringUtils.hasText(uri.getHost()) && uri.getRawUserInfo() == null
                    && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && isAsciiHost(uri.getHost())
                    && (uri.getRawAuthority() == null || !uri.getRawAuthority().contains("%25"))
                    && (uri.getPort() < 0 || uri.getPort() <= 65535)
                    && (uri.getRawPath() == null || uri.getRawPath().isEmpty());
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    public static boolean isValidAbsoluteHttpUrl(String value) {
        if (!StringUtils.hasText(value) || value.codePointCount(0, value.length()) > MAX_URL_CODE_POINTS
                || value.indexOf('\\') >= 0 || hasUnsafeCharacters(value)) {
            return false;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            return uri.isAbsolute() && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && StringUtils.hasText(uri.getHost()) && uri.getRawUserInfo() == null
                    && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && (uri.getPort() < 0 || uri.getPort() <= 65535)
                    && isValidUrlPath(uri.getRawPath());
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private static boolean isValidPath(String value, int minimumLength) {
        if (!StringUtils.hasText(value) || value.codePointCount(0, value.length())
                > (minimumLength == 2 ? MAX_ROUTE_PREFIX_CODE_POINTS : MAX_URL_CODE_POINTS)
                || value.codePointCount(0, value.length()) < minimumLength
                || value.charAt(0) != '/' || value.startsWith("//") || value.contains("//")
                || value.indexOf('\\') >= 0
                || value.indexOf('?') >= 0 || value.indexOf('#') >= 0
                || value.matches(".*%(2[fF]|5[cC]).*")) {
            return false;
        }
        if (hasUnsafeCharacters(value) || !hasValidPercentEncoding(value)) {
            return false;
        }
        return isValidUrlPath(value);
    }

    private static boolean isValidUrlPath(String path) {
        if (path == null || path.isEmpty()) {
            return true;
        }
        if (path.codePointCount(0, path.length()) > MAX_URL_CODE_POINTS
                || hasUnsafeCharacters(path) || !hasValidPercentEncoding(path)) {
            return false;
        }
        if (path.indexOf('\\') >= 0 || path.matches(".*%(2[fF]|5[cC]).*")) {
            return false;
        }
        for (String segment : path.split("/", -1)) {
            if (isDotSegment(segment)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasUnsafeCharacters(String value) {
        for (int index = 0; index < value.length(); ) {
            int codePoint = value.codePointAt(index);
            if (Character.isISOControl(codePoint) || Character.isWhitespace(codePoint)
                    || Character.isSpaceChar(codePoint)) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean isAsciiHost(String host) {
        for (int index = 0; index < host.length(); index++) {
            char value = host.charAt(index);
            if (value > 0x7f || Character.isWhitespace(value) || Character.isISOControl(value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasValidPercentEncoding(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) != '%') {
                continue;
            }
            if (index + 2 >= value.length() || !isHex(value.charAt(index + 1))
                    || !isHex(value.charAt(index + 2))) {
                return false;
            }
            int decoded = hexValue(value.charAt(index + 1)) * 16 + hexValue(value.charAt(index + 2));
            if (decoded <= 0x20 || decoded == 0x7f) {
                return false;
            }
            index += 2;
        }
        return true;
    }

    private static boolean isDotSegment(String segment) {
        if (segment.isEmpty()) {
            return false;
        }
        int dots = 0;
        for (int index = 0; index < segment.length(); ) {
            if (segment.charAt(index) == '.') {
                dots++;
                index++;
            } else if (index + 2 < segment.length() && segment.charAt(index) == '%'
                    && segment.charAt(index + 1) == '2'
                    && (segment.charAt(index + 2) == 'e' || segment.charAt(index + 2) == 'E')) {
                dots++;
                index += 3;
            } else {
                return false;
            }
        }
        return dots == 1 || dots == 2;
    }

    private static boolean isHex(char value) {
        return (value >= '0' && value <= '9') || (value >= 'a' && value <= 'f')
                || (value >= 'A' && value <= 'F');
    }

    private static int hexValue(char value) {
        if (value >= '0' && value <= '9') {
            return value - '0';
        }
        if (value >= 'a' && value <= 'f') {
            return value - 'a' + 10;
        }
        return value - 'A' + 10;
    }
}
