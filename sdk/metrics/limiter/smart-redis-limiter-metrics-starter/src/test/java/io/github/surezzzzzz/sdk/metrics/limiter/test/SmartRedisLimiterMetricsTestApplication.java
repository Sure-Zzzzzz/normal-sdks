package io.github.surezzzzzz.sdk.metrics.limiter.test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * SmartRedisLimiter 指标测试应用
 *
 * @author surezzzzzz
 */
@SpringBootApplication
public class SmartRedisLimiterMetricsTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartRedisLimiterMetricsTestApplication.class, args);
    }

    @Bean
    public SimpleMeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}
