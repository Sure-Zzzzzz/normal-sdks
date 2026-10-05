package io.github.surezzzzzz.sdk.expression.condition.parser.test.cases;

import io.github.surezzzzzz.sdk.expression.condition.parser.exception.ConditionExpressionParseException;
import io.github.surezzzzzz.sdk.expression.condition.parser.parser.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.*;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 直接观察主代码 DEBUG 和 stderr，默认错误不得回显输入。 */
@Slf4j
class ParserDiagnosticTest {
    @Test
    void realDebugAndDefaultErrorsRemainContentFree() throws Exception {
        log.info("验证主代码诊断不包含字段、值、原文或原因");
        ConditionExpressionParser parser = new ConditionExpressionParser(new ValueParser(Arrays.asList(
                new BooleanValueParseStrategy(), new TimeRangeValueParseStrategy(), new NumberValueParseStrategy(), new StringValueParseStrategy())));
        StringBuilder captured = new StringBuilder();
        AbstractAppender appender = new AbstractAppender("condition-test-capture", null, PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY) {
            public void append(LogEvent event) {
                captured.append(event.getMessage().getFormattedMessage()).append('\n');
                if (event.getThrown() != null) captured.append(event.getThrown().toString());
            }
        };
        org.apache.logging.log4j.core.Logger logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(ConditionExpressionParser.class);
        Level previous = logger.getLevel();
        PrintStream stderr = System.err;
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        try (PrintStream redirected = new PrintStream(errors, true, StandardCharsets.UTF_8.name())) {
            System.setErr(redirected);
            assertNotNull(parser.parse("diagnosticSecretField='diagnosticSecretValue'"));
            ConditionExpressionParseException ex = assertThrows(ConditionExpressionParseException.class,
                    () -> parser.parse("diagnosticSecretField='diagnosticSecretValue' @"));
            assertEquals("diagnosticSecretField='diagnosticSecretValue' @", ex.getExpression());
            assertFalse(ex.getMessage().contains("diagnosticSecret"));
            assertFalse(ex.toString().contains("diagnosticSecret"));
            assertFalse(ex.getSuggestion().contains("diagnosticSecret"));
            assertNull(ex.getCause());
            ConditionExpressionParseException invalid = ConditionExpressionParseException.missingValue("diagnosticSecretField=", 1, 2, "diagnosticSecretToken");
            assertFalse(invalid.getMessage().contains("diagnosticSecret"));
            assertEquals("diagnosticSecretToken", invalid.getOffendingToken());
        } finally {
            System.setErr(stderr);
            logger.removeAppender(appender);
            logger.setLevel(previous);
            appender.stop();
        }
        assertTrue(captured.toString().contains("Condition parse start"));
        assertTrue(captured.toString().contains("Condition lexical complete"));
        assertTrue(captured.toString().contains("Condition parse complete"));
        assertTrue(captured.toString().contains("Condition parse rejected"));
        assertFalse(captured.toString().contains("diagnosticSecret"));
        assertEquals("", errors.toString(StandardCharsets.UTF_8.name()));
    }
}
