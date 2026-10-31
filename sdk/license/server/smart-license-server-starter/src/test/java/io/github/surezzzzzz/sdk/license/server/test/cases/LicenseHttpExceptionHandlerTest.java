package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpExceptionHandler;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/** 框架层拒绝请求时仍遵循 License 的 JSON 错误响应契约。 */
class LicenseHttpExceptionHandlerTest {

    private LicenseHttpExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new LicenseHttpExceptionHandler();
        request = mock(HttpServletRequest.class);
    }

    @Test
    void frameworkErrorsUseStableStatusAndBody() {
        assertError(handler.handleMethodNotSupported(mock(HttpRequestMethodNotSupportedException.class), request), 405);
        assertError(handler.handleMediaTypeNotSupported(mock(HttpMediaTypeNotSupportedException.class), request), 415);
        assertError(handler.handleMediaTypeNotAcceptable(mock(HttpMediaTypeNotAcceptableException.class), request), 406);
        assertError(handler.handleUnreadableMessage(mock(HttpMessageNotReadableException.class), request), 400);
        assertError(handler.handleMissingParameter(mock(MissingServletRequestParameterException.class), request), 400);
        assertError(handler.handleArgumentNotValid(mock(MethodArgumentNotValidException.class), request), 422);
    }

    private static void assertError(ResponseEntity<Map<String, Object>> response, int status) {
        assertThat(response.getStatusCodeValue()).isEqualTo(status);
        assertThat(response.getBody()).containsKeys("message", "timestamp", "requestId");
        assertThat(response.getBody().get("message")).isInstanceOf(String.class);
    }
}
