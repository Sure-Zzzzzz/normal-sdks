package io.github.surezzzzzz.sdk.messaging.kafka.consumer.configuration;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.SimpleKafkaConsumerConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.exception.KafkaConsumerConfigurationException;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.KafkaConsumerIdempotencyChecker;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.RedisKafkaConsumerIdempotencyChecker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 幂等检查器自动配置。
 *
 * @author surezzzzzz
 */
@Configuration
public class SimpleKafkaConsumerIdempotencyConfiguration {

    /**
     * Redis 依赖存在时的幂等检查器配置。
     */
    @Configuration
    @ConditionalOnClass(name = SimpleKafkaConsumerConstant.CLASS_NAME_REDIS_ROUTE_REGISTRY)
    @ConditionalOnProperty(prefix = SimpleKafkaConsumerConstant.CONFIG_PREFIX,
            name = SimpleKafkaConsumerConstant.CONFIG_PROPERTY_ENABLE,
            havingValue = "true")
    protected static class RedisIdempotencyConfiguration {

        @Bean
        @ConditionalOnMissingBean(KafkaConsumerIdempotencyChecker.class)
        @ConditionalOnProperty(prefix = SimpleKafkaConsumerConstant.CONFIG_PREFIX,
                name = SimpleKafkaConsumerConstant.CONFIG_PROPERTY_IDEMPOTENCY_ENABLE,
                havingValue = "true")
        public KafkaConsumerIdempotencyChecker redisKafkaConsumerIdempotencyChecker(
                SimpleKafkaConsumerProperties properties,
                ObjectProvider<io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry> redisProvider) {
            io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry registry = redisProvider.getIfAvailable();
            if (registry == null) {
                throw new KafkaConsumerConfigurationException(ErrorCode.CONFIG_INVALID,
                        String.format(ErrorMessage.CONFIG_INVALID,
                                SimpleKafkaConsumerConstant.REASON_IDEMPOTENCY_REDIS_MISSING));
            }
            return new RedisKafkaConsumerIdempotencyChecker(registry, properties);
        }
    }
}
