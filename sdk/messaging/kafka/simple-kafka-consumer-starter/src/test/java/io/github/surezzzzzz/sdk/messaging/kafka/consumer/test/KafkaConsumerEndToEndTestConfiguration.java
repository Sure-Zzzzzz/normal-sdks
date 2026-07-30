package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test;

import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.configuration.SimpleKafkaConsumerProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.error.DeadLetterPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.error.DefaultDeadLetterPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.KafkaConsumerIdempotencyAcquireResult;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.KafkaConsumerIdempotencyChecker;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.RedisKafkaConsumerIdempotencyChecker;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.listener.KafkaConsumerEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.support.FailOnceDeadLetterPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.support.KafkaConsumerE2eRecorder;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Consumer 端到端测试专用 Bean。
 *
 * @author surezzzzzz
 */
@TestConfiguration
public class KafkaConsumerEndToEndTestConfiguration {

    @Bean
    public KafkaConsumerEventListener kafkaConsumerE2eEventListener() {
        return KafkaConsumerE2eRecorder::event;
    }

    @Bean
    public KafkaConsumerIdempotencyChecker kafkaConsumerIdempotencyChecker(SimpleRedisRouteRegistry redisRouteRegistry,
                                                                           SimpleKafkaConsumerProperties properties) {
        KafkaConsumerIdempotencyChecker delegate = new RedisKafkaConsumerIdempotencyChecker(redisRouteRegistry,
                properties);
        return (messageId, datasourceKey, groupId) -> {
            KafkaConsumerIdempotencyAcquireResult result = delegate.acquire(messageId, datasourceKey, groupId);
            KafkaConsumerE2eRecorder.recordIdempotencyStatus(messageId, result.getStatus());
            return result;
        };
    }

    @Bean
    @Primary
    public FailOnceDeadLetterPublisher failOnceDeadLetterPublisher(SimpleKafkaRouteRegistry routeRegistry,
                                                                   SimpleKafkaConsumerProperties properties) {
        DeadLetterPublisher delegate = new DefaultDeadLetterPublisher(routeRegistry, properties);
        return new FailOnceDeadLetterPublisher(delegate);
    }
}
