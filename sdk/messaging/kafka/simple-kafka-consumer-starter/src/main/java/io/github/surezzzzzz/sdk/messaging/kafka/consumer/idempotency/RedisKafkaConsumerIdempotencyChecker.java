package io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.configuration.SimpleKafkaConsumerProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.SimpleKafkaConsumerConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.support.KafkaConsumerStringHelper;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;

/**
 * 基于 redis-route 的幂等检查器，使用 owner 安全的处理租约与完成标记。
 *
 * @author surezzzzzz
 */
@Slf4j
public class RedisKafkaConsumerIdempotencyChecker implements KafkaConsumerIdempotencyChecker {

    private static final long ACQUIRED = 1L;
    private static final long IN_PROGRESS = 2L;
    private static final long COMPLETED = 3L;
    private static final long SCRIPT_SUCCESS = 1L;
    private static final String PROCESSING_PREFIX =
            SimpleKafkaConsumerConstant.IDEMPOTENCY_PROCESSING_VALUE_PREFIX;
    private static final String COMPLETED_VALUE =
            SimpleKafkaConsumerConstant.IDEMPOTENCY_COMPLETED_VALUE;

    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>(
            "local value = redis.call('GET', KEYS[1]); "
                    + "if not value then redis.call('PSETEX', KEYS[1], ARGV[2], ARGV[1]); return 1; end; "
                    + "if value == ARGV[3] then return 3; end; return 2;", Long.class);
    private static final DefaultRedisScript<Long> COMPLETE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "redis.call('PSETEX', KEYS[1], ARGV[3], ARGV[2]); return 1; end; return 0;", Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then redis.call('DEL', KEYS[1]); return 1; end; return 0;",
            Long.class);

    private final SimpleRedisRouteRegistry registry;
    private final SimpleKafkaConsumerProperties properties;

    public RedisKafkaConsumerIdempotencyChecker(SimpleRedisRouteRegistry registry,
                                                SimpleKafkaConsumerProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @Override
    public KafkaConsumerIdempotencyAcquireResult acquire(String messageId, String datasourceKey, String groupId) {
        if (!KafkaConsumerStringHelper.hasText(messageId)) {
            return KafkaConsumerIdempotencyAcquireResult.acquired(NoOpLease.INSTANCE);
        }
        String ownerValue = PROCESSING_PREFIX + UUID.randomUUID().toString();
        String key = buildKey(messageId, datasourceKey, groupId);
        Long result = template().execute(ACQUIRE_SCRIPT, Collections.singletonList(key), ownerValue,
                String.valueOf(properties.getIdempotency().getLeaseMs()), COMPLETED_VALUE);
        if (Long.valueOf(ACQUIRED).equals(result)) {
            return KafkaConsumerIdempotencyAcquireResult.acquired(new RedisLease(key, ownerValue));
        }
        if (Long.valueOf(COMPLETED).equals(result)) {
            return KafkaConsumerIdempotencyAcquireResult.completed();
        }
        if (Long.valueOf(IN_PROGRESS).equals(result)) {
            log.debug("幂等检查发现处理中租约，等待 Kafka 重投：messageId=[{}]",
                    KafkaConsumerStringHelper.safeDisplay(messageId));
            return KafkaConsumerIdempotencyAcquireResult.inProgress();
        }
        throw new IllegalStateException("Redis 幂等领取脚本返回非法结果");
    }

    private StringRedisTemplate template() {
        return registry.getStringRedisTemplate(properties.getIdempotency().getRedisRouteKey());
    }

    private String buildKey(String messageId, String datasourceKey, String groupId) {
        if (!KafkaConsumerStringHelper.hasText(datasourceKey) || !KafkaConsumerStringHelper.hasText(groupId)) {
            return String.format(SimpleKafkaConsumerConstant.IDEMPOTENCY_REDIS_KEY_TEMPLATE, messageId);
        }
        String scopedMessageId = String.format("%d:%s:%d:%s:%s", datasourceKey.length(), datasourceKey,
                groupId.length(), groupId, messageId);
        return String.format(SimpleKafkaConsumerConstant.IDEMPOTENCY_REDIS_KEY_TEMPLATE, scopedMessageId);
    }

    private enum NoOpLease implements KafkaConsumerIdempotencyLease {
        INSTANCE;

        @Override
        public boolean complete() {
            return true;
        }

        @Override
        public boolean release() {
            return true;
        }
    }

    private class RedisLease implements KafkaConsumerIdempotencyLease {

        private final String key;
        private final String ownerValue;

        private RedisLease(String key, String ownerValue) {
            this.key = key;
            this.ownerValue = ownerValue;
        }

        @Override
        public boolean complete() {
            Long result = template().execute(COMPLETE_SCRIPT, Collections.singletonList(key), ownerValue,
                    COMPLETED_VALUE, String.valueOf(properties.getIdempotency().getTtlMs()));
            return Long.valueOf(SCRIPT_SUCCESS).equals(result);
        }

        @Override
        public boolean release() {
            Long result = template().execute(RELEASE_SCRIPT, Collections.singletonList(key), ownerValue);
            return Long.valueOf(SCRIPT_SUCCESS).equals(result);
        }
    }
}
