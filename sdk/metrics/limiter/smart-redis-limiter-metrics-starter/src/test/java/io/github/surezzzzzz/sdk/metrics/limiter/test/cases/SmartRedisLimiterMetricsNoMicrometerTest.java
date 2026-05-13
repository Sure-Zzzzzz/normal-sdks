package io.github.surezzzzzz.sdk.metrics.limiter.test.cases;

import io.github.surezzzzzz.sdk.metrics.limiter.listener.SmartRedisLimiterMetricsListener;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试排除 MetricsAutoConfiguration 时 Listener 不注册
 *
 * <p>模拟 Micrometer 不在 classpath 的场景：排除 AutoConfiguration 后，
 * ComponentScan 不执行，MetricsListener Bean 不创建。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterMetricsNoMicrometerTest.NoMetricsConfig.class,
        properties = "spring.autoconfigure.exclude=" +
                "io.github.surezzzzzz.sdk.metrics.limiter.configuration.SmartRedisLimiterMetricsAutoConfiguration")
public class SmartRedisLimiterMetricsNoMicrometerTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Configuration
    @EnableAutoConfiguration
    static class NoMetricsConfig {

        @Bean
        public SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Test
    public void testMetricsListenerNotRegisteredWithoutAutoConfiguration() {
        log.info("========== 测试：排除 AutoConfiguration 时 MetricsListener 不注册 ==========");

        boolean hasListener = applicationContext.getBeansOfType(SmartRedisLimiterMetricsListener.class).size() > 0;
        assertFalse(hasListener, "MetricsListener should not be registered without AutoConfiguration");

        log.info("testMetricsListenerNotRegisteredWithoutAutoConfiguration passed");
    }
}
