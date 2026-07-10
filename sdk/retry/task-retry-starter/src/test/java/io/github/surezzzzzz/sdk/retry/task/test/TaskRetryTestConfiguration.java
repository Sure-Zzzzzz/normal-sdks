package io.github.surezzzzzz.sdk.retry.task.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Task Retry 测试配置
 *
 * @author surezzzzzz
 */
@TestConfiguration
public class TaskRetryTestConfiguration {

    @Bean
    @Primary
    public NoopRetrySleeper noopRetrySleeper() {
        return new NoopRetrySleeper();
    }
}
