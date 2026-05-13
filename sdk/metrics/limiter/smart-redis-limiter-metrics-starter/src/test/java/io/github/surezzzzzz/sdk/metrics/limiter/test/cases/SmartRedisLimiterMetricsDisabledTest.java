package io.github.surezzzzzz.sdk.metrics.limiter.test.cases;

import io.github.surezzzzzz.sdk.metrics.limiter.listener.SmartRedisLimiterMetricsListener;
import io.github.surezzzzzz.sdk.metrics.limiter.test.SmartRedisLimiterMetricsTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 metrics.enable=false 时 Listener 不注册
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterMetricsTestApplication.class)
@TestPropertySource(properties = {
        "io.github.surezzzzzz.sdk.metrics.limiter.enable=false"
})
public class SmartRedisLimiterMetricsDisabledTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void testMetricsListenerNotRegisteredWhenDisabled() {
        log.info("========== 测试：enable=false 时 MetricsListener 不注册 ==========");

        boolean hasListener = applicationContext.getBeansOfType(SmartRedisLimiterMetricsListener.class).size() > 0;
        assertFalse(hasListener, "MetricsListener should not be registered when enable=false");

        log.info("testMetricsListenerNotRegisteredWhenDisabled passed");
    }
}
