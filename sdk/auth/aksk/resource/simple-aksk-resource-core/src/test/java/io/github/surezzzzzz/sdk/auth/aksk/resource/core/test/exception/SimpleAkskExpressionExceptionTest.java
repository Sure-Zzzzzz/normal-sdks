package io.github.surezzzzzz.sdk.auth.aksk.resource.core.test.exception;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.exception.SimpleAkskExpressionException;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.exception.SimpleAkskSecurityException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SimpleAkskExpressionException 单元测试
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
class SimpleAkskExpressionExceptionTest {

    @Test
    void testConstructorWithMessageAndExpression() {
        String message = "Expression evaluation failed";
        String expression = "#context['invalid']";
        SimpleAkskExpressionException exception = new SimpleAkskExpressionException(message, expression);

        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertEquals(expression, exception.getExpression());
        assertNull(exception.getCause());
    }

    @Test
    void testConstructorWithMessageExpressionAndCause() {
        String message = "Expression evaluation failed";
        String expression = "#context['invalid']";
        Throwable cause = new RuntimeException("Root cause");
        SimpleAkskExpressionException exception = new SimpleAkskExpressionException(message, expression, cause);

        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertEquals(expression, exception.getExpression());
        assertEquals(cause, exception.getCause());
        assertEquals("Root cause", exception.getCause().getMessage());
    }

    @Test
    void testExtendsSimpleAkskSecurityException() {
        SimpleAkskExpressionException exception = new SimpleAkskExpressionException("Test", "#test");
        assertTrue(exception instanceof SimpleAkskSecurityException);
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    void testGetExpression() {
        String expression = "#context['userId'] != null && #context['userId'].length() > 0";
        SimpleAkskExpressionException exception = new SimpleAkskExpressionException("Test", expression);

        assertEquals(expression, exception.getExpression());
    }

    @Test
    void testCanBeThrown() {
        assertThrows(SimpleAkskExpressionException.class, () -> {
            throw new SimpleAkskExpressionException("Test exception", "#test");
        });
    }

    @Test
    void testCanBeCaughtAsSimpleAkskSecurityException() {
        try {
            throw new SimpleAkskExpressionException("Test exception", "#test");
        } catch (SimpleAkskSecurityException e) {
            assertEquals("Test exception", e.getMessage());
            assertTrue(e instanceof SimpleAkskExpressionException);
            assertEquals("#test", ((SimpleAkskExpressionException) e).getExpression());
        }
    }

    @Test
    void testCanBeCaughtAsRuntimeException() {
        try {
            throw new SimpleAkskExpressionException("Test exception", "#test");
        } catch (RuntimeException e) {
            assertEquals("Test exception", e.getMessage());
            assertTrue(e instanceof SimpleAkskExpressionException);
        }
    }
}
