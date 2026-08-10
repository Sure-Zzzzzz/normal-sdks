package io.github.surezzzzzz.sdk.auth.resource.server.support;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.exception.ApplicationAuthorizationException;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.support.ApplicationAuthorizationValidationHelper;
import io.github.surezzzzzz.sdk.auth.resource.server.configuration.ResourceServerProperties;
import io.github.surezzzzzz.sdk.auth.resource.server.constant.SimpleResourceServerStarterConstant;
import io.github.surezzzzzz.sdk.auth.resource.server.exception.ResourceServerConfigurationException;
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * 配置化精确API权限规则解析器。
 *
 * @author surezzzzzz
 */
public final class ConfiguredApiPermissionResolver {

    private final List<String> protectedPaths;
    private final List<Rule> rules;

    /**
     * 创建并校验配置化精确API权限规则。
     *
     * @param security    资源安全配置
     * @param contextPath Servlet上下文路径
     */
    public ConfiguredApiPermissionResolver(ResourceServerProperties.Security security, String contextPath) {
        List<String> normalizedProtectedPaths = ResourceSecurityPathHelper.normalizePaths(security.getProtectedPaths(),
                contextPath, security.isContextPathAware());
        if (normalizedProtectedPaths.isEmpty()) {
            throw new ResourceServerConfigurationException(
                    SimpleResourceServerStarterConstant.ERROR_MISSING_PROTECTED_PATH);
        }
        List<String> permitAllPaths = ResourceSecurityPathHelper.normalizePaths(security.getPermitAllPaths(), contextPath,
                security.isContextPathAware());
        this.protectedPaths = Collections.unmodifiableList(normalizedProtectedPaths);
        this.rules = Collections.unmodifiableList(compileRules(security.getApiPermissionRules(), this.protectedPaths,
                permitAllPaths, contextPath, security.isContextPathAware()));
    }

    /**
     * 获取受保护路径模式副本。
     *
     * @return 受保护路径模式
     */
    public String[] getProtectedPathPatterns() {
        return protectedPaths.toArray(new String[0]);
    }

    /**
     * 判断请求是否位于受保护路径。
     *
     * @param request HTTP请求
     * @return 是否受保护
     */
    public boolean isProtected(HttpServletRequest request) {
        return ResourceSecurityPathHelper.isProtected(protectedPaths, ResourceSecurityPathHelper.applicationPath(request));
    }

    /**
     * 解析请求对应的配置化精确API权限。
     *
     * @param request HTTP请求
     * @return 精确API权限；未配置规则时返回null
     */
    public String resolve(HttpServletRequest request) {
        HttpMethod requestMethod = HttpMethod.resolve(request.getMethod());
        if (requestMethod == null) {
            return null;
        }
        String method = requestMethod.name();
        String path = ResourceSecurityPathHelper.applicationPath(request);
        for (Rule rule : rules) {
            if (rule.method.equals(method) && ResourceSecurityPathHelper.isProtected(
                    Collections.singletonList(rule.pathPattern), path)) {
                return rule.apiPermission;
            }
        }
        return null;
    }

    private List<Rule> compileRules(Collection<ResourceServerProperties.ApiPermissionRule> configuredRules,
                                    List<String> protectedPaths, List<String> permitAllPaths, String contextPath,
                                    boolean contextPathAware) {
        if (configuredRules == null) {
            return Collections.emptyList();
        }
        List<Rule> compiledRules = new ArrayList<Rule>();
        for (ResourceServerProperties.ApiPermissionRule configuredRule : configuredRules) {
            if (configuredRule == null) {
                throw new ResourceServerConfigurationException(
                        SimpleResourceServerStarterConstant.ERROR_API_PERMISSION_RULE_NULL);
            }
            String pathPattern = normalizePath(configuredRule.getPathPattern(), contextPath, contextPathAware);
            String method = canonicalMethod(configuredRule.getMethod());
            String apiPermission = validateApiPermission(configuredRule.getApiPermission());
            validateProtectedPathOverlap(pathPattern, protectedPaths);
            validatePermitAllPathOverlap(pathPattern, permitAllPaths);
            validateRuleConflict(pathPattern, method, compiledRules);
            compiledRules.add(new Rule(pathPattern, method, apiPermission));
        }
        return compiledRules;
    }

