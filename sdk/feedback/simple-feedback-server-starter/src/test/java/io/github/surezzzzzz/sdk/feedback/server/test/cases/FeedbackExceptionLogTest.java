package io.github.surezzzzzz.sdk.feedback.server.test.cases;

import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.feedback.server.exception.SimpleFeedbackServerException;
import io.github.surezzzzzz.sdk.feedback.server.support.FeedbackRequestExceptionHandler;
import io.github.surezzzzzz.sdk.feedback.server.support.FeedbackRestExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 反馈异常日志只保留有限的定位栈信息，不记录异常消息。
 *
 * @author surezzzzzz
 */
@Slf4j
class FeedbackExceptionLogTest {

    @Test
    void unexpectedFailureLogsBoundedStackWithoutExceptionMessage() {
        log.debug("验证未预期故障日志的脱敏边界");
        RuntimeException thirdCause = failure("third cause secret=third", "ThirdCause", 33, null);
        RuntimeException secondCause = failure("second cause secret=second", "SecondCause", 22, thirdCause);
        RuntimeException firstCause = failure("password=private-cause", "FirstCause", 11, secondCause);
        RuntimeException failure = failure("Authorization=private-request", "FeedbackController", 7, firstCause);
        failure.setStackTrace(new StackTraceElement[]{
                frame("FeedbackController", 7), frame("SecondFrame", 8), frame("ThirdFrame", 9),
                frame("FourthFrame", 10), frame("FifthFrame", 11)});
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "feedback-log-test");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try (LogCapture capture = new LogCapture(FeedbackRequestExceptionHandler.class)) {
            ResponseEntity<Map<String, Object>> response =
                    new FeedbackRequestExceptionHandler().handleUnexpected(failure);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals(ErrorMessage.INTERNAL_ERROR, response.getBody().get("message"));
            assertEquals("feedback-log-test", response.getHeaders().getFirst("X-Correlation-Id"));
            assertEquals(1, capture.events().size());
            LogEvent event = capture.events().get(0);
            String message = event.getMessage().getFormattedMessage();
            assertEquals(Level.ERROR, event.getLevel());
            assertNull(event.getThrown());
            assertTrue(message.contains("FeedbackController#fail:7"));
            assertTrue(message.contains("FirstCause#fail:11"));
            assertTrue(message.contains("SecondCause#fail:22"));
            assertFalse(message.contains("ThirdCause"));
            assertFalse(message.contains("FifthFrame"));
            assertFalse(message.contains("private-request"));
            assertFalse(message.contains("private-cause"));
            assertFalse(message.contains("secret=second"));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void internalDomainFailureLogsCausePositionWithoutMessage() {
        log.debug("验证领域内部故障日志的脱敏边界");
        RuntimeException cause = failure("token=private-cause", "StorageClient", 42, null);
        SimpleFeedbackServerException failure = new SimpleFeedbackServerException(
                ErrorCode.COLLABORATION_ASSEMBLY_INCOMPLETE, "body=private-domain", cause);
        failure.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("io.github.surezzzzzz.sdk.feedback.server.TestService", "fail", "TestService.java", 21),
                new StackTraceElement("secret=forged-frame", "leak", "Fake.java", 9)});

        try (LogCapture capture = new LogCapture(FeedbackRestExceptionHandler.class)) {
            ResponseEntity<Map<String, Object>> response = new FeedbackRestExceptionHandler().handleDomain(failure);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals(ErrorMessage.INTERNAL_ERROR, response.getBody().get("message"));
            assertEquals(1, capture.events().size());
            LogEvent event = capture.events().get(0);
            String message = event.getMessage().getFormattedMessage();
            assertEquals(Level.ERROR, event.getLevel());
            assertNull(event.getThrown());
            assertTrue(message.contains("TestService#fail:21"));
            assertTrue(message.contains("StorageClient#fail:42"));
            assertFalse(message.contains("private-domain"));
            assertFalse(message.contains("private-cause"));
            assertFalse(message.contains("forged-frame"));
        }
    }

    private static RuntimeException failure(String message, String source, int line, Throwable cause) {
        RuntimeException failure = new RuntimeException(message, cause);
        failure.setStackTrace(new StackTraceElement[]{frame(source, line)});
        return failure;
    }

    private static StackTraceElement frame(String source, int line) {
        return new StackTraceElement("io.github.surezzzzzz.sdk.feedback.server." + source,
                "fail", source + ".java", line);
    }

    private static final class LogCapture implements AutoCloseable {

        private final Logger logger;
        private final Level originalLevel;
        private final CapturingAppender appender;

        private LogCapture(Class<?> source) {
            assertTrue(LoggerFactory.getILoggerFactory().getClass().getName()
                    .startsWith("org.apache.logging.slf4j."));
            logger = (Logger) LogManager.getLogger(source);
            originalLevel = logger.getLevel();
            appender = new CapturingAppender();
            appender.start();
            logger.addAppender(appender);
            logger.setLevel(Level.ERROR);
        }

        private List<LogEvent> events() {
            return appender.events;
        }

        @Override
        public void close() {
            logger.removeAppender(appender);
            appender.stop();
            logger.setLevel(originalLevel);
        }
    }

    private static final class CapturingAppender extends AbstractAppender {

        private final List<LogEvent> events = new CopyOnWriteArrayList<>();

        private CapturingAppender() {
            super("feedback-exception-test-appender", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }
    }
}
