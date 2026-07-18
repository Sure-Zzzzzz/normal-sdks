package io.github.surezzzzzz.sdk.lock.redis.test.cases;

import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.lock.redis.configuration.SimpleRedisLockConfiguration;
import io.github.surezzzzzz.sdk.lock.redis.configuration.SimpleRedisLockRouteConfiguration;
import io.github.surezzzzzz.sdk.lock.redis.configuration.SimpleRedisLockRouteMissingConfiguration;
import io.github.surezzzzzz.sdk.lock.redis.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.lock.redis.constant.SimpleRedisLockConstant;
import io.github.surezzzzzz.sdk.lock.redis.executor.DefaultRedisLockExecutor;
import io.github.surezzzzzz.sdk.lock.redis.executor.RedisLockExecutor;
import io.github.surezzzzzz.sdk.lock.redis.executor.RouteRedisLockExecutor;
import io.github.surezzzzzz.sdk.lock.redis.model.RedisLockLease;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis 分布式锁自动配置边界测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class SimpleRedisLockAutoConfigurationTest {

    private static final String ROUTE_ENABLE_PROPERTY = SimpleRedisLockConstant.ROUTE_CONFIG_PREFIX + "."
            + SimpleRedisLockConstant.PROPERTY_ENABLE + "=" + SimpleRedisLockConstant.PROPERTY_VALUE_TRUE;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SimpleRedisLockConfiguration.class,
                    SimpleRedisLockRouteConfiguration.class,
                    SimpleRedisLockRouteMissingConfiguration.class
            ));

    @Test
    public void testRouteDisabledUsesDefaultExecutor() {
        contextRunner.withUserConfiguration(DefaultRedisConfiguration.class)
                .run(context -> {
                    log.info("验证默认模式自动配置 Bean 列表: {}", (Object) context.getBeanDefinitionNames());
                    assertTrue(context.containsBean(SimpleRedisLockConstant.SIMPLE_REDIS_LOCK_REDIS_TEMPLATE_BEAN_NAME), "默认模式必须注册 simpleRedisLockRedisTemplate");
                    assertTrue(context.getBean(RedisLockExecutor.class) instanceof DefaultRedisLockExecutor,
                            "默认模式必须注册 DefaultRedisLockExecutor");
                    assertNotNull(context.getBean(SimpleRedisLock.class), "默认模式必须注册 SimpleRedisLock");
                });
    }

    @Test
    public void testRouteEnabledUsesRouteExecutor() {
        contextRunner.withUserConfiguration(RouteTemplateConfiguration.class)
                .withPropertyValues(ROUTE_ENABLE_PROPERTY)
                .run(context -> {
                    log.info("验证 route 模式自动配置 Bean 列表: {}", (Object) context.getBeanDefinitionNames());
                    assertFalse(context.containsBean(SimpleRedisLockConstant.SIMPLE_REDIS_LOCK_REDIS_TEMPLATE_BEAN_NAME), "route 模式不应注册 simpleRedisLockRedisTemplate");
                    assertTrue(context.getBean(RedisLockExecutor.class) instanceof RouteRedisLockExecutor,
                            "route 模式必须注册 RouteRedisLockExecutor");
                    assertNotNull(context.getBean(SimpleRedisLock.class), "route 模式必须注册 SimpleRedisLock");
                });
    }

    @Test
    public void testRouteEnabledWithoutRedisRouteTemplateFailsFast() {
        contextRunner.withPropertyValues(ROUTE_ENABLE_PROPERTY)
                .run(context -> {
                    log.info("验证 route 模式缺少 RedisRouteTemplate 时启动失败，failure={}", context.getStartupFailure());
                    assertNotNull(context.getStartupFailure(), "route.enable=true 但 RedisRouteTemplate 不存在时必须启动失败");
                    assertTrue(String.valueOf(context.getStartupFailure().getMessage()).contains("RedisRouteTemplate"),
                            "失败信息必须明确提示缺少 RedisRouteTemplate");
                });
    }

    @Test
    public void testCustomRedisLockExecutorBacksOffDefaults() {
        contextRunner.withUserConfiguration(DefaultRedisConfiguration.class, CustomExecutorConfiguration.class)
                .run(context -> {
                    log.info("验证业务自定义 RedisLockExecutor 时默认 executor 退让");
                    RedisLockExecutor executor = context.getBean(RedisLockExecutor.class);
                    assertSame(CustomExecutorConfiguration.CUSTOM_EXECUTOR, executor, "业务自定义 RedisLockExecutor 必须生效");
                    assertTrue(context.containsBean(SimpleRedisLockConstant.SIMPLE_REDIS_LOCK_REDIS_TEMPLATE_BEAN_NAME), "默认模式下仍可注册 simpleRedisLockRedisTemplate");
                    assertNotNull(context.getBean(SimpleRedisLock.class), "自定义 executor 模式必须注册 SimpleRedisLock");
                });
    }

    @Test
    public void testLegacyCustomExecutorRejectsLeaseRenewExplicitly() {
        contextRunner.withUserConfiguration(DefaultRedisConfiguration.class, CustomExecutorConfiguration.class)
                .run(context -> {
                    SimpleRedisLock simpleRedisLock = context.getBean(SimpleRedisLock.class);
                    Optional<RedisLockLease> optionalLease = simpleRedisLock.tryLockWithLease(
                            "custom-executor-lease", 1, TimeUnit.SECONDS);
                    log.info("验证旧 custom executor 可获取 lease 但明确拒绝续租，leasePresent={}", optionalLease.isPresent());
                    assertTrue(optionalLease.isPresent(), "旧 custom executor 的固定租约能力应继续可用");
                    UnsupportedOperationException renewException = null;
                    try {
                        optionalLease.get().renew(1, TimeUnit.SECONDS);
                    } catch (UnsupportedOperationException e) {
                        renewException = e;
                    }
                    CustomExecutorConfiguration.UNLOCK_CALL_COUNT.set(0);
                    boolean firstRelease = optionalLease.get().release();
                    optionalLease.get().close();
                    int unlockCallCount = CustomExecutorConfiguration.UNLOCK_CALL_COUNT.get();
                    log.info("验证旧 custom executor 的 lease 续租异常消息={}，释放结果={}，unlock 调用次数={}",
                            renewException == null ? null : renewException.getMessage(), firstRelease, unlockCallCount);
                    assertNotNull(renewException, "未实现 renew 的旧 custom executor 应明确提示不支持续租");
                    assertEquals(ErrorMessage.EXECUTOR_UNSUPPORTED_LEASE_RENEW, renewException.getMessage(),
                            "旧 custom executor 未实现 renew 时必须返回约定异常消息");
                    assertTrue(firstRelease, "旧 custom executor 的 lease 应继续复用 unlock 释放");
                    assertEquals(1, unlockCallCount, "release 与 close 只能触发一次 owner-CAS 解锁");
                });
    }

    @Test
    public void testConcurrentRenewAndReleaseAreSerialized() throws Exception {
        contextRunner.withUserConfiguration(DefaultRedisConfiguration.class, BlockingRenewExecutorConfiguration.class)
                .run(context -> {
                    SimpleRedisLock simpleRedisLock = context.getBean(SimpleRedisLock.class);
                    BlockingRenewExecutor executor = context.getBean(BlockingRenewExecutor.class);
                    Optional<RedisLockLease> optionalLease = simpleRedisLock.tryLockWithLease(
                            "blocking-renew-lease", 1, TimeUnit.SECONDS);
                    log.info("验证 renew/release 并发串行化，leasePresent={}", optionalLease.isPresent());
                    assertTrue(optionalLease.isPresent(), "阻塞续租执行器下首次获取租约应成功");

                    RedisLockLease lease = optionalLease.get();
                    AtomicReference<Boolean> renewResult = new AtomicReference<>();
                    AtomicReference<Boolean> releaseResult = new AtomicReference<>();
                    AtomicReference<Throwable> backgroundFailure = new AtomicReference<>();
                    CountDownLatch releaseAttemptedLatch = new CountDownLatch(1);
                    Thread renewThread = new Thread(() -> executeInBackground(
                            () -> renewResult.set(lease.renew(1, TimeUnit.SECONDS)), backgroundFailure),
                            "redis-lock-renew-test");
                    Thread releaseThread = new Thread(() -> executeInBackground(() -> {
                        releaseAttemptedLatch.countDown();
                        releaseResult.set(lease.release());
                    }, backgroundFailure), "redis-lock-release-test");
                    try {
                        renewThread.start();
                        boolean renewEntered = executor.awaitRenewEntered();
                        log.info("阻塞续租是否已进入 executor={}", renewEntered);
                        assertTrue(renewEntered, "renew 必须先进入阻塞执行器");

                        releaseThread.start();
                        boolean releaseAttempted = releaseAttemptedLatch.await(5L, TimeUnit.SECONDS);
                        boolean releaseBlocked = awaitThreadBlocked(releaseThread);
                        int unlockCountBeforeRenewReturns = executor.getUnlockCallCount();
                        log.info("renew 阻塞期间，releaseAttempted={}，releaseBlocked={}，unlockCount={}",
                                releaseAttempted, releaseBlocked, unlockCountBeforeRenewReturns);
                        assertTrue(releaseAttempted, "release 线程必须实际发起释放调用");
                        assertTrue(releaseBlocked, "renew 持有租约监视器时 release 必须阻塞等待");
                        assertEquals(0, unlockCountBeforeRenewReturns, "renew 尚未返回时 release 不得进入 unlock");

                        executor.allowRenewReturn();
                        renewThread.join(5_000L);
                        releaseThread.join(5_000L);
                        boolean renewAfterRelease = lease.renew(1, TimeUnit.SECONDS);
                        int renewCallCountAfterRelease = executor.getRenewCallCount();
                        log.info("并发调用结束，renewAlive={}，releaseAlive={}，renewResult={}，releaseResult={}，renewAfterRelease={}，renewCount={}，unlockCount={}，backgroundFailure={}",
                                renewThread.isAlive(), releaseThread.isAlive(), renewResult.get(), releaseResult.get(),
                                renewAfterRelease, renewCallCountAfterRelease, executor.getUnlockCallCount(),
                                backgroundFailure.get());
                        assertFalse(renewThread.isAlive(), "renew 线程必须在超时内结束");
                        assertFalse(releaseThread.isAlive(), "release 线程必须在超时内结束");
                        assertNull(backgroundFailure.get(), "并发调用不应抛出后台异常");
                        assertTrue(Boolean.TRUE.equals(renewResult.get()), "release 未执行前 renew 应成功");
                        assertTrue(Boolean.TRUE.equals(releaseResult.get()), "release 应在 renew 完成后成功执行");
                        assertFalse(renewAfterRelease, "release 成功后 renew 必须直接返回 false");
                        assertEquals(1, renewCallCountAfterRelease, "release 后 renew 不得再次调用 executor");
                        assertEquals(1, executor.getUnlockCallCount(), "并发 release 只能触发一次 owner-CAS 解锁");
                    } finally {
                        executor.allowRenewReturn();
                        renewThread.join(5_000L);
                        releaseThread.join(5_000L);
                    }
                });
    }

    @Test
    public void testRouteEnabledWithCustomExecutorDoesNotFailWithoutRouteTemplate() {
        contextRunner.withUserConfiguration(CustomExecutorConfiguration.class)
                .withPropertyValues(ROUTE_ENABLE_PROPERTY)
                .run(context -> {
                    log.info("验证 route.enable=true 且业务自定义 executor 时不强行失败");
                    assertNull(context.getStartupFailure(), "业务自定义 RedisLockExecutor 时 SDK 不应强行失败");
                    assertSame(CustomExecutorConfiguration.CUSTOM_EXECUTOR, context.getBean(RedisLockExecutor.class),
                            "业务自定义 RedisLockExecutor 必须优先生效");
                    assertFalse(context.containsBean(SimpleRedisLockConstant.SIMPLE_REDIS_LOCK_REDIS_TEMPLATE_BEAN_NAME), "route.enable=true 时不应注册 simpleRedisLockRedisTemplate");
                });
    }

    @Configuration
    static class DefaultRedisConfiguration {
        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return new LettuceConnectionFactory("localhost", 6379);
        }
    }

    @Configuration
    static class RouteTemplateConfiguration {
        @Bean
        RedisRouteTemplate redisRouteTemplate() {
            return new RedisRouteTemplate(null, null);
        }
    }

    @Configuration
    static class CustomExecutorConfiguration {
        static final AtomicInteger UNLOCK_CALL_COUNT = new AtomicInteger();
        static final RedisLockExecutor CUSTOM_EXECUTOR = new RedisLockExecutor() {
            @Override
            public boolean tryLock(String lockKey, String lockValue, long expireTime, TimeUnit timeUnit) {
                return true;
            }

            @Override
            public boolean unlock(String lockKey, String lockValue) {
                UNLOCK_CALL_COUNT.incrementAndGet();
                return true;
            }
        };

        @Bean
        RedisLockExecutor customRedisLockExecutor() {
            return CUSTOM_EXECUTOR;
        }
    }

    @Configuration
    static class BlockingRenewExecutorConfiguration {
        @Bean
        BlockingRenewExecutor blockingRenewExecutor() {
            return new BlockingRenewExecutor();
        }
    }

    static class BlockingRenewExecutor implements RedisLockExecutor {

        private final CountDownLatch renewEnteredLatch = new CountDownLatch(1);
        private final CountDownLatch renewReturnLatch = new CountDownLatch(1);
        private final AtomicInteger renewCallCount = new AtomicInteger();
        private final AtomicInteger unlockCallCount = new AtomicInteger();

        @Override
        public boolean tryLock(String lockKey, String lockValue, long expireTime, TimeUnit timeUnit) {
            return true;
        }

        @Override
        public boolean unlock(String lockKey, String lockValue) {
            unlockCallCount.incrementAndGet();
            return true;
        }

        @Override
        public boolean renew(String lockKey, String lockValue, long leaseTime, TimeUnit timeUnit) {
            renewCallCount.incrementAndGet();
            // 通知外部：renew 已进入 executor，当前持有 Lease 的 synchronized 监视器
            renewEnteredLatch.countDown();
            try {
                // 阻塞等待外部允许返回，模拟 renew 执行耗时
                renewReturnLatch.await(10L, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return true;
        }

        boolean awaitRenewEntered() throws InterruptedException {
            return renewEnteredLatch.await(5L, TimeUnit.SECONDS);
        }

        void allowRenewReturn() {
            renewReturnLatch.countDown();
        }

        int getRenewCallCount() {
            return renewCallCount.get();
        }

        int getUnlockCallCount() {
            return unlockCallCount.get();
        }
    }

    private static boolean awaitThreadBlocked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (System.nanoTime() < deadline) {
            if (thread.getState() == Thread.State.BLOCKED) {
                return true;
            }
            Thread.sleep(10L);
        }
        return false;
    }

    private static void executeInBackground(Runnable task, AtomicReference<Throwable> failureRef) {
        try {
            task.run();
        } catch (Throwable t) {
            failureRef.compareAndSet(null, t);
        }
    }
}
