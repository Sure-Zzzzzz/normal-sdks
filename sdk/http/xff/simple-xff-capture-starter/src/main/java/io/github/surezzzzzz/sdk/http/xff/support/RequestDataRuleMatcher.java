package io.github.surezzzzzz.sdk.http.xff.support;

import io.github.surezzzzzz.sdk.http.xff.configuration.SimpleXffCaptureProperties;
import io.github.surezzzzzz.sdk.http.xff.constant.SimpleXffCaptureWebConstant;
import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.http.xff.core.exception.XffCaptureValidationException;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * 请求数据采集方法与 URI 规则匹配器。
 *
 * @author surezzzzzz
 */
public final class RequestDataRuleMatcher {

    private static final String ALL_METHOD = "ALL";
    private static final Set<String> SUPPORTED_METHODS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(java.util.Arrays.asList(
                    "GET", "POST", "PUT", "PATCH", "DELETE", ALL_METHOD)));
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final List<CompiledRule> whitelist;
    private final List<CompiledRule> blacklist;

    /**
     * 创建规则匹配器。
     *
     * @param whitelist 白名单规则
     * @param blacklist 黑名单规则
     */
    public RequestDataRuleMatcher(Collection<SimpleXffCaptureProperties.RequestDataRule> whitelist,
                                  Collection<SimpleXffCaptureProperties.RequestDataRule> blacklist) {
        this.whitelist = compile(whitelist, "白名单");
        this.blacklist = compile(blacklist, "黑名单");
    }

    private static List<CompiledRule> compile(
            Collection<SimpleXffCaptureProperties.RequestDataRule> configuredRules, String ruleType) {
        if (configuredRules == null || configuredRules.isEmpty()) {
            return Collections.emptyList();
        }
        List<CompiledRule> compiledRules = new ArrayList<CompiledRule>();
        Set<String> uniqueRuleKeys = new LinkedHashSet<String>();
        for (SimpleXffCaptureProperties.RequestDataRule configuredRule : configuredRules) {
            if (configuredRule == null || !StringUtils.hasText(configuredRule.getPathPattern())) {
                throw validation("请求数据采集" + ruleType + "规则 pathPattern 不能为空");
            }
            String pathPattern = normalizePath(configuredRule.getPathPattern());
            String method = canonicalMethod(configuredRule.getMethod(), ruleType);
            String uniqueRuleKey = method + " " + pathPattern;
            if (!uniqueRuleKeys.add(uniqueRuleKey)) {
                throw validation("请求数据采集" + ruleType + "规则不能重复：" + uniqueRuleKey);
            }
            compiledRules.add(new CompiledRule(pathPattern, method));
        }
        return Collections.unmodifiableList(compiledRules);
    }

    private static XffCaptureValidationException validation(String detail) {
        return new XffCaptureValidationException(ErrorCode.CAPTURE_SNAPSHOT_STATE_INVALID,
                String.format(ErrorMessage.CAPTURE_SNAPSHOT_STATE_INVALID, detail));
    }

    private static String canonicalMethod(String method) {
        return canonicalMethod(method, "");
    }

    private static String canonicalMethod(String method, String ruleType) {
        if (!StringUtils.hasText(method)) {
            throw validation("请求数据采集" + ruleType + "规则 method 不能为空");
        }
        String canonicalMethod = method.trim().toUpperCase(Locale.ROOT);
        if (SUPPORTED_METHODS.contains(canonicalMethod)) {
            return canonicalMethod;
        }
        throw validation("请求数据采集" + ruleType
                + "规则 method 非法，仅支持 GET、POST、PUT、PATCH、DELETE、ALL：" + method);
    }

    private static String normalizePath(String path) {
        String normalizedPath = path.trim();
        return normalizedPath.startsWith(SimpleXffCaptureWebConstant.URL_PATH_SEPARATOR)
                ? normalizedPath : SimpleXffCaptureWebConstant.URL_PATH_SEPARATOR + normalizedPath;
    }

    private static String applicationPath(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && requestPath.equals(contextPath)) {
            return SimpleXffCaptureWebConstant.URL_PATH_SEPARATOR;
        }
        if (StringUtils.hasText(contextPath)
                && requestPath.startsWith(contextPath + SimpleXffCaptureWebConstant.URL_PATH_SEPARATOR)) {
            return requestPath.substring(contextPath.length());
        }
        return requestPath;
    }

    /**
     * 判断请求是否命中任一规则。
     *
     * @param request 当前请求
     * @return 是否命中
     */
    public boolean matches(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String requestMethod = canonicalMethod(request.getMethod());
        String applicationPath = applicationPath(request);
        if (matchesAny(blacklist, requestMethod, applicationPath)) {
            return false;
        }
        return matchesAny(whitelist, requestMethod, applicationPath);
    }

    private boolean matchesAny(List<CompiledRule> rules, String requestMethod, String applicationPath) {
        for (CompiledRule rule : rules) {
            if ((ALL_METHOD.equals(rule.method) || rule.method.equals(requestMethod))
                    && PATH_MATCHER.match(rule.pathPattern, applicationPath)) {
                return true;
            }
        }
        return false;
    }

    private static final class CompiledRule {

        private final String pathPattern;
        private final String method;

        private CompiledRule(String pathPattern, String method) {
            this.pathPattern = pathPattern;
            this.method = method;
        }
    }
}
