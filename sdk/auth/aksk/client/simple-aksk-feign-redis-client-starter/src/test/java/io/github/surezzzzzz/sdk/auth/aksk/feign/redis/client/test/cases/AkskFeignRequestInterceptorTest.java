package io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.test.cases;

import feign.RequestTemplate;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.interceptor.AkskFeignRequestInterceptor;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.test.SimpleAkskFeignRedisClientTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * AKSK Feign Request Interceptor Test
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskFeignRedisClientTestApplication.class)
class AkskFeignRequestInterceptorTest {

    @Mock
    private TokenManager tokenManager;

    private AkskFeignRequestInterceptor interceptor;

    @BeforeEach
    void setUp() {
        log.info("初始化测试环境...");
        MockitoAnnotations.openMocks(this);
        interceptor = new AkskFeignRequestInterceptor(tokenManager);
        log.info("测试环境初始化完成");
    }

    @Test
    void testInterceptorShouldAddAuthorizationHeader() {
        log.info("========== 测试：拦截器应该添加 Authorization 请求头 ==========");

        // Given
        String token = "test-token-123";
        when(tokenManager.getToken()).thenReturn(token);
        RequestTemplate template = new RequestTemplate();
        log.info("模拟 Token: {}", token);

        // When
        interceptor.apply(template);
        log.info("拦截器已应用");

        // Then
        Map<String, Collection<String>> headers = template.headers();
        assertTrue(headers.containsKey("Authorization"));
        Collection<String> authHeaders = headers.get("Authorization");
        assertNotNull(authHeaders);
        assertEquals(1, authHeaders.size());
        assertTrue(authHeaders.contains("Bearer " + token));
        log.info("验证通过：Authorization 头已添加，值: {}", authHeaders);
        log.info("测试通过");
    }

    @Test
    void testInterceptorShouldNotAddHeaderWhenTokenIsNull() {
        log.info("========== 测试：Token 为 null 时不应该添加请求头 ==========");

        // Given
        when(tokenManager.getToken()).thenReturn(null);
        RequestTemplate template = new RequestTemplate();
        log.info("模拟 Token: null");

        // When
        interceptor.apply(template);
        log.info("拦截器已应用");

        // Then
        Map<String, Collection<String>> headers = template.headers();
        assertFalse(headers.containsKey("Authorization"));
        log.info("验证通过：Authorization 头未添加");
        log.info("测试通过");
    }

    @Test
    void testInterceptorShouldNotAddHeaderWhenTokenIsEmpty() {
        log.info("========== 测试：Token 为空字符串时不应该添加请求头 ==========");

        // Given
        when(tokenManager.getToken()).thenReturn("");
        RequestTemplate template = new RequestTemplate();
        log.info("模拟 Token: \"\"");

        // When
        interceptor.apply(template);
        log.info("拦截器已应用");

        // Then
        Map<String, Collection<String>> headers = template.headers();
        assertFalse(headers.containsKey("Authorization"));
        log.info("验证通过：Authorization 头未添加");
        log.info("测试通过");
    }
}
