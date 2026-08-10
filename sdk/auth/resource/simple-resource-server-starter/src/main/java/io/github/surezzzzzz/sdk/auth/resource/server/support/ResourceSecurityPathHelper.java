package io.github.surezzzzzz.sdk.auth.resource.server.support;

import io.github.surezzzzzz.sdk.auth.resource.server.constant.SimpleResourceServerStarterConstant;
import io.github.surezzzzzz.sdk.auth.resource.server.exception.ResourceServerConfigurationException;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 资源安全路径处理工具。
 *
 * @author surezzzzzz
 */
public final class ResourceSecurityPathHelper {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private ResourceSecurityPathHelper() {
        throw new UnsupportedOperationException(SimpleResourceServerStarterConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }

    /**
     * 归一化路径配置。
     *
     * @param paths            原始路径配置
     * @param contextPath      Servlet上下文路径
     * @param contextPathAware 是否感知上下文路径
     * @return 归一化路径
     */
    public static List<String> normalizePaths(Collection<String> paths, String contextPath, boolean contextPathAware) {
        LinkedHashSet<String> normalizedPaths = new LinkedHashSet<String>();
        if (paths == null) {
            return new ArrayList<String>();
        }
        String normalizedContextPath = normalizeContextPath(contextPath);
        for (String path : paths) {
            String normalizedPath = normalizePath(path);
            if (contextPathAware) {
                normalizedPath = stripContextPath(normalizedPath, normalizedContextPath);
            } else if (containsContextPath(normalizedPath, normalizedContextPath)) {
                throw new ResourceServerConfigurationException(String.format(
                        SimpleResourceServerStarterConstant.ERROR_SECURITY_PATH_CONTAINS_CONTEXT_PATH, normalizedPath));
            }
            normalizedPaths.add(normalizedPath);
        }
        return new ArrayList<String>(normalizedPaths);
    }

    /**
     * 验证公开与受保护路径不存在歧义交集。
     *
     * @param permitAllPaths 公开路径
     * @param protectedPaths 受保护路径
     */
    public static void validateNoOverlap(Collection<String> permitAllPaths, Collection<String> protectedPaths) {
        for (String permitAllPath : permitAllPaths) {
            for (String protectedPath : protectedPaths) {
                if (overlaps(permitAllPath, protectedPath)) {
                    throw new ResourceServerConfigurationException(String.format(
                            SimpleResourceServerStarterConstant.ERROR_SECURITY_PATH_CONFLICT,
                            permitAllPath, protectedPath));
                }
            }
        }
    }

    /**
     * 获取Servlet应用内部请求路径。
     *
     * @param request HTTP请求
     * @return 不含context-path的请求路径
     */
    public static String applicationPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && (path.equals(contextPath)
                || path.startsWith(contextPath + SimpleResourceServerStarterConstant.URL_PATH_SEPARATOR))) {
            return path.substring(contextPath.length());
        }
        return path;
    }

    /**
     * 判断请求路径是否受保护。
     *
     * @param protectedPaths 受保护路径
     * @param requestPath    已归一化请求路径
     * @return 是否受保护
     */
    public static boolean isProtected(Collection<String> protectedPaths, String requestPath) {
        for (String protectedPath : protectedPaths) {
            if (PATH_MATCHER.match(protectedPath, requestPath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断两个Ant路径模式是否存在歧义交集。
     *
     * @param first  第一个路径模式
     * @param second 第二个路径模式
     * @return 是否存在交集
     */
    public static boolean overlaps(String first, String second) {
        return PATH_MATCHER.match(first, second) || PATH_MATCHER.match(second, first)
                || hasCompatibleStaticPrefix(first, second);
    }

    private static boolean hasCompatibleStaticPrefix(String first, String second) {
        if (wildcardIndex(first) < 0 || wildcardIndex(second) < 0) {
            return false;
        }
        String firstPrefix = staticPrefix(first);
        String secondPrefix = staticPrefix(second);
        return firstPrefix.startsWith(secondPrefix) || secondPrefix.startsWith(firstPrefix);
    }

    private static String staticPrefix(String pattern) {
        int firstWildcard = wildcardIndex(pattern);
        return firstWildcard < 0 ? pattern : pattern.substring(0, firstWildcard);
    }

    private static int wildcardIndex(String pattern) {
        int multipleWildcard = pattern.indexOf(SimpleResourceServerStarterConstant.ANT_MULTIPLE_CHARACTER_WILDCARD);
        int singleWildcard = pattern.indexOf(SimpleResourceServerStarterConstant.ANT_SINGLE_CHARACTER_WILDCARD);
        if (multipleWildcard < 0) {
            return singleWildcard;
        }
        if (singleWildcard < 0) {
            return multipleWildcard;
        }
        return Math.min(multipleWildcard, singleWildcard);
    }

    private static String normalizeContextPath(String contextPath) {
        if (!StringUtils.hasText(contextPath)
                || SimpleResourceServerStarterConstant.URL_PATH_SEPARATOR.equals(contextPath.trim())) {
            return SimpleResourceServerStarterConstant.EMPTY;
        }
        String normalized = contextPath.trim();
        if (!normalized.startsWith(SimpleResourceServerStarterConstant.URL_PATH_SEPARATOR)) {
            normalized = SimpleResourceServerStarterConstant.URL_PATH_SEPARATOR + normalized;
        }
        while (normalized.length() > 1 && normalized.endsWith(SimpleResourceServerStarterConstant.URL_PATH_SEPARATOR)) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            throw new ResourceServerConfigurationException(SimpleResourceServerStarterConstant.ERROR_SECURITY_PATH_EMPTY);
        }
        String normalized = path.trim();
        if (containsFragment(normalized)) {
            throw new ResourceServerConfigurationException(String.format(
                    SimpleResourceServerStarterConstant.ERROR_SECURITY_PATH_CONTAINS_FRAGMENT, normalized));
        }
        if (!normalized.startsWith(SimpleResourceServerStarterConstant.URL_PATH_SEPARATOR)) {
            normalized = SimpleResourceServerStarterConstant.URL_PATH_SEPARATOR + normalized;
        }
        return normalized;
    }

    private static boolean containsFragment(String path) {
        return path.contains(SimpleResourceServerStarterConstant.URL_FRAGMENT_SEPARATOR);
    }

    private static boolean containsContextPath(String path, String contextPath) {
        return !contextPath.isEmpty() && (path.equals(contextPath)
                || path.startsWith(contextPath + SimpleResourceServerStarterConstant.URL_PATH_SEPARATOR));
    }

    private static String stripContextPath(String path, String contextPath) {
        if (contextPath.isEmpty()) {
            return path;
        }
        if (path.equals(contextPath)) {
            return SimpleResourceServerStarterConstant.URL_PATH_SEPARATOR;
        }
        if (path.startsWith(contextPath + SimpleResourceServerStarterConstant.URL_PATH_SEPARATOR)) {
            return path.substring(contextPath.length());
        }
        return path;
    }
}
