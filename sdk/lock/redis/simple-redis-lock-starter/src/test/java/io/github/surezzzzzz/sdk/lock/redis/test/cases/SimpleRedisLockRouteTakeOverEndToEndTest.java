package io.github.surezzzzzz.sdk.lock.redis.test.cases;

import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.lock.redis.constant.SimpleRedisLockConstant;
import io.github.surezzzzzz.sdk.lock.redis.executor.DefaultRedisLockExecutor;
import io.github.surezzzzzz.sdk.lock.redis.executor.RedisLockExecutor;
import io.github.surezzzzzz.sdk.lock.redis.test.SimpleRedisLockTestApplication;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis 分布式锁「route 接管 + lock 默认关」让位端到端测试。
 *
 * <p>场景：{@code io.github.surezzzzz.sdk.redis.route.enable=true}（route 接管标准
 * {@code stringRedisTemplate}），lock 自身 route 开关保持缺省 false。
 * 验证 lock 自建模板按类型让位，不自建 {@code simpleRedisLockRedisTemplate}，
 * 容器内 StringRedisTemplate 唯一，lock 功能在让位后仍正常工作。
 *
 * @author surezzzzzz
 */
@Slf4j
@ActiveProfiles("redis-lock-takeover")
@SpringBootTest(classes = SimpleRedisLockTestApplication.class)
public class SimpleRedisLockRouteTakeOverEndToEndTest {

    private static final String LOCK_KEY_PREFIX = "lock:takeover:";

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private SimpleRedisLock simpleRedisLock;

    @Autowired
    private RedisLockExecutor redisLockExecutor;

    @Autowired
    @Qualifier("stringRedisTemplate")
    private StringRedisTemplate stringRedisTemplate;

    @AfterEach
    public void cleanUp() {
        stringRedisTemplate.delete(Arrays.asList(
                LOCK_KEY_PREFIX + "001", LOCK_KEY_PREFIX + "002"));
    }

    @Test
    public void testTakeOverModeYieldsSelfTemplateAndKeepsSingleCandidate() {
        log.info("验证 route 接管时 lock 自建模板让位，StringRedisTemplate 唯一");

        Map<String, StringRedisTemplate> templates =
                applicationContext.getBeansOfType(StringRedisTemplate.class);
        assertEquals(1, templates.size(), "route 接管后容器内 StringRedisTemplate 必须唯一，实际: " + templates.keySet());
        assertTrue(templates.containsKey("stringRedisTemplate"), "唯一候选必须是 route 接管的标准 Bean");
        assertFalse(templates.containsKey(SimpleRedisLockConstant.SIMPLE_REDIS_LOCK_REDIS_TEMPLATE_BEAN_NAME),
                "lock 默认分支不得自建 simpleRedisLockRedisTemplate");

        try {
            applicationContext.getBean(SimpleRedisLockConstant.SIMPLE_REDIS_LOCK_REDIS_TEMPLATE_BEAN_NAME);
            fail("simpleRedisLockRedisTemplate 应不存在");
        } catch (NoSuchBeanDefinitionException e) {
            log.info("✓ simpleRedisLockRedisTemplate 已让位不存在");
        }

        assertSame(stringRedisTemplate, templates.get("stringRedisTemplate"));
        assertTrue(redisLockExecutor instanceof DefaultRedisLockExecutor,
                "lock 默认分支应使用 DefaultRedisLockExecutor，实际: " + redisLockExecutor.getClass().getName());
        log.info("✓ 让位后执行器={}，模板解析到标准 Bean", redisLockExecutor.getClass().getName());
    }

    @Test
    public void testTakeOverModeLockStillWorks() {
        log.info("验证让位后加锁/互斥/解锁仍正常");

        String key = LOCK_KEY_PREFIX + "001";
        String owner = UUID.randomUUID().toString();

        assertTrue(simpleRedisLock.tryLock(key, owner, 10, TimeUnit.SECONDS), "首次加锁应成功");
        assertFalse(simpleRedisLock.tryLock(key, "other-owner", 10, TimeUnit.SECONDS), "互斥加锁应失败");
        assertTrue(simpleRedisLock.unlock(key, owner), "持有者解锁应成功");
        assertFalse(simpleRedisLock.unlock(key, owner), "重复解锁应失败");
        assertTrue(simpleRedisLock.tryLock(key, "other-owner", 10, TimeUnit.SECONDS), "解锁后他人可加锁");
        log.info("✓ 让位后锁语义完整");
    }
}
