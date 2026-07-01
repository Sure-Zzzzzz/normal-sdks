package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.support;

import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.constant.SimpleAkskResourceServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.exception.SimpleAkskResourceServerConfigurationException;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Spring Security path Helper
 *
 * <p>用于将外部访问路径写法归一化为 Spring Security matcher 实际匹配的应用内路径。
 *
 * @author surezzzzzz
 */
public final class SecurityPathHelper {

    private SecurityPathHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 归一化 Spring Security matcher 路径列表。
     *
     * @param paths 原始配置路径
     * @param contextPath server.servlet.context-path
     * @param contextPathAware 是否启用 context-path 前缀剥离
     * @return 归一化后的 matcher 路径列表
     */
    public static List<String> normalizePaths(List<String> paths, String contextPath, boolean contextPathAware) {
        LinkedHashSet<String> normalizedPaths = new LinkedHashSet<>();
        String normalizedContextPath = normalizeContextPath(contextPath);

        if (paths == null || paths.isEmpty()) {
            return new ArrayList<>();
        }

        for (String path : paths) {
            String normalizedPath = normalizeMatcherPath(path);
            if (!StringUtils.hasText(normalizedPath)) {
                continue;
            }
            if (contextPathAware) {
                normalizedPath = stripContextPath(normalizedPath, normalizedContextPath);
            }
            normalizedPaths.add(normalizedPath);
        }

        return new ArrayList<>(normalizedPaths);
    }

    private static String normalizeContextPath(String contextPath) {
        if (!StringUtils.hasText(contextPath)) {
            return "";
        }

        String normalized = contextPath.trim();
        if (!normalized.startsWith(SimpleAkskResourceServerConstant.URL_PATH_SEPARATOR)) {
            normalized = SimpleAkskResourceServerConstant.URL_PATH_SEPARATOR + normalized;
        }

        while (normalized.length() > 1
                && normalized.endsWith(SimpleAkskResourceServerConstant.URL_PATH_SEPARATOR)) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (SimpleAkskResourceServerConstant.URL_PATH_SEPARATOR.equals(normalized)) {
            return "";
        }
        return normalized;
    }

    private static String normalizeMatcherPath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }

        String normalized = path.trim();
        if (normalized.contains(SimpleAkskResourceServerConstant.URL_QUERY_SEPARATOR)) {
            throw new SimpleAkskResourceServerConfigurationException(
                    String.format(SimpleAkskResourceServerConstant.ERROR_SECURITY_PATH_CONTAINS_QUERY_STRING,
                            normalized));
        }
        if (!normalized.startsWith(SimpleAkskResourceServerConstant.URL_PATH_SEPARATOR)) {
            normalized = SimpleAkskResourceServerConstant.URL_PATH_SEPARATOR + normalized;
        }
        return normalized;
    }

    private static String stripContextPath(String path, String contextPath) {
        if (!StringUtils.hasText(contextPath)) {
            return path;
        }
        if (path.equals(contextPath)) {
            return SimpleAkskResourceServerConstant.URL_PATH_SEPARATOR;
        }
        if (path.startsWith(contextPath + SimpleAkskResourceServerConstant.URL_PATH_SEPARATOR)) {
            String stripped = path.substring(contextPath.length());
            if (!StringUtils.hasText(stripped)) {
                return SimpleAkskResourceServerConstant.URL_PATH_SEPARATOR;
            }
            return stripped;
        }
        return path;
    }
}
