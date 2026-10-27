package io.github.surezzzzzz.sdk.ticket.server.test.support;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 从测试运行时的 Log4j2 logger 直接捕获目标类事件。
 *
 * @author surezzzzzz
 */
public final class TicketLogCapture implements AutoCloseable {

    private final Logger logger;
    private final Level originalLevel;
    private final CapturingAppender appender;

    /**
     * 挂接目标类 logger 并临时调整级别。
     *
     * @param source 日志来源类
     * @param level  捕获级别
     */
    public TicketLogCapture(Class<?> source, Level level) {
        String binding = LoggerFactory.getILoggerFactory().getClass().getName();
        assertTrue(binding.startsWith("org.apache.logging.slf4j."), "实际 SLF4J 绑定：" + binding);
        logger = (Logger) LogManager.getLogger(source);
        originalLevel = logger.getLevel();
        appender = new CapturingAppender();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(level);
    }

    /**
     * 返回捕获到的事件。
     *
     * @return 已捕获事件列表，事件自身为不可变快照
     */
    public List<LogEvent> events() {
        return appender.events;
    }

    @Override
    public void close() {
        logger.removeAppender(appender);
        appender.stop();
        logger.setLevel(originalLevel);
    }

    private static final class CapturingAppender extends AbstractAppender {

        private final List<LogEvent> events = new CopyOnWriteArrayList<>();

        private CapturingAppender() {
            super("ticket-test-appender", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }
    }
}
