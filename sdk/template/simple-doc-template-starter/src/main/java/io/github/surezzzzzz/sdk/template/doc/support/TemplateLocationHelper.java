package io.github.surezzzzzz.sdk.template.doc.support;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.configuration.SimpleDocTemplateProperties;
import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateRenderException;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Template Location Helper
 *
 * <p>统一处理模板 location 解析、后缀提取和后缀校验。
 *
 * @author surezzzzzz
 */
@SimpleDocTemplateComponent
@RequiredArgsConstructor
public class TemplateLocationHelper {

    private static final char UNIX_SEPARATOR = '/';
    private static final char WINDOWS_SEPARATOR = '\\';
    private static final char DOT = '.';
    private static final char COLON = ':';
    private static final char QUESTION = '?';
    private static final char FRAGMENT = '#';

    private final SimpleDocTemplateProperties properties;

    /**
     * 解析模板 location。
     *
     * @param location 模板路径
     * @return 解析后的模板路径
     */
    public String resolveLocation(String location) {
        if (location == null || location.isEmpty()) {
            return SimpleDocTemplateConstant.EMPTY;
        }
        if (hasExplicitScheme(location) || isWindowsAbsolutePath(location)) {
            return location;
        }
        rejectPathTraversal(location);
        String base = properties.getTemplateLocation();
        if (base == null || base.isEmpty()) {
            return location;
        }
        return base.endsWith(String.valueOf(UNIX_SEPARATOR)) ? base + location : base + UNIX_SEPARATOR + location;
    }

    /**
     * 提取模板后缀。
     *
     * @param resolvedLocation 已解析模板路径
     * @return 小写后缀，无后缀返回空字符串
     */
    public String extractSuffix(String resolvedLocation) {
        if (resolvedLocation == null || resolvedLocation.isEmpty()) {
            return SimpleDocTemplateConstant.EMPTY;
        }
        String path = stripQueryAndFragment(resolvedLocation);
        int lastSlash = Math.max(path.lastIndexOf(UNIX_SEPARATOR), path.lastIndexOf(WINDOWS_SEPARATOR));
        int lastDot = path.lastIndexOf(DOT);
        if (lastDot <= lastSlash || lastDot < 0 || lastDot == path.length() - 1) {
            return SimpleDocTemplateConstant.EMPTY;
        }
        return path.substring(lastDot).toLowerCase(Locale.ROOT);
    }

    /**
     * 要求模板后缀匹配。
     *
     * @param templateLocation 模板路径
     * @param expectedSuffix 期望后缀
     */
    public void requireSuffix(String templateLocation, String expectedSuffix) {
        String resolvedLocation = resolveLocation(templateLocation);
        String suffix = extractSuffix(resolvedLocation);
        if (expectedSuffix == null || !expectedSuffix.equalsIgnoreCase(suffix)) {
            throw TemplateRenderException.formatMismatch(expectedSuffix, suffix);
        }
    }

    /**
     * 判断模板后缀是否匹配。
     *
     * @param templateLocation 模板路径
     * @param expectedSuffix 期望后缀
     * @return true 匹配
     */
    public boolean hasSuffix(String templateLocation, String expectedSuffix) {
        String resolvedLocation = resolveLocation(templateLocation);
        String suffix = extractSuffix(resolvedLocation);
        return expectedSuffix != null && expectedSuffix.equalsIgnoreCase(suffix);
    }

    /**
     * 生成期望后缀文本。
     *
     * @param suffixes 后缀数组
     * @return 后缀文本
     */
    public String expectedSuffixText(String... suffixes) {
        if (suffixes == null || suffixes.length == 0) {
            return SimpleDocTemplateConstant.EMPTY;
        }
        StringBuilder sb = new StringBuilder();
        for (String suffix : suffixes) {
            if (suffix == null || suffix.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(suffix);
        }
        return sb.toString();
    }

    /**
     * 判断是否为显式 scheme。
     *
     * @param location 模板路径
     * @return true 表示有显式 scheme
     */
    public boolean hasExplicitScheme(String location) {
        if (location == null) {
            return false;
        }
        int colon = location.indexOf(COLON);
        if (colon <= 1) {
            return false;
        }
        String scheme = location.substring(0, colon).toLowerCase(Locale.ROOT);
        if (!scheme.matches("[a-z][a-z0-9+.-]*")) {
            return false;
        }
        if (SimpleDocTemplateConstant.URL_SCHEME_FILE.equals(scheme)) {
            return true;
        }
        try {
            new URI(location);
            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * 判断是否为 Windows 绝对路径。
     *
     * @param location 模板路径
     * @return true 表示 Windows 绝对路径
     */
    public boolean isWindowsAbsolutePath(String location) {
        return location != null
                && location.length() >= 3
                && Character.isLetter(location.charAt(0))
                && location.charAt(1) == COLON
                && (location.charAt(2) == UNIX_SEPARATOR || location.charAt(2) == WINDOWS_SEPARATOR);
    }

    private String stripQueryAndFragment(String location) {
        int queryIndex = location.indexOf(QUESTION);
        int fragmentIndex = location.indexOf(FRAGMENT);
        int endIndex = location.length();
        if (queryIndex >= 0) {
            endIndex = Math.min(endIndex, queryIndex);
        }
        if (fragmentIndex >= 0) {
            endIndex = Math.min(endIndex, fragmentIndex);
        }
        return location.substring(0, endIndex);
    }

    private void rejectPathTraversal(String location) {
        String normalized = location.replace(WINDOWS_SEPARATOR, UNIX_SEPARATOR);
        if (normalized.startsWith("../") || normalized.contains("/../") || normalized.endsWith("/..") || "..".equals(normalized)) {
            throw TemplateRenderException.markdownSecurityRejected("模板路径不得包含路径逃逸: " + location);
        }
    }
}
