package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.configuration.SimpleKafkaConsumerProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.KafkaConsumerIdempotencyAcquireResult;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.KafkaConsumerIdempotencyAcquireStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.RedisKafkaConsumerIdempotencyChecker;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Redis 幂等检查器测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class RedisKafkaConsumerIdempotencyCheckerTest {

    @Test
    public void testAcquireUsesScopedKeyAndProcessingLease() {
        StringRedisTemplate template = templateReturning(1L);
        RedisKafkaConsumerIdempotencyChecker checker = checker(template);

        KafkaConsumerIdempotencyAcquireResult result = checker.acquire("mock-message", "source-a", "group-a");
        log.info("Redis 领取状态：{}", result.getStatus());

        assertEquals(KafkaConsumerIdempotencyAcquireStatus.ACQUIRED, result.getStatus(), "空 key 必须领取处理租约");
        assertNotNull(result.getLease(), "领取成功必须返回 owner 租约");
        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object> ownerValue = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> leaseMs = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> completedValue = ArgumentCaptor.forClass(Object.class);
        verify(template).execute(any(RedisScript.class), keys.capture(), ownerValue.capture(), leaseMs.capture(),
                completedValue.capture());
        assertEquals("sure-kafka-consumer:idempotency:8:source-a:7:group-a:mock-message", keys.getValue().get(0),
                "Redis key 必须按 datasource/group/messageId 隔离");
        assertTrue(String.valueOf(ownerValue.getValue()).startsWith("PROCESSING:"), "首次写入必须是 owner 处理租约");
        assertEquals("4321", leaseMs.getValue(), "处理租约必须使用 lease-ms");
        assertEquals("COMPLETED", completedValue.getValue(), "领取脚本必须识别完成标记");
    }

    @Test
    public void testCompletedAndInProgressDoNotReturnLease() {
        RedisKafkaConsumerIdempotencyChecker completedChecker = checker(templateReturning(3L));
        RedisKafkaConsumerIdempotencyChecker inProgressChecker = checker(templateReturning(2L));

        KafkaConsumerIdempotencyAcquireResult completed = completedChecker.acquire("mock-message", "source-a", "group-a");
        KafkaConsumerIdempotencyAcquireResult inProgress = inProgressChecker.acquire("mock-message", "source-a", "group-a");
        log.info("Redis 状态：completed={}，inProgress={}", completed.getStatus(), inProgress.getStatus());

        assertEquals(KafkaConsumerIdempotencyAcquireStatus.COMPLETED, completed.getStatus(), "完成标记必须明确返回 COMPLETED");
        assertNull(completed.getLease(), "完成标记不能暴露处理租约");
        assertEquals(KafkaConsumerIdempotencyAcquireStatus.IN_PROGRESS, inProgress.getStatus(), "处理中租约必须明确返回 IN_PROGRESS");
        assertNull(inProgress.getLease(), "他人处理中不能暴露 owner 租约");
    }

    @Test
    public void testCompleteThenReleaseUseOwnerSafeScripts() {
        StringRedisTemplate template = templateReturning(1L, 1L, 0L);
        RedisKafkaConsumerIdempotencyChecker checker = checker(template);
        KafkaConsumerIdempotencyAcquireResult result = checker.acquire("mock-message", "source-a", "group-a");
        boolean completed = result.getLease().complete();
        boolean released = result.getLease().release();
        log.info("Redis owner 租约结果：completed={}，released={}", completed, released);

        assertTrue(completed, "当前 owner 必须能写入完成标记");
        assertFalse(released, "完成标记后旧 owner 不得删除 key");
        verify(template, times(3)).execute(any(RedisScript.class), anyList(), (Object[]) any());
    }

    @Test
    public void testRedisFailureDoesNotPretendMessageIsCompleted() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.execute(any(RedisScript.class), anyList(), (Object[]) any()))
                .thenThrow(new IllegalStateException("mock redis unavailable"));
        RedisKafkaConsumerIdempotencyChecker checker = checker(template);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> checker.acquire("mock-message", "source-a", "group-a"),
                "Redis 状态未知时 checker 不能伪造 COMPLETED 或 IN_PROGRESS");
        log.info("Redis 异常：{}", exception.getMessage());
    }

    private StringRedisTemplate templateReturning(Long... values) {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        AtomicInteger index = new AtomicInteger();
        when(template.execute(any(RedisScript.class), anyList(), (Object[]) any())).thenAnswer(invocation -> {
            int current = index.getAndIncrement();
            return values[Math.min(current, values.length - 1)];
        });
        return template;
    }

    private RedisKafkaConsumerIdempotencyChecker checker(StringRedisTemplate template) {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        when(registry.getStringRedisTemplate("mock-redis")).thenReturn(template);
        SimpleKafkaConsumerProperties properties = new SimpleKafkaConsumerProperties();
        properties.getIdempotency().setRedisRouteKey("mock-redis");
        properties.getIdempotency().setTtlMs(1234L);
        properties.getIdempotency().setLeaseMs(4321L);
        return new RedisKafkaConsumerIdempotencyChecker(registry, properties);
    }
}
