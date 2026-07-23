package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.fixture.SmartRedisLimiterAutoConfigurationTestFixture.UserExecutorConfiguration;
import io.github.surezzzzzz.sdk.limiter.redis.fixture.SmartRedisLimiterAutoConfigurationTestFixture.UserRedisExecutor;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterAutoConfiguration;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterConfigurationException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.executor.SmartRedisLimiterRedisExecutor;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.SmartRedisLimiterPolicyRefreshManager;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.SmartRedisLimiterPolicyResolver;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.SmartRedisLimiterPolicySnapshotStore;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.client.SmartRedisLimiterPolicyClient;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.json.SmartRedisLimiterPolicyJsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SmartRedisLimiter 自动配置契约测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SmartRedisLimiterAutoConfiguration.class))
            .withPropertyValues(
                    "io.github.surezzzzzz.sdk.limiter.redis.smart.enable=true",
                    "io.github.surezzzzzz.sdk.limiter.redis.smart.me=test");

    @Test
    public void testMissingRedisRouteTemplateBeanFailsStartup() {
        contextRunner.run(context -> {
            Throwable startupFailure = context.getStartupFailure();
            log.info("RedisRouteTemplate 缺失时启动异常: {}", startupFailure == null ? null : startupFailure.getMessage());
            assertTrue(startupFailure != null, "RedisRouteTemplate Bean 缺失时必须启动失败");
            SmartRedisLimiterConfigurationException exception = findCause(
                    startupFailure, SmartRedisLimiterConfigurationException.class);
            assertTrue(exception != null, "启动失败原因应包含 SmartRedisLimiterConfigurationException");
            assertEquals(ErrorCode.CONFIG_REDIS_ROUTE_TEMPLATE_MISSING, exception.getErrorCode(),
                    "route 依赖缺失错误码应精确匹配");
            assertTrue(exception.getMessage().contains("redis.route.enable"),
                    "错误消息应提示开启 redis.route.enable，实际: " + exception.getMessage());
        });
    }

    @Test
    public void testRouteAutoConfigurationExcludedFailsStartupBeforeExecutor() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SmartRedisLimiterAutoConfiguration.class))
                .withPropertyValues(
                        "io.github.surezzzzzz.sdk.limiter.redis.smart.enable=true",
                        "io.github.surezzzzzz.sdk.limiter.redis.smart.me=test")
                .run(context -> {
                    Throwable startupFailure = context.getStartupFailure();
                    assertTrue(startupFailure != null,
                            "排除 route 自动配置后 smart limiter 必须启动失败，不能静默降级");
                    SmartRedisLimiterConfigurationException exception = findCause(
                            startupFailure, SmartRedisLimiterConfigurationException.class);
                    assertTrue(exception != null,
                            "启动失败必须由 SmartRedisLimiter 显式抛出，而不是 Spring 原生 NoSuchBeanDefinitionException");
                    assertEquals(ErrorCode.CONFIG_REDIS_ROUTE_TEMPLATE_MISSING, exception.getErrorCode(),
                            "route 自动配置缺失错误码应精确匹配");
                    log.info("route 自动配置缺失启动失败: {}", exception.getMessage());
                });
    }

    @Test
    public void testUserRedisExecutorOverridesDefaultAndPropertiesIsSingleBean() {
        contextRunner.withUserConfiguration(UserExecutorConfiguration.class).run(context -> {
            assertTrue(context.getStartupFailure() == null,
                    "提供 RedisRouteTemplate 和用户执行器后上下文应正常启动");
            assertEquals(1, context.getBeansOfType(SmartRedisLimiterRedisExecutor.class).size(),
                    "用户执行器存在时默认执行器必须让位");
            assertSame(context.getBean(UserRedisExecutor.class),
                    context.getBean(SmartRedisLimiterRedisExecutor.class),
                    "实际注入的应为用户执行器");
            assertEquals(1, context.getBeansOfType(SmartRedisLimiterProperties.class).size(),
                    "SmartRedisLimiterProperties 必须只注册一个 Bean");
            log.info("用户执行器覆盖成功，Properties Bean 数量={}",
                    context.getBeansOfType(SmartRedisLimiterProperties.class).size());
        });
    }

    @Test
    public void testRemotePolicyDisabledCreatesNoRemoteBeans() {
        contextRunner.withUserConfiguration(UserExecutorConfiguration.class).run(context -> {
            log.info("远程策略关闭时 Bean 数量: client={}, codec={}, store={}, resolver={}, manager={}",
                    context.getBeansOfType(SmartRedisLimiterPolicyClient.class).size(),
                    context.getBeansOfType(SmartRedisLimiterPolicyJsonCodec.class).size(),
                    context.getBeansOfType(SmartRedisLimiterPolicySnapshotStore.class).size(),
                    context.getBeansOfType(SmartRedisLimiterPolicyResolver.class).size(),
                    context.getBeansOfType(SmartRedisLimiterPolicyRefreshManager.class).size());
            assertEquals(0, context.getBeansOfType(SmartRedisLimiterPolicyClient.class).size(),
                    "remote disabled 时不得创建 HTTP Client");
            assertEquals(0, context.getBeansOfType(SmartRedisLimiterPolicyJsonCodec.class).size(),
                    "remote disabled 时不得创建 JSON Codec");
            assertEquals(0, context.getBeansOfType(SmartRedisLimiterPolicySnapshotStore.class).size(),
                    "remote disabled 时不得创建 Snapshot Store");
            assertEquals(0, context.getBeansOfType(SmartRedisLimiterPolicyResolver.class).size(),
                    "remote disabled 时不得创建 Policy Resolver");
            assertEquals(0, context.getBeansOfType(SmartRedisLimiterPolicyRefreshManager.class).size(),
                    "remote disabled 时不得创建刷新线程管理器");
        });
    }

    @Test
    public void testRemotePolicyEnabledCreatesSingleRemoteBeanSet() {
        contextRunner
                .withUserConfiguration(UserExecutorConfiguration.class)
                .withPropertyValues(
                        "io.github.surezzzzzz.sdk.limiter.redis.smart.remote-policy.enable=true",
                        "io.github.surezzzzzz.sdk.limiter.redis.smart.remote-policy.snapshot-url=http://management.internal/api/v1/policy/snapshot")
                .run(context -> {
                    assertTrue(context.getStartupFailure() == null, "合法 remote 配置应正常启动");
                    assertEquals(1, context.getBeansOfType(SmartRedisLimiterPolicyClient.class).size(),
                            "PolicyClient 应只有一个 Bean");
                    assertEquals(1, context.getBeansOfType(SmartRedisLimiterPolicyJsonCodec.class).size(),
                            "PolicyJsonCodec 应只有一个 Bean");
                    assertEquals(1, context.getBeansOfType(SmartRedisLimiterPolicySnapshotStore.class).size(),
                            "SnapshotStore 应只有一个 Bean");
                    assertEquals(1, context.getBeansOfType(SmartRedisLimiterPolicyResolver.class).size(),
                            "PolicyResolver 应只有一个 Bean");
                    assertEquals(1, context.getBeansOfType(SmartRedisLimiterPolicyRefreshManager.class).size(),
                            "RefreshManager 应只有一个 Bean");
                });
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

}
