package io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.test.cases;

import feign.RequestTemplate;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.exception.TokenFetchException;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.interceptor.AkskFeignRequestInterceptor;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.test.SimpleAkskFeignRedisClientTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
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

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        log.info("初始化测试环境...");
        mocks = MockitoAnnotations.openMocks(this);
        interceptor = new AkskFeignRequestInterceptor(tokenManager);
        log.info("测试环境初始化完成");
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testInterceptorShouldAddAuthorizationHeader() {
        log.info("========== 测试：拦截器应该添加 Authorization 请求头 ==========");

        // Given
        String token = "test-token-123";
        when(tokenManager.getToken()).thenReturn(token);
        RequestTemplate template = new RequestTemplate();
        log.info("模拟 TokenManager 返回可用 Token");

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
        log.info("验证通过：Authorization 头已添加");
        log.info("测试通过");
    }

    @Test
    void testInterceptorShouldFailClosedWhenTokenIsNull() {
        log.info("========== 测试：Token 为 null 时应该终止请求 ==========");

        // Given
        when(tokenManager.getToken()).thenReturn(null);
        RequestTemplate template = new RequestTemplate();
        log.info("模拟 TokenManager 未返回 Token");

        // When
        TokenFetchException exception = assertThrows(TokenFetchException.class, () -> interceptor.apply(template),
                "空 Token 必须终止请求");
        log.info("拦截器已失败关闭");

        // Then
        Map<String, Collection<String>> headers = template.headers();
        assertNotNull(exception, "应返回统一 Token 获取异常");
        assertFalse(headers.containsKey("Authorization"));
        log.info("验证通过：未生成 Authorization 头");
        log.info("测试通过");
    }

    @Test
    void testInterceptorShouldFailClosedWhenTokenIsEmpty() {
        log.info("========== 测试：Token 为空字符串时应该终止请求 ==========");

        // Given
        when(tokenManager.getToken()).thenReturn("");
        RequestTemplate template = new RequestTemplate();
        log.info("模拟 TokenManager 返回空白 Token");

        // When
        TokenFetchException exception = assertThrows(TokenFetchException.class, () -> interceptor.apply(template),
                "空白 Token 必须终止请求");
        log.info("拦截器已失败关闭");

        // Then
        Map<String, Collection<String>> headers = template.headers();
        assertNotNull(exception, "应返回统一 Token 获取异常");
        assertFalse(headers.containsKey("Authorization"));
        log.info("验证通过：未生成 Authorization 头");
        log.info("测试通过");
    }

    @Test
    void testInterceptorShouldPropagateExceptionWhenTokenManagerThrows() {
        log.info("========== 测试：TokenManager 抛异常时应该向上传播 ==========");

        // Given
        when(tokenManager.getToken()).thenThrow(new RuntimeException("Token fetch failed"));
        RequestTemplate template = new RequestTemplate();
        log.info("模拟 TokenManager 抛出 RuntimeException");

        // When / Then
        assertThrows(RuntimeException.class, () -> interceptor.apply(template),
                "TokenManager 抛异常时应该向上传播，不能被吞掉");
        assertFalse(template.headers().containsKey("Authorization"), "异常时不应添加 Authorization 头");
        log.info("测试通过：异常正确向上传播");
    }

    @Test
    void testInterceptorShouldOverwriteExistingAuthorizationHeader() {
        log.info("========== 测试：已有 Authorization 头时应该覆盖 ==========");

        // Given
        RequestTemplate template = new RequestTemplate();
        template.header("Authorization", "Bearer old-token");
        when(tokenManager.getToken()).thenReturn("new-token-456");
        log.info("模拟已有认证头，TokenManager 返回新 Token");

        // When
        interceptor.apply(template);

        // Then
        Collection<String> authHeaders = template.headers().get("Authorization");
        assertNotNull(authHeaders);
        assertEquals(1, authHeaders.size(), "应只有一个 Authorization 头");
        assertTrue(authHeaders.contains("Bearer new-token-456"), "应覆盖旧的 Authorization 头");
        log.info("测试通过：旧 Authorization 头已被覆盖");
    }
}
