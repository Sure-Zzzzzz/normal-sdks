package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.exception.SimpleAkskResourceServerConfigurationException;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.support.SecurityPathHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SecurityPathHelper Test
 *
 * @author surezzzzzz
 */
@Slf4j
class SecurityPathHelperTest {

    @Test
    @DisplayName("测试空 context-path 不剥离，只做基础清洗")
    void testNullContextPathShouldOnlyCleanPath() {
        assertNormalize(Collections.singletonList("api/**"), null, true,
                Collections.singletonList("/api/**"));
    }

    @Test
    @DisplayName("测试根 context-path 不剥离，只做基础清洗")
    void testRootContextPathShouldOnlyCleanPath() {
        assertNormalize(Collections.singletonList("/api/**"), "/", true,
                Collections.singletonList("/api/**"));
    }

    @Test
    @DisplayName("测试 /api/** 在 /api context-path 下归一化为 /**")
    void testApiPatternShouldStripToUniversal() {
        assertNormalize(Collections.singletonList("/api/**"), "/api", true,
                Collections.singletonList("/**"));
    }

    @Test
    @DisplayName("测试 /api/public/** 在 /api context-path 下归一化为 /public/**")
    void testApiPublicPatternShouldStripContextPath() {
        assertNormalize(Collections.singletonList("/api/public/**"), "/api", true,
                Collections.singletonList("/public/**"));
    }

    @Test
    @DisplayName("测试精确 context-path 归一化为根路径")
    void testExactContextPathShouldStripToRoot() {
        assertNormalize(Collections.singletonList("/api"), "/api", true,
                Collections.singletonList("/"));
        assertNormalize(Collections.singletonList("/api/"), "/api", true,
                Collections.singletonList("/"));
    }

    @Test
    @DisplayName("测试相似前缀不误剥离")
    void testSimilarPrefixShouldNotStrip() {
        assertNormalize(Arrays.asList("/apix/**", "/apiary/**", "/api-v1/**"), "/api", true,
                Arrays.asList("/apix/**", "/apiary/**", "/api-v1/**"));
    }

    @Test
    @DisplayName("测试多级 context-path 归一化")
    void testNestedContextPathShouldStrip() {
        assertNormalize(Arrays.asList("/gateway/api/**", "/gateway/api/user/**", "/gateway/apix/**"),
                "/gateway/api", true,
                Arrays.asList("/**", "/user/**", "/gateway/apix/**"));
    }

    @Test
    @DisplayName("测试空白路径过滤、补前导斜杠、保留特殊 matcher")
    void testCleanPath() {
        assertNormalize(Arrays.asList(null, "", "   ", "api/**", "/", "/**"), "/api", false,
                Arrays.asList("/api/**", "/", "/**"));
    }

    @Test
    @DisplayName("测试去重且保序")
    void testDeduplicateWithOrder() {
        assertNormalize(Arrays.asList("/api/**", "/**", "/api/public/**", "/public/**"), "/api", true,
                Arrays.asList("/**", "/public/**"));
    }

    @Test
    @DisplayName("测试 contextPathAware=false 时不剥离 context-path")
    void testContextPathAwareFalseShouldNotStrip() {
        assertNormalize(Arrays.asList("/api/**", "api/public/**", "", "/api/**"), "/api", false,
                Arrays.asList("/api/**", "/api/public/**"));
    }

    @Test
    @DisplayName("测试 security path 包含 query string 时启动失败")
    void testPathContainsQueryShouldFailFast() {
        List<String> paths = Collections.singletonList("/api/user/**?debug=true");
        log.info("输入 paths={}, contextPath={}, contextPathAware={}", paths, "/api", true);
        assertThrows(SimpleAkskResourceServerConfigurationException.class,
                () -> SecurityPathHelper.normalizePaths(paths, "/api", true),
                "security path 包含 query string 时应 fail fast");
    }

    private void assertNormalize(List<String> paths, String contextPath, boolean contextPathAware,
                                 List<String> expected) {
        List<String> actual = SecurityPathHelper.normalizePaths(paths, contextPath, contextPathAware);
        log.info("输入 paths={}, contextPath={}, contextPathAware={}", paths, contextPath, contextPathAware);
        log.info("归一化结果: {}", actual);
        assertEquals(expected, actual, "归一化结果不符合预期");
    }
}
