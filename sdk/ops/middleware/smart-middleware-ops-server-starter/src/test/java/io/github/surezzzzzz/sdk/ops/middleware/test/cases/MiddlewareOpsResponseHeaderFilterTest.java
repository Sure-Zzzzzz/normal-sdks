package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import io.github.surezzzzzz.sdk.ops.middleware.configuration.SmartMiddlewareOpsServerProperties;
import io.github.surezzzzzz.sdk.ops.middleware.constant.SmartMiddlewareOpsServerConstant;
import io.github.surezzzzzz.sdk.ops.middleware.controller.MiddlewareOpsResponseHeaderFilter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HTTP 安全响应头测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class MiddlewareOpsResponseHeaderFilterTest {

    @Test
    void shouldGenerateServiceOwnedRequestIdAndDisableCachingForOpsPath() throws Exception {
        MockHttpServletRequest request = request("/api/v1/middleware-ops/redis/datasources");
        request.addHeader(SmartMiddlewareOpsServerConstant.REQUEST_ID_HEADER, "untrusted-request-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterWithBasePath(SmartMiddlewareOpsServerConstant.DEFAULT_API_BASE_PATH)
                .doFilter(request, response, new MockFilterChain());

        String requestId = response.getHeader(SmartMiddlewareOpsServerConstant.REQUEST_ID_HEADER);
        log.info("Ops 路径响应头：requestId={}，cacheControl={}", requestId, response.getHeader("Cache-Control"));
        assertNotNull(requestId);
        assertNotEquals("untrusted-request-id", requestId);
        assertEquals(requestId, request.getAttribute(SmartMiddlewareOpsServerConstant.REQUEST_ID_HEADER));
        assertEquals(SmartMiddlewareOpsServerConstant.CACHE_CONTROL_NO_STORE, response.getHeader("Cache-Control"));
    }

    @Test
    void shouldNotModifyNonOpsPathOrSimilarPrefix() throws Exception {
        MockHttpServletRequest request = request("/api/v1/middleware-ops-other/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterWithBasePath(SmartMiddlewareOpsServerConstant.DEFAULT_API_BASE_PATH)
                .doFilter(request, response, new MockFilterChain());
        log.info("相似前缀路径响应头：requestId={}，cacheControl={}",
                response.getHeader(SmartMiddlewareOpsServerConstant.REQUEST_ID_HEADER), response.getHeader("Cache-Control"));

        assertNull(request.getAttribute(SmartMiddlewareOpsServerConstant.REQUEST_ID_HEADER));
        assertNull(response.getHeader(SmartMiddlewareOpsServerConstant.REQUEST_ID_HEADER));
        assertNull(response.getHeader("Cache-Control"));
    }

    @Test
    void shouldRejectOverlappingOrMalformedRoutePaths() {
        SmartMiddlewareOpsServerProperties properties = new SmartMiddlewareOpsServerProperties();
        properties.setApiBasePath("/middleware-ops/api");
        properties.setUiBasePath("/middleware-ops");
        assertThrows(IllegalStateException.class, properties::validateRoutePaths);

        properties.setApiBasePath("api");
        properties.setUiBasePath("/middleware-ops");
        assertThrows(IllegalStateException.class, properties::validateRoutePaths);
    }

    @Test
    void shouldRejectInvalidNumericLimitsAtStartupValidation() {
        SmartMiddlewareOpsServerProperties properties = new SmartMiddlewareOpsServerProperties();
        properties.getAudit().setMaxRangeDays(29);
        assertThrows(IllegalStateException.class, properties::validateRoutePaths);

        properties = new SmartMiddlewareOpsServerProperties();
        properties.getQuery().setDefaultSize(101);
        properties.getQuery().setMaxSize(100);
        assertThrows(IllegalStateException.class, properties::validateRoutePaths);

        properties = new SmartMiddlewareOpsServerProperties();
        properties.getConcurrency().setDatasource(0);
        assertThrows(IllegalStateException.class, properties::validateRoutePaths);
    }

    @Test
    void shouldHonorConfiguredBasePath() throws Exception {
        MockHttpServletRequest request = request("/internal/ops/kafka/datasources");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterWithBasePath("/internal/ops").doFilter(request, response, new MockFilterChain());
        log.info("自定义 Ops 路径响应头：requestId={}，cacheControl={}",
                response.getHeader(SmartMiddlewareOpsServerConstant.REQUEST_ID_HEADER), response.getHeader("Cache-Control"));

        assertNotNull(response.getHeader(SmartMiddlewareOpsServerConstant.REQUEST_ID_HEADER));
        assertEquals(SmartMiddlewareOpsServerConstant.CACHE_CONTROL_NO_STORE, response.getHeader("Cache-Control"));
    }

    private MiddlewareOpsResponseHeaderFilter filterWithBasePath(String basePath) {
        SmartMiddlewareOpsServerProperties properties = new SmartMiddlewareOpsServerProperties();
        properties.setApiBasePath(basePath);
        return new MiddlewareOpsResponseHeaderFilter(properties);
    }

    private MockHttpServletRequest request(String requestUri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(requestUri);
        return request;
    }
}
