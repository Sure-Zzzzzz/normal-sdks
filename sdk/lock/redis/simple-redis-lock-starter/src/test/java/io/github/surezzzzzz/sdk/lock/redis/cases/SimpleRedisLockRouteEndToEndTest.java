package io.github.surezzzzzz.sdk.lock.redis.cases;

import io.github.surezzzzzz.sdk.lock.redis.LockApplication;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.lock.redis.executor.RedisLockExecutor;
import io.github.surezzzzzz.sdk.lock.redis.executor.RouteRedisLockExecutor;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis 分布式锁 route 模式端到端测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@ActiveProfiles("redis-lock-route")
@SpringBootTest(classes = LockApplication.class)
public class SimpleRedisLockRouteEndToEndTest {

    private static final String LOCK_KEY = "lock:test:route:001";
    private static final String DEFAULT_KEY = "default:test:route:001";
    private static final String LOCK_VALUE = "route-client-id";

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private SimpleRedisLock simpleRedisLock;

    @Autowired
    private RedisLockExecutor redisLockExecutor;

    @Autowired
    private RedisRouteTemplate redisRouteTemplate;

    @Autowired
    @Qualifier("stringRedisTemplate")
    private StringRedisTemplate defaultRedisTemplate;

    @AfterEach
    public void cleanUp() {
        defaultRedisTemplate.delete(Arrays.asList(LOCK_KEY, DEFAULT_KEY));
        redisRouteTemplate.executeOn("lock", template -> {
            template.delete(Arrays.asList(LOCK_KEY, DEFAULT_KEY));
            return null;
        });
    }

    @Test
    public void testRouteModeUsesRouteExecutorAndDoesNotCreateDefaultLockTemplate() {
        log.info("验证 route 模式使用 RouteRedisLockExecutor，且不注册 simpleRedisLockRedisTemplate");
        assertTrue(redisLockExecutor instanceof RouteRedisLockExecutor, "route 模式必须使用 RouteRedisLockExecutor");
        assertThrows(NoSuchBeanDefinitionException.class,
                () -> applicationContext.getBean("simpleRedisLockRedisTemplate"),
                "route 模式不应注册 simpleRedisLockRedisTemplate");
    }

    @Test
    public void testLockKeyRoutesToLockDatasource() {
        log.info("验证 lock 前缀 key 路由到 lock datasource，lockKey={}", LOCK_KEY);
        assertTrue(simpleRedisLock.tryLock(LOCK_KEY, LOCK_VALUE, 10, TimeUnit.SECONDS), "route 模式 lock datasource 加锁应成功");
        assertNull(defaultRedisTemplate.opsForValue().get(LOCK_KEY), "lock datasource 加锁后 default datasource 不应读到 key");
        String lockValue = redisRouteTemplate.executeOn("lock", template -> template.opsForValue().get(LOCK_KEY));
        assertEquals(LOCK_VALUE, lockValue, "lock datasource 应读到锁 value");
        assertTrue(simpleRedisLock.unlock(LOCK_KEY, LOCK_VALUE), "route 模式 lock datasource 解锁应成功");
        String afterUnlock = redisRouteTemplate.executeOn("lock", template -> template.opsForValue().get(LOCK_KEY));
        assertNull(afterUnlock, "解锁后 lock datasource 中 key 应删除");
    }

    @Test
    public void testDefaultKeyRoutesToDefaultDatasource() {
        log.info("验证非 lock 前缀 key 路由到 default datasource，lockKey={}", DEFAULT_KEY);
        assertTrue(simpleRedisLock.tryLock(DEFAULT_KEY, LOCK_VALUE, 10, TimeUnit.SECONDS), "route 模式 default datasource 加锁应成功");
        assertEquals(LOCK_VALUE, defaultRedisTemplate.opsForValue().get(DEFAULT_KEY), "default datasource 应读到锁 value");
        String lockDatasourceValue = redisRouteTemplate.executeOn("lock", template -> template.opsForValue().get(DEFAULT_KEY));
        assertNull(lockDatasourceValue, "default datasource 加锁后 lock datasource 不应读到 key");
        assertTrue(simpleRedisLock.unlock(DEFAULT_KEY, LOCK_VALUE), "route 模式 default datasource 解锁应成功");
        assertNull(defaultRedisTemplate.opsForValue().get(DEFAULT_KEY), "解锁后 default datasource 中 key 应删除");
    }

    @Test
    public void testWrongValueUnlockKeepsRouteLock() {
        log.info("验证 route 模式错误 value 解锁不会删除锁，lockKey={}", LOCK_KEY);
        assertTrue(simpleRedisLock.tryLock(LOCK_KEY, LOCK_VALUE, 10, TimeUnit.SECONDS), "route 模式加锁应成功");
        assertFalse(simpleRedisLock.unlock(LOCK_KEY, "route-client-wrong"), "错误 value 解锁应返回 false");
        String lockValue = redisRouteTemplate.executeOn("lock", template -> template.opsForValue().get(LOCK_KEY));
        assertEquals(LOCK_VALUE, lockValue, "错误 value 解锁后锁仍应保留");
    }
}
