package io.github.surezzzzzz.sdk.lock.redis.test.cases;

import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.lock.redis.constant.SimpleRedisLockConstant;
import io.github.surezzzzzz.sdk.lock.redis.executor.RedisLockExecutor;
import io.github.surezzzzzz.sdk.lock.redis.executor.RouteRedisLockExecutor;
import io.github.surezzzzzz.sdk.lock.redis.model.RedisLockLease;
import io.github.surezzzzzz.sdk.lock.redis.test.SimpleRedisLockTestApplication;
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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis 分布式锁 route 模式端到端测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@ActiveProfiles("redis-lock-route")
@SpringBootTest(classes = SimpleRedisLockTestApplication.class)
public class SimpleRedisLockRouteEndToEndTest {

    private static final String LOCK_KEY = "lock:test:route:001";
    private static final String DEFAULT_KEY = "default:test:route:001";
    private static final String LOCK_VALUE = "route-client-id";
    private static final String LEASE_LOCK_KEY = "lock:test:route:lease";
    private static final String LEASE_DEFAULT_KEY = "default:test:route:lease";
    private static final String LEASE_STALE_KEY = "lock:test:route:lease:stale";

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
        defaultRedisTemplate.delete(Arrays.asList(
                LOCK_KEY, DEFAULT_KEY, LEASE_LOCK_KEY, LEASE_DEFAULT_KEY, LEASE_STALE_KEY));
        redisRouteTemplate.executeOn("lock", template -> {
            template.delete(Arrays.asList(
                    LOCK_KEY, DEFAULT_KEY, LEASE_LOCK_KEY, LEASE_DEFAULT_KEY, LEASE_STALE_KEY));
            return null;
        });
    }

    @Test
    public void testRouteModeUsesRouteExecutorAndDoesNotCreateDefaultLockTemplate() {
        log.info("验证 route 模式使用 RouteRedisLockExecutor，且不注册 simpleRedisLockRedisTemplate");
        NoSuchBeanDefinitionException exception = null;
        try {
            applicationContext.getBean(SimpleRedisLockConstant.SIMPLE_REDIS_LOCK_REDIS_TEMPLATE_BEAN_NAME);
        } catch (NoSuchBeanDefinitionException e) {
            exception = e;
        }
        log.info("route executor 类型={}，缺失模板异常消息={}", redisLockExecutor.getClass().getName(),
                exception == null ? null : exception.getMessage());
        assertTrue(redisLockExecutor instanceof RouteRedisLockExecutor, "route 模式必须使用 RouteRedisLockExecutor");
        assertNotNull(exception, "route 模式不应注册 simpleRedisLockRedisTemplate");
        assertEquals(SimpleRedisLockConstant.SIMPLE_REDIS_LOCK_REDIS_TEMPLATE_BEAN_NAME, exception.getBeanName(),
                "缺失 Bean 必须是 simpleRedisLockRedisTemplate");
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

    @Test
    public void testLeaseLifecycleUsesLockDatasourceWithoutDefaultFallback() {
        log.info("验证 lock 前缀租约的获取、续租和释放始终命中 lock datasource，lockKey={}", LEASE_LOCK_KEY);
        Optional<RedisLockLease> optionalLease = simpleRedisLock.tryLockWithLease(
                LEASE_LOCK_KEY, 1, TimeUnit.SECONDS);
        boolean defaultExistsAfterAcquire = Boolean.TRUE.equals(defaultRedisTemplate.hasKey(LEASE_LOCK_KEY));
        Boolean lockExistsAfterAcquire = redisRouteTemplate.executeOn(
                "lock", template -> template.hasKey(LEASE_LOCK_KEY));
        log.info("lock 租约获取后，defaultExists={}，lockExists={}，leasePresent={}",
                defaultExistsAfterAcquire, lockExistsAfterAcquire, optionalLease.isPresent());
        assertTrue(optionalLease.isPresent(), "lock datasource 首次获取租约应成功");
        assertFalse(defaultExistsAfterAcquire, "lock 前缀租约获取后 default datasource 不应存在 key");
        assertTrue(Boolean.TRUE.equals(lockExistsAfterAcquire), "lock 前缀租约获取后 lock datasource 必须存在 key");

        RedisLockLease lease = optionalLease.get();
        boolean renewed = lease.renew(2, TimeUnit.SECONDS);
        Long lockPttl = redisRouteTemplate.executeOn(
                "lock", template -> template.getExpire(LEASE_LOCK_KEY, TimeUnit.MILLISECONDS));
        boolean defaultExistsAfterRenew = Boolean.TRUE.equals(defaultRedisTemplate.hasKey(LEASE_LOCK_KEY));
        log.info("lock 租约续租后，renewed={}，lockPttl={}，defaultExists={}",
                renewed, lockPttl, defaultExistsAfterRenew);
        assertTrue(renewed, "lock datasource 中当前 owner 应能续租");
        assertNotNull(lockPttl, "lock datasource 续租后 PTTL 不应为 null");
        assertTrue(lockPttl > 1000L, "续租后的 PTTL 应体现新的租约时长");
        assertFalse(defaultExistsAfterRenew, "续租不得错误回退并写入 default datasource");

        boolean released = lease.release();
        Boolean lockExistsAfterRelease = redisRouteTemplate.executeOn(
                "lock", template -> template.hasKey(LEASE_LOCK_KEY));
        boolean defaultExistsAfterRelease = Boolean.TRUE.equals(defaultRedisTemplate.hasKey(LEASE_LOCK_KEY));
        log.info("lock 租约释放后，released={}，lockExists={}，defaultExists={}",
                released, lockExistsAfterRelease, defaultExistsAfterRelease);
        assertTrue(released, "lock datasource 中当前 owner 应能释放租约");
        assertFalse(Boolean.TRUE.equals(lockExistsAfterRelease), "释放后 lock datasource 中 key 应删除");
        assertFalse(defaultExistsAfterRelease, "释放不得影响或写入 default datasource");
    }

    @Test
    public void testLeaseLifecycleUsesDefaultDatasourceWhenRuleDoesNotMatch() {
        log.info("验证未命中规则的租约完整生命周期命中 default datasource，lockKey={}", LEASE_DEFAULT_KEY);
        Optional<RedisLockLease> optionalLease = simpleRedisLock.tryLockWithLease(
                LEASE_DEFAULT_KEY, 1, TimeUnit.SECONDS);
        boolean defaultExistsAfterAcquire = Boolean.TRUE.equals(defaultRedisTemplate.hasKey(LEASE_DEFAULT_KEY));
        Boolean lockExistsAfterAcquire = redisRouteTemplate.executeOn(
                "lock", template -> template.hasKey(LEASE_DEFAULT_KEY));
        log.info("default 租约获取后，defaultExists={}，lockExists={}，leasePresent={}",
                defaultExistsAfterAcquire, lockExistsAfterAcquire, optionalLease.isPresent());
        assertTrue(optionalLease.isPresent(), "default datasource 首次获取租约应成功");
        assertTrue(defaultExistsAfterAcquire, "未命中规则的租约应写入 default datasource");
        assertFalse(Boolean.TRUE.equals(lockExistsAfterAcquire), "未命中规则的租约不应写入 lock datasource");

        RedisLockLease lease = optionalLease.get();
        boolean renewed = lease.renew(2, TimeUnit.SECONDS);
        Long defaultPttl = defaultRedisTemplate.getExpire(LEASE_DEFAULT_KEY, TimeUnit.MILLISECONDS);
        Boolean lockExistsAfterRenew = redisRouteTemplate.executeOn(
                "lock", template -> template.hasKey(LEASE_DEFAULT_KEY));
        log.info("default 租约续租后，renewed={}，defaultPttl={}，lockExists={}",
                renewed, defaultPttl, lockExistsAfterRenew);
        assertTrue(renewed, "default datasource 中当前 owner 应能续租");
        assertNotNull(defaultPttl, "default datasource 续租后 PTTL 不应为 null");
        assertTrue(defaultPttl > 1000L, "default datasource 续租后的 PTTL 应体现新的租约时长");
        assertFalse(Boolean.TRUE.equals(lockExistsAfterRenew), "续租不得错误写入 lock datasource");

        boolean released = lease.release();
        boolean defaultExistsAfterRelease = Boolean.TRUE.equals(defaultRedisTemplate.hasKey(LEASE_DEFAULT_KEY));
        Boolean lockExistsAfterRelease = redisRouteTemplate.executeOn(
                "lock", template -> template.hasKey(LEASE_DEFAULT_KEY));
        log.info("default 租约释放后，released={}，defaultExists={}，lockExists={}",
                released, defaultExistsAfterRelease, lockExistsAfterRelease);
        assertTrue(released, "default datasource 中当前 owner 应能释放租约");
        assertFalse(defaultExistsAfterRelease, "释放后 default datasource 中 key 应删除");
        assertFalse(Boolean.TRUE.equals(lockExistsAfterRelease), "释放不得影响 lock datasource");
    }

    @Test
    public void testExpiredLeaseCannotChangeSuccessorInLockDatasource() throws Exception {
        log.info("验证 lock datasource 中过期租约无法续租或释放新持有者，lockKey={}", LEASE_STALE_KEY);
        Optional<RedisLockLease> first = simpleRedisLock.tryLockWithLease(
                LEASE_STALE_KEY, 250, TimeUnit.MILLISECONDS);
        log.info("第一个 lock datasource 租约获取结果={}", first.isPresent());
        assertTrue(first.isPresent(), "第一个 lock datasource 租约应成功获取");
        Thread.sleep(500L);
        Optional<RedisLockLease> successor = simpleRedisLock.tryLockWithLease(
                LEASE_STALE_KEY, 1, TimeUnit.SECONDS);
        log.info("第一个租约到期后，新租约获取结果={}", successor.isPresent());
        assertTrue(successor.isPresent(), "第一个租约到期后新持有者应能获取 lock datasource 锁");

        boolean renewed = first.get().renew(1, TimeUnit.SECONDS);
        boolean released = first.get().release();
        Boolean lockExists = redisRouteTemplate.executeOn("lock", template -> template.hasKey(LEASE_STALE_KEY));
        Long lockPttl = redisRouteTemplate.executeOn(
                "lock", template -> template.getExpire(LEASE_STALE_KEY, TimeUnit.MILLISECONDS));
        boolean defaultExists = Boolean.TRUE.equals(defaultRedisTemplate.hasKey(LEASE_STALE_KEY));
        log.info("旧租约操作后，renewed={}，released={}，lockExists={}，lockPttl={}，defaultExists={}",
                renewed, released, lockExists, lockPttl, defaultExists);
        assertFalse(renewed, "过期 owner 不得续租 lock datasource 中新持有者的锁");
        assertFalse(released, "过期 owner 不得释放 lock datasource 中新持有者的锁");
        assertTrue(Boolean.TRUE.equals(lockExists), "旧 owner 操作后 lock datasource 中新持有者的 key 必须保留");
        assertNotNull(lockPttl, "旧 owner 操作后新持有者的 PTTL 必须保留");
        assertTrue(lockPttl > 0L, "旧 owner 操作后新持有者的 PTTL 必须有效");
        assertFalse(defaultExists, "旧 owner 操作不得回退并写入 default datasource");
        successor.get().close();
    }
}
