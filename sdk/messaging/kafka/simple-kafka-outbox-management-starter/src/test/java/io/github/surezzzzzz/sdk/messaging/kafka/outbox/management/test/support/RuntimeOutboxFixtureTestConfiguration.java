package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.test.support;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.KafkaPublisher;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Runtime 测试夹具配置。
 *
 * @author surezzzzzz
 */
@TestConfiguration
public class RuntimeOutboxFixtureTestConfiguration {
    /**
     * 提供 Runtime 自动配置所需的测试发布器。
     */
    @Bean
    KafkaPublisher kafkaPublisher() {
        return Mockito.mock(KafkaPublisher.class);
    }
}
