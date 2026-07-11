package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.exception.RouteException;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import io.github.surezzzzzz.sdk.redis.route.test.SimpleRedisRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis route 端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleRedisRouteTestApplication.class)
public class RedisRouteEndToEndTest {

    @Autowired
    private RedisRouteTemplate template;

    @AfterEach
    public void cleanUp() {
        template.executeOn("default", redisTemplate -> redisTemplate.delete("route:default:001"));
        template.executeOn("cache", redisTemplate -> redisTemplate.delete("cache:user:001"));
        template.executeOn("cache", redisTemplate -> redisTemplate.delete("cache:order:001"));
        template.executeOn("lock", redisTemplate -> redisTemplate.delete("lock:order:001"));
    }

    @Test
    public void testExecuteByKeyAndDatasource() {
        String cacheValue = template.execute("cache:user:001", redisTemplate -> {
            redisTemplate.opsForValue().set("cache:user:001", "cache-value");
            return redisTemplate.opsForValue().get("cache:user:001");
        });
        String lockValue = template.executeOn("lock", redisTemplate -> {
            redisTemplate.opsForValue().set("lock:order:001", "lock-value");
            return redisTemplate.opsForValue().get("lock:order:001");
        });
        String defaultValue = template.executeOn("default", redisTemplate -> {
            redisTemplate.opsForValue().set("route:default:001", "default-value");
            return redisTemplate.opsForValue().get("route:default:001");
        });

        assertEquals("cache-value", cacheValue);
        assertEquals("lock-value", lockValue);
        assertEquals("default-value", defaultValue);
        assertSame(template.stringTemplate("cache"), template.stringTemplateByKey("cache:user:001"));
        assertSame(template.stringTemplate("lock"), template.stringTemplateByKey("lock:order:001"));
        assertSame(template.connectionFactory("lock"), template.connectionFactoryByKey("lock:order:001"));
        assertEquals("cache-value", template.stringTemplate("cache").opsForValue().get("cache:user:001"));
        assertEquals("lock-value", template.stringTemplate("lock").opsForValue().get("lock:order:001"));
        assertEquals("default-value", template.stringTemplate().opsForValue().get("route:default:001"));
        assertNull(template.stringTemplate().opsForValue().get("cache:user:001"));
        assertNull(template.stringTemplate("cache").opsForValue().get("lock:order:001"));
        assertNull(template.stringTemplate("lock").opsForValue().get("route:default:001"));
    }

    @Test
    public void testMultiKeySameDatasource() {
        Boolean result = template.execute(Arrays.asList("cache:user:001", "cache:order:001"), redisTemplate -> {
            assertSame(template.stringTemplate("cache"), redisTemplate);
            redisTemplate.opsForValue().set("cache:user:001", "user-value");
            redisTemplate.opsForValue().set("cache:order:001", "order-value");
            return "user-value".equals(redisTemplate.opsForValue().get("cache:user:001"))
                    && "order-value".equals(redisTemplate.opsForValue().get("cache:order:001"));
        });
        assertTrue(result);
        assertEquals("user-value", template.stringTemplate("cache").opsForValue().get("cache:user:001"));
        assertEquals("order-value", template.stringTemplate("cache").opsForValue().get("cache:order:001"));
        assertNull(template.stringTemplate().opsForValue().get("cache:user:001"));
        assertNull(template.stringTemplate("lock").opsForValue().get("cache:order:001"));
    }

    @Test
    public void testMultiKeyCrossDatasourceThrows() {
        RouteException exception = assertThrows(RouteException.class,
                () -> template.execute(Arrays.asList("cache:user:001", "lock:order:001"), redisTemplate -> redisTemplate));
        assertEquals(ErrorCode.REDIS_ROUTE_009, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("cache"));
        assertTrue(exception.getMessage().contains("lock"));
        assertTrue(exception.getMessage().contains("cache:user:001"));
        assertTrue(exception.getMessage().contains("lock:order:001"));
    }

    @Test
    public void testInputBoundary() {
        assertEquals(ErrorCode.REDIS_ROUTE_008,
                assertThrows(RouteException.class, () -> template.execute(" ", redisTemplate -> redisTemplate)).getErrorCode());
        assertEquals(ErrorCode.REDIS_ROUTE_008,
                assertThrows(RouteException.class, () -> template.execute(Collections.emptyList(), redisTemplate -> redisTemplate)).getErrorCode());
        assertEquals(ErrorCode.REDIS_ROUTE_010,
                assertThrows(RouteException.class, () -> template.execute("cache:user:001", null)).getErrorCode());
        assertEquals(ErrorCode.REDIS_ROUTE_003,
                assertThrows(RouteException.class, () -> template.executeOn(" ", redisTemplate -> redisTemplate)).getErrorCode());
    }
}
