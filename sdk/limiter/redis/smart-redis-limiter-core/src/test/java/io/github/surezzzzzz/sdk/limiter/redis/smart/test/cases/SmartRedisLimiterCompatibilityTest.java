package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterEventPayload;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterRecord;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * SmartRedisLimiter 2.0.0 二进制契约兼容测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterCompatibilityTest {

    @Test
    public void testPublishedConstructorsRemainAvailable() throws Exception {
        log.info("开始测试 2.0.0 已发布构造器兼容性");
        Constructor<SmartRedisLimiterEventPayload> payloadConstructor =
                SmartRedisLimiterEventPayload.class.getConstructor(
                        String.class, String.class, String.class, String.class,
                        boolean.class, boolean.class,
                        String.class, String.class, String.class, boolean.class,
                        String.class, String.class, String.class, String.class,
                        String.class, String.class, String.class, Map.class,
                        long.class, long.class, long.class, long.class, String.class);
        assertNotNull(payloadConstructor);

        Constructor<SmartRedisLimiterEvent> eventConstructor =
                SmartRedisLimiterEvent.class.getConstructor(
                        Object.class, String.class, String.class, String.class,
                        String.class, boolean.class, String.class,
                        String.class, String.class, String.class, String.class,
                        String.class, String.class, Map.class,
                        long.class, long.class, long.class, long.class);
        assertNotNull(eventConstructor);

        Constructor<SmartRedisLimiterRecord> recordConstructor =
                SmartRedisLimiterRecord.class.getConstructor(
                        String.class, String.class, String.class, String.class,
                        String.class, String.class, String.class, String.class,
                        boolean.class, String.class, String.class, String.class,
                        boolean.class, boolean.class, String.class,
                        String.class, String.class, String.class, String.class,
                        String.class, String.class, String.class,
                        long.class, long.class, long.class, long.class,
                        Long.class, String.class, Map.class);
        assertNotNull(recordConstructor, "2.0.0 Record 29 参数构造器应继续存在");
        log.info("2.0.0 已发布构造器兼容性测试通过");
    }

    @Test
    public void testPublishedSourceMethodsRemainAvailable() throws Exception {
        log.info("开始测试 2.0.0 来源方法兼容性");
        Method getSource = SmartRedisLimiterEvent.class.getMethod("getSource");
        Method getRawSource = SmartRedisLimiterEvent.class.getMethod("getRawSource");
        assertEquals(String.class, getSource.getReturnType());
        assertEquals(Object.class, getRawSource.getReturnType(), "getRawSource 返回类型应保持 Object");
        log.info("2.0.0 来源方法兼容性测试通过");
    }
}
