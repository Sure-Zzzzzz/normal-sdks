package io.github.surezzzzzz.sdk.auth.aksk.feign.httpsession.client.test.cases;

import feign.RequestTemplate;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.feign.httpsession.client.interceptor.AkskHttpSessionFeignRequestInterceptor;
import io.github.surezzzzzz.sdk.auth.aksk.feign.httpsession.client.test.SimpleAkskFeignHttpSessionClientTestApplication;
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
 * AKSK HttpSession Feign Request Interceptor Test
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskFeignHttpSessionClientTestApplication.class)
class AkskHttpSessionFeignRequestInterceptorTest {

    @Mock
    private TokenManager tokenManager;

    private AkskHttpSessionFeignRequestInterceptor interceptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        interceptor = new AkskHttpSessionFeignRequestInterceptor(tokenManager);
    }

    @Test
    void testInterceptorShouldAddAuthorizationHeader() {
        String token = "test-token-123";
        when(tokenManager.getToken()).thenReturn(token);
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        Map<String, Collection<String>> headers = template.headers();
        assertTrue(headers.containsKey("Authorization"));
        Collection<String> authHeaders = headers.get("Authorization");
        assertNotNull(authHeaders);
        assertEquals(1, authHeaders.size());
        assertTrue(authHeaders.contains("Bearer " + token));
    }

    @Test
    void testInterceptorShouldNotAddHeaderWhenTokenIsNull() {
        when(tokenManager.getToken()).thenReturn(null);
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        Map<String, Collection<String>> headers = template.headers();
        assertFalse(headers.containsKey("Authorization"));
    }

    @Test
    void testInterceptorShouldNotAddHeaderWhenTokenIsEmpty() {
        when(tokenManager.getToken()).thenReturn("");
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        Map<String, Collection<String>> headers = template.headers();
        assertFalse(headers.containsKey("Authorization"));
    }

    @Test
    void testInterceptorShouldPropagateExceptionWhenTokenManagerThrows() {
        when(tokenManager.getToken()).thenThrow(new RuntimeException("Token fetch failed"));
        RequestTemplate template = new RequestTemplate();

        assertThrows(RuntimeException.class, () -> interceptor.apply(template),
                "TokenManager 抛异常时应该向上传播，不能被吞掉");
        assertFalse(template.headers().containsKey("Authorization"), "异常时不应添加 Authorization 头");
    }

    @Test
    void testInterceptorShouldOverwriteExistingAuthorizationHeader() {
        RequestTemplate template = new RequestTemplate();
        template.header("Authorization", "Bearer old-token");
        when(tokenManager.getToken()).thenReturn("new-token-456");

        interceptor.apply(template);

        Collection<String> authHeaders = template.headers().get("Authorization");
        assertNotNull(authHeaders);
        assertEquals(1, authHeaders.size(), "应只有一个 Authorization 头");
        assertTrue(authHeaders.contains("Bearer new-token-456"), "应覆盖旧的 Authorization 头");
    }
}