    private String normalizePath(String pathPattern, String contextPath, boolean contextPathAware) {
        if (!StringUtils.hasText(pathPattern)) {
            throw new ResourceServerConfigurationException(String.format(
                    SimpleResourceServerStarterConstant.ERROR_API_PERMISSION_RULE_FIELD_EMPTY, "pathPattern"));
        }
        return ResourceSecurityPathHelper.normalizePaths(Collections.singletonList(pathPattern), contextPath,
                contextPathAware).get(0);
    }

    private String canonicalMethod(String method) {
        if (!StringUtils.hasText(method)) {
            throw new ResourceServerConfigurationException(String.format(
                    SimpleResourceServerStarterConstant.ERROR_API_PERMISSION_RULE_FIELD_EMPTY, "method"));
        }
        String canonicalMethod = method.toUpperCase(Locale.ROOT);
        if (HttpMethod.resolve(canonicalMethod) == null) {
            throw new ResourceServerConfigurationException(String.format(
                    SimpleResourceServerStarterConstant.ERROR_API_PERMISSION_RULE_METHOD_INVALID, method));
        }
        return canonicalMethod;
    }

    private String validateApiPermission(String apiPermission) {
        if (!StringUtils.hasText(apiPermission)) {
            throw new ResourceServerConfigurationException(String.format(
                    SimpleResourceServerStarterConstant.ERROR_API_PERMISSION_RULE_FIELD_EMPTY, "apiPermission"));
        }
        try {
            return ApplicationAuthorizationValidationHelper.requireIdentifier(apiPermission, "apiPermission");
        } catch (ApplicationAuthorizationException exception) {
            throw new ResourceServerConfigurationException(String.format(
                    SimpleResourceServerStarterConstant.ERROR_API_PERMISSION_RULE_PERMISSION_INVALID, apiPermission));
        }
    }

    private void validateProtectedPathOverlap(String pathPattern, List<String> protectedPaths) {
        for (String protectedPath : protectedPaths) {
            if (ResourceSecurityPathHelper.overlaps(pathPattern, protectedPath)) {
                return;
            }
        }
        throw new ResourceServerConfigurationException(String.format(
                SimpleResourceServerStarterConstant.ERROR_API_PERMISSION_RULE_OUTSIDE_PROTECTED_PATH, pathPattern));
    }

    private void validatePermitAllPathOverlap(String pathPattern, List<String> permitAllPaths) {
        for (String permitAllPath : permitAllPaths) {
            if (ResourceSecurityPathHelper.overlaps(pathPattern, permitAllPath)) {
                throw new ResourceServerConfigurationException(String.format(
                        SimpleResourceServerStarterConstant.ERROR_API_PERMISSION_RULE_CONFLICT_PERMIT_ALL_PATH,
                        pathPattern, permitAllPath));
            }
        }
    }

    private void validateRuleConflict(String pathPattern, String method, List<Rule> rules) {
        for (Rule rule : rules) {
            if (rule.method.equals(method) && ResourceSecurityPathHelper.overlaps(rule.pathPattern, pathPattern)) {
                throw new ResourceServerConfigurationException(String.format(
                        SimpleResourceServerStarterConstant.ERROR_API_PERMISSION_RULE_PATH_CONFLICT, method,
                        rule.pathPattern, pathPattern));
            }
        }
    }

    private static final class Rule {

        private final String pathPattern;
        private final String method;
        private final String apiPermission;

        private Rule(String pathPattern, String method, String apiPermission) {
            this.pathPattern = pathPattern;
            this.method = method;
            this.apiPermission = apiPermission;
        }
    }
}
