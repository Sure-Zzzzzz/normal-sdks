package io.github.surezzzzzz.sdk.auth.resource.server.test.cases;

import io.github.surezzzzzz.sdk.auth.resource.server.configuration.ResourceServerProperties;
import io.github.surezzzzzz.sdk.auth.resource.server.exception.ResourceServerConfigurationException;
import io.github.surezzzzzz.sdk.auth.resource.server.support.ConfiguredApiPermissionResolver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置化精确API权限规则解析器测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class ConfiguredApiPermissionResolverTest {

    /**
     * 验证同一路径的不同HTTP方法只能解析各自的精确权限。
     */
    @Test
    void shouldResolveExactPermissionByPathAndMethod() {
        ConfiguredApiPermissionResolver resolver = resolver(Arrays.asList(
                rule("/api/orders/**", "GET", "order:read"),
                rule("/api/orders/**", "POST", "order:create")));

        assertEquals("order:read", resolver.resolve(request("GET", "/api/orders/1")),
                "GET必须解析只读权限");
        assertEquals("order:create", resolver.resolve(request("POST", "/api/orders/1")),
                "POST必须解析创建权限");
        assertNull(resolver.resolve(request("DELETE", "/api/orders/1")), "未配置的方法不得推断权限");
        assertNull(resolver.resolve(request("GET", "/api/customers/1")), "未配置路径不得推断权限");
        assertNull(resolver.resolve(request("PROPFIND", "/api/orders/1")), "未知请求方法不得触发配置异常");
        MockHttpServletRequest contextPathRequest = request("GET", "/gateway/api/orders/1");
        contextPathRequest.setContextPath("/gateway");
        assertEquals("order:read", resolver.resolve(contextPathRequest), "请求必须按应用内部路径匹配规则");
        log.info("配置规则按精确HTTP方法和应用内部路径解析权限");
    }

    /**
     * 验证同一方法下的路径交叠、公开路径交叠和无受保护路径均在启动时拒绝。
     */
    @Test
    void shouldRejectAmbiguousOrUnsafeRuleConfiguration() {
        assertThrows(ResourceServerConfigurationException.class, () -> resolver(Arrays.asList(
                        rule("/api/**", "GET", "api:read"), rule("/api/orders/**", "GET", "order:read"))),
                "同一方法的重叠规则必须拒绝");

        ResourceServerProperties.Security permitAllSecurity = security(Collections.singletonList("/api/**"));
        permitAllSecurity.setPermitAllPaths(Collections.singletonList("/api/public/**"));
        permitAllSecurity.setApiPermissionRules(Collections.singletonList(
                rule("/api/public/**", "GET", "public:read")));
        assertThrows(ResourceServerConfigurationException.class,
                () -> new ConfiguredApiPermissionResolver(permitAllSecurity, null), "规则不得与公开路径交叠");

        ResourceServerProperties.Security missingProtectedSecurity = security(Collections.<String>emptyList());
        missingProtectedSecurity.setApiPermissionRules(Collections.singletonList(rule("/api/**", "GET", "api:read")));
        assertThrows(ResourceServerConfigurationException.class,
                () -> new ConfiguredApiPermissionResolver(missingProtectedSecurity, null), "规则不能脱离受保护路径生效");
    }

    /**
     * 验证非法字段和context-path规则在启动时拒绝。
     */
    @Test
    void shouldRejectInvalidRuleFieldAndContextPathConfiguration() {
        assertThrows(ResourceServerConfigurationException.class,
                () -> resolver(Collections.singletonList(rule("/api/**", "ANY", "api:read"))),
                "必须声明精确HTTP方法");
        assertThrows(ResourceServerConfigurationException.class,
                () -> resolver(Collections.singletonList(rule(null, "GET", "api:read"))),
                "路径不能为空");
        assertThrows(ResourceServerConfigurationException.class,
                () -> resolver(Collections.singletonList(rule("/api/**", null, "api:read"))),
                "方法不能为空");
        assertThrows(ResourceServerConfigurationException.class,
                () -> resolver(Collections.singletonList(rule("/api/**", "GET", null))),
                "权限不能为空");
        assertThrows(ResourceServerConfigurationException.class,
                () -> resolver(Collections.singletonList(rule("/api/**", "GET", " api:read"))),
                "权限不得包含首尾空白");
        assertThrows(ResourceServerConfigurationException.class,
                () -> resolver(Collections.singletonList(rule("/api/**", "GET", "${api.permission}"))),
                "权限不得使用动态表达式");
        assertThrows(ResourceServerConfigurationException.class,
                () -> resolver(Collections.singletonList(null)), "空规则必须拒绝");

        ResourceServerProperties.Security security = security(Collections.singletonList("/api/**"));
        security.setContextPathAware(false);
        security.setApiPermissionRules(Collections.singletonList(rule("/gateway/api/**", "GET", "api:read")));
        assertThrows(ResourceServerConfigurationException.class,
                () -> new ConfiguredApiPermissionResolver(security, "/gateway"),
                "未启用归一化时规则不得包含context-path");
    }

    private ConfiguredApiPermissionResolver resolver(
            java.util.List<ResourceServerProperties.ApiPermissionRule> rules) {
        ResourceServerProperties.Security security = security(Collections.singletonList("/api/**"));
        security.setApiPermissionRules(rules);
        return new ConfiguredApiPermissionResolver(security, null);
    }

    private ResourceServerProperties.Security security(java.util.List<String> protectedPaths) {
        ResourceServerProperties.Security security = new ResourceServerProperties.Security();
        security.setProtectedPaths(protectedPaths);
        return security;
    }

    private ResourceServerProperties.ApiPermissionRule rule(String pathPattern, String method, String apiPermission) {
        ResourceServerProperties.ApiPermissionRule rule = new ResourceServerProperties.ApiPermissionRule();
        rule.setPathPattern(pathPattern);
        rule.setMethod(method);
        rule.setApiPermission(apiPermission);
        return rule;
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }
}
