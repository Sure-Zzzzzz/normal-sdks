package io.github.surezzzzzz.sdk.metrics.aksk.resource.test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * AKSK Resource Metrics 测试应用
 *
 * @author surezzzzzz
 */
@SpringBootApplication
public class SimpleAkskResourceMetricsTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleAkskResourceMetricsTestApplication.class, args);
    }

    @Bean
    public SimpleMeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}