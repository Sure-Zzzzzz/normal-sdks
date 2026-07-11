package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteConfiguration;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis route 自动配置测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SimpleRedisRouteAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SimpleRedisRouteConfiguration.class)
            .withPropertyValues(
                    "io.github.surezzzzzz.sdk.redis.route.enable=true",
                    "io.github.surezzzzzz.sdk.redis.route.default-source=default",
                    "io.github.surezzzzzz.sdk.redis.route.sources.default.host=localhost",
                    "io.github.surezzzzzz.sdk.redis.route.sources.default.port=6379",
                    "io.github.surezzzzzz.sdk.redis.route.sources.default.database=0",
                    "io.github.surezzzzzz.sdk.redis.route.sources.default.timeout-ms=3000",
                    "io.github.surezzzzzz.sdk.redis.route.sources.default.connect-timeout-ms=3000",
                    "io.github.surezzzzzz.sdk.redis.route.sources.cache.host=localhost",
                    "io.github.surezzzzzz.sdk.redis.route.sources.cache.port=6379",
                    "io.github.surezzzzzz.sdk.redis.route.sources.cache.database=1",
                    "io.github.surezzzzzz.sdk.redis.route.sources.cache.timeout-ms=3000",
                    "io.github.surezzzzzz.sdk.redis.route.sources.cache.connect-timeout-ms=3000",
                    "io.github.surezzzzzz.sdk.redis.route.rules[0].pattern=cache:",
                    "io.github.surezzzzzz.sdk.redis.route.rules[0].type=prefix",
                    "io.github.surezzzzzz.sdk.redis.route.rules[0].datasource=cache",
                    "io.github.surezzzzzz.sdk.redis.route.rules[0].priority=1"
            );

    @Test
    public void testRouteBeansCreatedWithRealRedis() {
        contextRunner.run(context -> {
            assertTrue(context.containsBean("simpleRedisRouteRegistry"));
            assertTrue(context.containsBean("redisRouteTemplate"));
            assertTrue(context.containsBean("redisRoutePatternMatcher"));
            SimpleRedisRouteRegistry registry = context.getBean(SimpleRedisRouteRegistry.class);
            assertTrue(registry.containsDatasource("default"));
            assertTrue(registry.containsDatasource("cache"));
            assertFalse(registry.containsDatasource("lock"));
            RedisRouteTemplate template = context.getBean(RedisRouteTemplate.class);
            template.execute("cache:auto:001", redisTemplate -> {
                redisTemplate.opsForValue().set("cache:auto:001", "auto-value");
                return null;
            });
            assertSame(template.stringTemplate("cache"), template.stringTemplateByKey("cache:auto:001"));
            assertEquals("auto-value", template.stringTemplate("cache").opsForValue().get("cache:auto:001"));
            assertNull(template.stringTemplate().opsForValue().get("cache:auto:001"));
            template.stringTemplate("cache").delete("cache:auto:001");
        });
    }

    @Test
    public void testDoesNotCreateGlobalRedisBeans() {
        contextRunner.run(context -> {
            assertEquals(0, context.getBeansOfType(RedisConnectionFactory.class).size());
            assertEquals(0, context.getBeansOfType(RedisTemplate.class).size());
            assertEquals(0, context.getBeansOfType(StringRedisTemplate.class).size());
        });
    }
}
