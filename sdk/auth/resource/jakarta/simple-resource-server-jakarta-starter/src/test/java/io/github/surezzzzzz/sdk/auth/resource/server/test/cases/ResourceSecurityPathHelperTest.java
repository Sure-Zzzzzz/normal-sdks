package io.github.surezzzzzz.sdk.auth.resource.server.test.cases;

import io.github.surezzzzzz.sdk.auth.resource.server.exception.ResourceServerConfigurationException;
import io.github.surezzzzzz.sdk.auth.resource.server.support.ResourceSecurityPathHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 资源安全路径处理测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class ResourceSecurityPathHelperTest {

    /**
     * 验证上下文路径归一化、去重及请求匹配。
     */
    @Test
    void shouldNormalizeContextPathAndMatchProtectedPath() {
        List<String> paths = ResourceSecurityPathHelper.normalizePaths(
                Arrays.asList("api/**", "/gateway/api/**", "/gateway/api/**"), "/gateway", true);

        log.info("归一化安全路径: {}", paths);
        assertEquals(Collections.singletonList("/api/**"), paths, "context-path必须只剥离一次并去重");
        assertTrue(ResourceSecurityPathHelper.isProtected(paths, "/api/orders"), "归一化受保护路径必须匹配请求");
        assertFalse(ResourceSecurityPathHelper.isProtected(paths, "/public/ping"), "不相关路径不得受保护");
        assertFalse(ResourceSecurityPathHelper.overlaps("/api/order", "/api/orders"),
                "不同静态兄弟路径不得误判为交叠");
    }

    /**
     * 验证请求路径只剥离完整的context-path路径段。
     */
    @Test
    void shouldExtractApplicationPathByContextPathBoundary() {
        MockHttpServletRequest contextPathRequest = new MockHttpServletRequest("GET", "/gateway/api/orders");
        contextPathRequest.setContextPath("/gateway");
        MockHttpServletRequest prefixOnlyRequest = new MockHttpServletRequest("GET", "/gateway-api/orders");
        prefixOnlyRequest.setContextPath("/gateway");

        String applicationPath = ResourceSecurityPathHelper.applicationPath(contextPathRequest);
        String prefixOnlyPath = ResourceSecurityPathHelper.applicationPath(prefixOnlyRequest);
        log.info("完整context-path剥离结果: {}, 前缀路径结果: {}", applicationPath, prefixOnlyPath);
        assertEquals("/api/orders", applicationPath, "完整context-path必须剥离");
        assertEquals("/gateway-api/orders", prefixOnlyPath, "非路径段前缀不得误剥离");
    }

    /**
     * 验证公开与受保护路径的歧义交集和非法路径均在启动前拒绝。
     */
    @Test
    void shouldRejectAmbiguousOrIllegalPathConfiguration() {
        ResourceServerConfigurationException overlap = assertThrows(ResourceServerConfigurationException.class,
                () -> ResourceSecurityPathHelper.validateNoOverlap(
                        Collections.singletonList("/api/**"), Collections.singletonList("/api/orders/**")));
        ResourceServerConfigurationException fragment = assertThrows(ResourceServerConfigurationException.class,
                () -> ResourceSecurityPathHelper.normalizePaths(
                        Collections.singletonList("/api/orders#detail"), null, true));
        ResourceServerConfigurationException empty = assertThrows(ResourceServerConfigurationException.class,
                () -> ResourceSecurityPathHelper.normalizePaths(Collections.singletonList("  "), null, true));
        ResourceServerConfigurationException contextPath = assertThrows(ResourceServerConfigurationException.class,
                () -> ResourceSecurityPathHelper.normalizePaths(Collections.singletonList("/gateway/api/**"),
                        "/gateway", false));

        log.info("路径交集、片段、空路径与未归一化context-path均已拒绝");
        assertTrue(overlap.getMessage().contains("歧义交集"), "交集配置必须给出明确原因");
        assertTrue(fragment.getMessage().contains("片段"), "片段必须拒绝");
        assertTrue(empty.getMessage().contains("不能为空"), "空路径必须拒绝");
        assertTrue(contextPath.getMessage().contains("context-path"), "未归一化路径不得包含context-path");
        assertEquals(Collections.singletonList("/api/**"), ResourceSecurityPathHelper.normalizePaths(
                        Collections.singletonList("/api/**"), "/gateway", false),
                "关闭归一化时必须保留应用内部路径");
    }

    /**
     * 验证Ant单字符通配符不被误判为URL查询参数。
     */
    @Test
    void shouldKeepAntSingleCharacterWildcardAsPathPattern() {
        assertEquals(Collections.singletonList("/api/order?"), ResourceSecurityPathHelper.normalizePaths(
                        Collections.singletonList("/api/order?"), null, true),
                "合法Ant单字符通配符必须保留");
    }
}
