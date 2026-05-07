package io.github.surezzzzzz.sdk.auth.aksk.resttemplate.httpsession.client.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.httpsession.client.interceptor.HttpSessionRestTemplateInterceptor;
import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.httpsession.client.test.RestTemplateTestApplication;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AkskHttpSessionRestTemplateInterceptor Test
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = RestTemplateTestApplication.class)
class RestTemplateInterceptorTest {

    private TokenManager tokenManager;
    private HttpSessionRestTemplateInterceptor interceptor;
    private HttpRequest request;
    private ClientHttpRequestExecution execution;
    private ClientHttpResponse response;

    @BeforeEach
    void setUp() {
        tokenManager = mock(TokenManager.class);
        interceptor = new HttpSessionRestTemplateInterceptor(tokenManager);
        request = mock(HttpRequest.class);
        execution = mock(ClientHttpRequestExecution.class);
        response = mock(ClientHttpResponse.class);

        HttpHeaders headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);
        when(request.getURI()).thenReturn(URI.create("http://localhost:8080/api/test"));
    }

    @Test
    void testInterceptShouldAddAuthorizationHeader() throws IOException {
        String token = "test-token-123";
        when(tokenManager.getToken()).thenReturn(token);
        when(execution.execute(any(), any())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);

        byte[] body = new byte[0];
        ClientHttpResponse result = interceptor.intercept(request, body, execution);

        String authHeader = request.getHeaders().getFirst("Authorization");
        assertEquals("Bearer test-token-123", authHeader);
        assertEquals(response, result);
        verify(tokenManager, times(1)).getToken();
        verify(execution, times(1)).execute(request, body);
    }

    @Test
    void testInterceptWhenTokenIsNullShouldProceedWithoutAuthorizationHeader() throws IOException {
        when(tokenManager.getToken()).thenReturn(null);
        when(execution.execute(any(), any())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);

        byte[] body = new byte[0];
        ClientHttpResponse result = interceptor.intercept(request, body, execution);

        String authHeader = request.getHeaders().getFirst("Authorization");
        assertNull(authHeader);
        assertEquals(response, result);
        verify(tokenManager, times(1)).getToken();
        verify(execution, times(1)).execute(request, body);
    }

    @Test
    void testInterceptWhenTokenIsEmptyShouldProceedWithoutAuthorizationHeader() throws IOException {
        when(tokenManager.getToken()).thenReturn("");
        when(execution.execute(any(), any())).thenReturn(response);
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);

        byte[] body = new byte[0];
        ClientHttpResponse result = interceptor.intercept(request, body, execution);

        String authHeader = request.getHeaders().getFirst("Authorization");
        assertNull(authHeader);
        assertEquals(response, result);
        verify(tokenManager, times(1)).getToken();
        verify(execution, times(1)).execute(request, body);
    }

    @Test
    void testInterceptWhenTokenManagerThrowsExceptionShouldPropagateException() throws IOException {
        when(tokenManager.getToken()).thenThrow(new RuntimeException("Token fetch failed"));

        byte[] body = new byte[0];
        assertThrows(RuntimeException.class, () -> interceptor.intercept(request, body, execution),
                "TokenManager 抛异常时应该向上传播，不能被吞掉");
        verify(execution, never()).execute(any(), any());
    }

    @Test
    void testInterceptWhenExecutionThrowsIOExceptionShouldPropagateException() throws IOException {
        String token = "test-token-123";
        when(tokenManager.getToken()).thenReturn(token);
        when(execution.execute(any(), any())).thenThrow(new IOException("Network error"));

        byte[] body = new byte[0];
        assertThrows(IOException.class, () -> interceptor.intercept(request, body, execution),
                "execution 抛 IOException 时应该向上传播");
        String authHeader = request.getHeaders().getFirst("Authorization");
        assertEquals("Bearer test-token-123", authHeader, "Authorization 头应已设置（异常发生在 execute 时）");
    }

    @Test
    void testInterceptShouldOverwriteExistingAuthorizationHeader() throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer old-token");
        when(request.getHeaders()).thenReturn(headers);
        when(tokenManager.getToken()).thenReturn("new-token-456");
        when(execution.execute(any(), any())).thenReturn(response);

        byte[] body = new byte[0];
        interceptor.intercept(request, body, execution);

        String authHeader = request.getHeaders().getFirst("Authorization");
        assertEquals("Bearer new-token-456", authHeader, "应覆盖旧的 Authorization 头");
    }
}
