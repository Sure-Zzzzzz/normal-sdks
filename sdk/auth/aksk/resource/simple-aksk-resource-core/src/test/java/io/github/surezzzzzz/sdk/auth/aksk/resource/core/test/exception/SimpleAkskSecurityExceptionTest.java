package io.github.surezzzzzz.sdk.auth.aksk.resource.core.test.exception;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.exception.SimpleAkskSecurityException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SimpleAkskSecurityException 单元测试
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
class SimpleAkskSecurityExceptionTest {

    @Test
    void testConstructorWithMessage() {
        String message = "Security check failed";
        SimpleAkskSecurityException exception = new SimpleAkskSecurityException(message);

        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testConstructorWithMessageAndCause() {
        String message = "Security check failed";
        Throwable cause = new RuntimeException("Root cause");
        SimpleAkskSecurityException exception = new SimpleAkskSecurityException(message, cause);

        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals("Root cause", exception.getCause().getMessage());
    }

    @Test
    void testIsRuntimeException() {
        SimpleAkskSecurityException exception = new SimpleAkskSecurityException("Test");
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    void testCanBeThrown() {
        assertThrows(SimpleAkskSecurityException.class, () -> {
            throw new SimpleAkskSecurityException("Test exception");
        });
    }

    @Test
    void testCanBeCaught() {
        try {
            throw new SimpleAkskSecurityException("Test exception");
        } catch (SimpleAkskSecurityException e) {
            assertEquals("Test exception", e.getMessage());
        }
    }
}
