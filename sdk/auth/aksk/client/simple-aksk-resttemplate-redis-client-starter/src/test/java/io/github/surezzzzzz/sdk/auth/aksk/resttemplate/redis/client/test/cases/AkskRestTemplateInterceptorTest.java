package io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.interceptor.AkskRestTemplateInterceptor;
import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.test.SimpleAkskRestTemplateRedisClientTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * AkskRestTemplateInterceptor 单元测试
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskRestTemplateRedisClientTestApplication.class)
class AkskRestTemplateInterceptorTest {

    private TokenManager tokenManager;
    private AkskRestTemplateInterceptor interceptor;
    private HttpRequest request;
    private ClientHttpRequestExecution execution;
    private ClientHttpResponse response;

    @BeforeEach
    void setUp() {
        tokenManager = mock(TokenManager.class);
        interceptor = new AkskRestTemplateInterceptor(tokenManager);
        request = mock(HttpRequest.class);
        execution = mock(ClientHttpRequestExecution.class);
        response = mock(ClientHttpResponse.class);

        // Mock request headers
        HttpHeaders headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);
        when(request.getURI()).thenReturn(URI.create("http://localhost:8080/api/test"));
    }

    @Test
    void testInterceptShouldAddAuthorizationHeader() throws IOException {
        log.info("========== 测试：拦截器应该添加 Authorization 头 ==========");

        // Given
        String token = "test-token-123";
        when(tokenManager.getToken()).thenReturn(token);
        when(execution.execute(any(), any())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);
        log.info("Given: token = {}", token);

        // When
        byte[] body = new byte[0];
        ClientHttpResponse result = interceptor.intercept(request, body, execution);
        log.info("When: 调用拦截器 intercept 方法");

        // Then
        String authHeader = request.getHeaders().getFirst("Authorization");
        log.info("Then: Authorization header = {}", authHeader);
        assertEquals("Bearer test-token-123", authHeader);
        assertEquals(response, result);
        verify(tokenManager, times(1)).getToken();
        verify(execution, times(1)).execute(request, body);
        log.info("测试通过：Authorization 头已正确添加\n");
    }

    @Test
    void testInterceptWhenTokenIsNullShouldProceedWithoutAuthorizationHeader() throws IOException {
        log.info("========== 测试：当 token 为 null 时应该不添加 Authorization 头 ==========");

        // Given
        when(tokenManager.getToken()).thenReturn(null);
        when(execution.execute(any(), any())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);
        log.info("Given: token = null");

        // When
        byte[] body = new byte[0];
        ClientHttpResponse result = interceptor.intercept(request, body, execution);
        log.info("When: 调用拦截器 intercept 方法");

        // Then
        String authHeader = request.getHeaders().getFirst("Authorization");
        log.info("Then: Authorization header = {}", authHeader);
        assertNull(authHeader);
        assertEquals(response, result);
        verify(tokenManager, times(1)).getToken();
        verify(execution, times(1)).execute(request, body);
        log.info("测试通过：未添加 Authorization 头\n");
    }

    @Test
    void testInterceptWhenTokenIsEmptyShouldProceedWithoutAuthorizationHeader() throws IOException {
        log.info("========== 测试：当 token 为空字符串时应该不添加 Authorization 头 ==========");

        // Given
        when(tokenManager.getToken()).thenReturn("");
        when(execution.execute(any(), any())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);
        log.info("Given: token = \"\"");

        // When
        byte[] body = new byte[0];
        ClientHttpResponse result = interceptor.intercept(request, body, execution);
        log.info("When: 调用拦截器 intercept 方法");

        // Then
        String authHeader = request.getHeaders().getFirst("Authorization");
        log.info("Then: Authorization header = {}", authHeader);
        assertNull(authHeader);
        assertEquals(response, result);
        verify(tokenManager, times(1)).getToken();
        verify(execution, times(1)).execute(request, body);
        log.info("测试通过：未添加 Authorization 头\n");
    }

    @Test
    void testInterceptWhenTokenManagerThrowsExceptionShouldPropagateException() throws IOException {
        log.info("========== 测试：TokenManager 抛异常时应该向上传播 ==========");

        // Given
        when(tokenManager.getToken()).thenThrow(new RuntimeException("Token fetch failed"));
        log.info("Given: tokenManager.getToken() 抛出 RuntimeException");

        // When / Then
        byte[] body = new byte[0];
        assertThrows(RuntimeException.class, () -> interceptor.intercept(request, body, execution),
                "TokenManager 抛异常时应该向上传播，不能被吞掉");
        verify(execution, never()).execute(any(), any());
        log.info("测试通过：异常正确向上传播，execution 未被调用\n");
    }

    @Test
    void testInterceptWhenExecutionThrowsIOExceptionShouldPropagateException() throws IOException {
        log.info("========== 测试：execution 抛 IOException 时应该向上传播 ==========");

        // Given
        String token = "test-token-123";
        when(tokenManager.getToken()).thenReturn(token);
        when(execution.execute(any(), any())).thenThrow(new IOException("Network error"));
        log.info("Given: execution.execute() 抛出 IOException");

        // When / Then
        byte[] body = new byte[0];
        assertThrows(IOException.class, () -> interceptor.intercept(request, body, execution),
                "execution 抛 IOException 时应该向上传播");
        String authHeader = request.getHeaders().getFirst("Authorization");
        assertEquals("Bearer test-token-123", authHeader, "Authorization 头应已设置（异常发生在 execute 时）");
        log.info("测试通过：IOException 正确向上传播\n");
    }

    @Test
    void testInterceptShouldOverwriteExistingAuthorizationHeader() throws IOException {
        log.info("========== 测试：已有 Authorization 头时应该覆盖 ==========");

        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer old-token");
        when(request.getHeaders()).thenReturn(headers);
        when(tokenManager.getToken()).thenReturn("new-token-456");
        when(execution.execute(any(), any())).thenReturn(response);
        log.info("Given: 请求已有 Authorization: Bearer old-token，新 token = new-token-456");

        // When
        byte[] body = new byte[0];
        interceptor.intercept(request, body, execution);

        // Then
        String authHeader = request.getHeaders().getFirst("Authorization");
        log.info("Then: Authorization header = {}", authHeader);
        assertEquals("Bearer new-token-456", authHeader, "应覆盖旧的 Authorization 头");
        log.info("测试通过：旧 Authorization 头已被覆盖\n");
    }
}
