package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.exception.CacheConfigurationException;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smart Cache 配置与启动校验测试
 *
 * <p>验证关键配置组合的启动行为：
 * <ul>
 *   <li>strong + pubsub.disabled 必须启动失败，根因为 {@link CacheConfigurationException}</li>
 *   <li>L2 启用但缺少 {@code RedisRouteTemplate} 必须启动失败，根因为 {@link CacheConfigurationException}</li>
 * </ul>
 *
 * <p>启动失败时 Spring 会把 {@code afterPropertiesSet} 抛出的 {@link CacheConfigurationException}
 * 包装成 {@code BeanCreationException} / {@code UnsatisfiedDependencyException}，因此这里捕获
 * {@link Exception} 并沿 cause 链定位 {@link CacheConfigurationException}，避免对具体包装层数强依赖。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartCacheTestApplication.class)
public class SmartCacheConfigurationTest {

    @Test
    @DisplayName("强一致性 + pubsub.mode=disabled 时必须启动失败，根因为 CacheConfigurationException")
    void shouldFailStartupWhenStrongWithDisabledPubsub() {
        Exception ex = assertThrows(Exception.class, () -> {
            ConfigurableApplicationContext context = SpringApplication.run(
                    SmartCacheTestApplication.class,
                    "--spring.main.banner-mode=off",
                    "--spring.main.allow-bean-definition-overriding=true",
                    "--io.github.surezzzzzz.sdk.cache.enabled=true",
                    "--io.github.surezzzzzz.sdk.cache.me=test-config",
                    "--io.github.surezzzzzz.sdk.cache.consistency.mode=strong",
                    "--io.github.surezzzzzz.sdk.cache.pubsub.mode=disabled",
                    "--io.github.surezzzzzz.sdk.cache.l2.enabled=true",
                    "--io.github.surezzzzzz.sdk.cache.l1.enabled=true",
                    "--io.github.surezzzzzz.sdk.redis.route.enable=true",
                    "--io.github.surezzzzzz.sdk.redis.route.default-source=default",
                    "--io.github.surezzzzzz.sdk.redis.route.sources.default.mode=standalone",
                    "--io.github.surezzzzzz.sdk.redis.route.sources.default.host=localhost",
                    "--io.github.surezzzzzz.sdk.redis.route.sources.default.port=6379",
                    "--io.github.surezzzzzz.sdk.redis.route.sources.default.database=0",
                    "--spring.redis.host=localhost",
                    "--spring.redis.port=6379",
                    "--spring.redis.database=0"
            );
            context.close();
        }, "强一致性 + pubsub.disabled 必须抛出启动异常");

        CacheConfigurationException cacheEx = findCause(ex, CacheConfigurationException.class);
        assertNotNull(cacheEx,
                "根因应为 CacheConfigurationException，实际异常链：" + describeChain(ex));
        assertEquals("SMART_CACHE_001", cacheEx.getErrorCode(),
                "错误码应为配置错误 SMART_CACHE_001");
        assertTrue(cacheEx.getMessage().contains("强一致性模式不能关闭 Pub/Sub"),
                "错误消息应包含配置语义，实际：" + cacheEx.getMessage());
        log.info("验证通过：强一致性 + pubsub.disabled 正确抛出 CacheConfigurationException，错误码：{}，消息：{}",
                cacheEx.getErrorCode(), cacheEx.getMessage());
    }

