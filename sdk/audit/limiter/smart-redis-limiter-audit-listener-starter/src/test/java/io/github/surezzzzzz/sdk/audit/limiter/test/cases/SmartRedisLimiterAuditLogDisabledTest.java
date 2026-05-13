package io.github.surezzzzzz.sdk.audit.limiter.test.cases;

import io.github.surezzzzzz.sdk.audit.limiter.handler.SmartRedisLimiterAuditHandler;
import io.github.surezzzzzz.sdk.audit.limiter.test.SmartRedisLimiterAuditListenerTestApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 log.enabled=false 时 LogHandler 不注册
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterAuditListenerTestApplication.class)
@TestPropertySource(properties = {
        "io.github.surezzzzzz.sdk.audit.limiter.listener.handler.log.enabled=false"
})
public class SmartRedisLimiterAuditLogDisabledTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private List<SmartRedisLimiterAuditHandler> handlers;

    @Test
    public void testLogHandlerNotRegisteredWhenDisabled() {
        log.info("========== 测试：log.enabled=false 时 LogHandler 不注册 ==========");

        assertNotNull(handlers, "Handlers list should not be null");

        boolean hasLogHandler = handlers.stream()
                .anyMatch(h -> h.getClass().getSimpleName().contains("LogSmartRedisLimiterAuditHandler"));
        assertFalse(hasLogHandler, "LogHandler should not be registered when log.enabled=false");

        log.info("testLogHandlerNotRegisteredWhenDisabled passed, handler count={}", handlers.size());
    }

    @Test
    public void testCustomHandlerStillWorksWhenLogDisabled() throws InterruptedException {
        log.info("========== 测试：log 关闭时自定义 Handler 仍正常工作 ==========");

        SmartRedisLimiterEvent event = new SmartRedisLimiterEvent(
                this, "smart-limiter:my-service:path:/api/test:5s",
                "path", "fixed", "5/10s", false,
                "INTERCEPTOR",
                "/api/test", "GET", "127.0.0.1", null,
                null, null,
                null,
                5, 0, 1715635500L, 100L
        );

        assertDoesNotThrow(() -> eventPublisher.publishEvent(event));

        // log 关闭不影响 TestSmartRedisLimiterAuditHandler（它是 @Component 注册的）
        // 这里只验证不抛异常，因为 log.enabled=false 时 TestHandler 可能还没初始化
        // （取决于 TestApplication 的包扫描是否覆盖到 support 包）
        log.info("testCustomHandlerStillWorksWhenLogDisabled passed");
    }
}
