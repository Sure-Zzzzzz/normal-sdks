package io.github.surezzzzzz.sdk.prometheus.route.transport;

import io.github.surezzzzzz.sdk.prometheus.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.prometheus.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.prometheus.route.constant.SimplePrometheusRouteConstant;
import io.github.surezzzzzz.sdk.prometheus.route.exception.PrometheusRouteException;
import io.github.surezzzzzz.sdk.prometheus.route.model.PrometheusRouteParameter;
import org.apache.http.client.utils.URIBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

/**
 * Route 固定 base path 的安全 URI 创建器。
 *
 * @author surezzzzzz
 */
public final class PrometheusRouteUriFactory {

    private PrometheusRouteUriFactory() {
    }

    /**
     * 规范化并校验 target base URI。
     *
     * @param value 配置 URL
     * @return 已规范化的 base URI
     */
    public static URI normalizeBaseUri(String value) {
        try {
            if (value == null || value.trim().isEmpty()) {
                failConfiguration();
            }
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null || !(SimplePrometheusRouteConstant.HTTP_SCHEME.equalsIgnoreCase(scheme)
                    || SimplePrometheusRouteConstant.HTTPS_SCHEME.equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || hasUnsafePath(uri.getRawPath())) {
                failConfiguration();
            }
            String path = normalizeBasePath(uri.getRawPath());
            return new URI(uri.getScheme().toLowerCase(Locale.ROOT), null, uri.getHost(), uri.getPort(), path, null, null);
        } catch (URISyntaxException exception) {
            failConfiguration();
            throw new IllegalStateException(exception);
        }
    }

    /**
     * 为固定 target 创建请求 URI。
     *
     * @param baseUri    已校验 target base URI
     * @param path       相对 API path
     * @param parameters 有序 query 参数
     * @return 请求 URI
     */
    public static URI create(URI baseUri, String path, List<PrometheusRouteParameter> parameters) {
        validateRelativePath(path);
        String basePath = normalizeBasePath(baseUri.getRawPath());
        String finalPath = SimplePrometheusRouteConstant.ROOT_PATH.equals(basePath) ? path : basePath + path;
        try {
            URIBuilder builder = new URIBuilder(new URI(baseUri.getScheme(), null, baseUri.getHost(), baseUri.getPort(),
                    finalPath, null, null));
            if (parameters != null) {
                for (PrometheusRouteParameter parameter : parameters) {
                    builder.addParameter(parameter.getName(), parameter.getValue());
                }
            }
            return builder.build();
        } catch (URISyntaxException exception) {
            throw requestException();
        }
    }

    private static void validateRelativePath(String path) {
        if (path == null || !path.startsWith(SimplePrometheusRouteConstant.ROOT_PATH)
                || path.startsWith(SimplePrometheusRouteConstant.AUTHORITY_PATH_PREFIX)
                || path.indexOf(SimplePrometheusRouteConstant.QUERY_SEPARATOR) >= 0
                || path.indexOf(SimplePrometheusRouteConstant.FRAGMENT_SEPARATOR) >= 0 || hasUnsafePath(path)) {
            throw requestException();
        }
        try {
            URI uri = new URI(path);
            if (uri.getScheme() != null || uri.getAuthority() != null || uri.getHost() != null || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw requestException();
            }
        } catch (URISyntaxException exception) {
            throw requestException();
        }
    }

    private static String normalizeBasePath(String rawPath) {
        if (rawPath == null || rawPath.isEmpty() || SimplePrometheusRouteConstant.ROOT_PATH.equals(rawPath)) {
            return SimplePrometheusRouteConstant.ROOT_PATH;
        }
        String result = rawPath;
        while (result.length() > SimplePrometheusRouteConstant.MIN_PATH_LENGTH
                && result.endsWith(SimplePrometheusRouteConstant.ROOT_PATH)) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static boolean hasUnsafePath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        String lowerPath = path.toLowerCase(Locale.ROOT);
        if (lowerPath.contains(SimplePrometheusRouteConstant.ENCODED_DOT)) {
            return true;
        }
        String[] segments = path.split(SimplePrometheusRouteConstant.ROOT_PATH,
                SimplePrometheusRouteConstant.KEEP_TRAILING_EMPTY_SEGMENTS);
        for (String segment : segments) {
            if (SimplePrometheusRouteConstant.CURRENT_PATH_SEGMENT.equals(segment)
                    || SimplePrometheusRouteConstant.PARENT_PATH_SEGMENT.equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private static PrometheusRouteException requestException() {
        return new PrometheusRouteException(ErrorCode.REQUEST_ILLEGAL, ErrorMessage.REQUEST_ILLEGAL);
    }

    private static void failConfiguration() {
        throw new PrometheusRouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                ErrorMessage.TARGET_CONFIGURATION_ILLEGAL);
    }
}