    @Test
    @DisplayName("L2 关闭但强一致性缺少 RedisRouteTemplate 时必须启动失败，根因为 CacheConfigurationException")
    void shouldFailStartupWhenStrongConsistencyButRouteMissing() {
        Exception ex = assertThrows(Exception.class, () -> {
            ConfigurableApplicationContext context = SpringApplication.run(
                    SmartCacheTestApplication.class,
                    "--spring.main.banner-mode=off",
                    "--spring.main.allow-bean-definition-overriding=true",
                    "--io.github.surezzzzzz.sdk.cache.enabled=true",
                    "--io.github.surezzzzzz.sdk.cache.me=test-strong-route-missing",
                    "--io.github.surezzzzzz.sdk.cache.consistency.mode=strong",
                    "--io.github.surezzzzzz.sdk.cache.pubsub.mode=routed",
                    "--io.github.surezzzzzz.sdk.cache.l2.enabled=false",
                    "--io.github.surezzzzzz.sdk.cache.l1.enabled=true",
                    "--io.github.surezzzzzz.sdk.redis.route.enable=false",
                    "--io.github.surezzzzzz.sdk.lock.redis.route.enable=false",
                    "--spring.redis.host=localhost",
                    "--spring.redis.port=6379",
                    "--spring.redis.database=0"
            );
            context.close();
        }, "L2 关闭但强一致性缺少 RedisRouteTemplate 必须抛出启动异常");

        CacheConfigurationException cacheEx = findCause(ex, CacheConfigurationException.class);
        assertNotNull(cacheEx,
                "根因应为 CacheConfigurationException，实际异常链：" + describeChain(ex));
        assertEquals("SMART_CACHE_002", cacheEx.getErrorCode(),
                "错误码应为路由缺失 SMART_CACHE_002");
        assertTrue(cacheEx.getMessage().contains("强一致性模式必须提供 RedisRouteTemplate"),
                "错误消息应包含强一致性 Route 依赖，实际：" + cacheEx.getMessage());
        log.info("验证通过：L2 关闭但强一致性缺少 RedisRouteTemplate 正确启动失败，错误码：{}",
                cacheEx.getErrorCode());
    }

    @Test
    @DisplayName("L2 启用但缺少 RedisRouteTemplate 时必须启动失败，根因为 CacheConfigurationException")
    void shouldFailStartupWhenL2EnabledButRouteMissing() {
        Exception ex = assertThrows(Exception.class, () -> {
            ConfigurableApplicationContext context = SpringApplication.run(
                    SmartCacheTestApplication.class,
                    "--spring.main.banner-mode=off",
                    "--spring.main.allow-bean-definition-overriding=true",
                    "--io.github.surezzzzzz.sdk.cache.enabled=true",
                    "--io.github.surezzzzzz.sdk.cache.me=test-route-missing",
                    // eventual + disabled 是合法组合，确保唯一失败原因是缺少 RedisRouteTemplate
                    "--io.github.surezzzzzz.sdk.cache.consistency.mode=eventual",
                    "--io.github.surezzzzzz.sdk.cache.pubsub.mode=disabled",
                    "--io.github.surezzzzzz.sdk.cache.l2.enabled=true",
                    "--io.github.surezzzzzz.sdk.cache.l1.enabled=true",
                    // 显式关闭 Redis Route，不创建 RedisRouteTemplate Bean
                    "--io.github.surezzzzzz.sdk.redis.route.enable=false",
                    // 关闭 lock route，避免 simple-redis-lock-starter 的路由缺失守卫先于缓存守卫触发
                    "--io.github.surezzzzzz.sdk.lock.redis.route.enable=false",
                    "--spring.redis.host=localhost",
                    "--spring.redis.port=6379",
                    "--spring.redis.database=0"
            );
            context.close();
        }, "L2 启用但缺少 RedisRouteTemplate 必须抛出启动异常");

        CacheConfigurationException cacheEx = findCause(ex, CacheConfigurationException.class);
        assertNotNull(cacheEx,
                "根因应为 CacheConfigurationException，实际异常链：" + describeChain(ex));
        assertEquals("SMART_CACHE_002", cacheEx.getErrorCode(),
                "错误码应为路由缺失 SMART_CACHE_002");
        log.info("验证通过：L2 启用但缺少 RedisRouteTemplate 正确抛出 CacheConfigurationException，错误码：{}",
                cacheEx.getErrorCode());
    }

    private static <T extends Throwable> T findCause(Throwable t, Class<T> type) {
        Set<Throwable> seen = new HashSet<>();
        Throwable cur = t;
        while (cur != null && seen.add(cur)) {
            if (type.isInstance(cur)) {
                return type.cast(cur);
            }
            cur = cur.getCause();
        }
        return null;
    }

    private static String describeChain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Set<Throwable> seen = new HashSet<>();
        Throwable cur = t;
        while (cur != null && seen.add(cur)) {
            if (sb.length() > 0) {
                sb.append(" -> ");
            }
            sb.append(cur.getClass().getSimpleName())
                    .append("(").append(cur.getMessage()).append(")");
            cur = cur.getCause();
        }
        return sb.toString();
    }
}
