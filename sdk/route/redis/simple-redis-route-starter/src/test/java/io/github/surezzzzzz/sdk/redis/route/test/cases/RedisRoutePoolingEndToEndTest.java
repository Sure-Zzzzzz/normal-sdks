package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import io.github.surezzzzzz.sdk.redis.route.test.SimpleRedisRouteTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis route Lettuce 连接池端到端测试
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = SimpleRedisRouteTestApplication.class)
public class RedisRoutePoolingEndToEndTest {

    @Autowired
    private RedisRouteTemplate template;

    @Autowired
    private SimpleRedisRouteRegistry registry;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @AfterEach
    public void cleanUp() {
        template.executeOn("default", redisTemplate -> redisTemplate.delete("pool:default:001"));
        template.executeOn("cache", redisTemplate -> redisTemplate.delete("cache:pool:001"));
    }

    @Test
    public void testPooledStandaloneDefaultSourceAndRouteDatasourceReadWrite() {
        stringRedisTemplate.opsForValue().set("pool:default:001", "default-value");
        String cacheValue = template.execute("cache:pool:001", redisTemplate -> {
            redisTemplate.opsForValue().set("cache:pool:001", "cache-value");
            return redisTemplate.opsForValue().get("cache:pool:001");
        });

        assertSame(registry.getConnectionFactory(), redisConnectionFactory);
        assertSame(registry.getStringRedisTemplate(), stringRedisTemplate);
        assertEquals("default-value", template.stringTemplate().opsForValue().get("pool:default:001"));
        assertEquals("cache-value", cacheValue);
        assertEquals("cache-value", template.stringTemplate("cache").opsForValue().get("cache:pool:001"));
        assertNull(template.stringTemplate().opsForValue().get("cache:pool:001"));
    }

    @Test
    public void testPooledStandaloneFactoriesExposeConfiguredCapacity() {
        assertPoolConfiguration("default", 12, 8, 2, 1000L);
        assertPoolConfiguration("cache", 10, 6, 1, 500L);
    }

    private void assertPoolConfiguration(String datasourceKey,
                                         int maxActive,
                                         int maxIdle,
                                         int minIdle,
                                         long maxWaitMs) {
        LettuceConnectionFactory connectionFactory = (LettuceConnectionFactory)
                registry.getConnectionFactory(datasourceKey);
        assertTrue(connectionFactory.getClientConfiguration()
                instanceof LettucePoolingClientConfiguration);
        LettucePoolingClientConfiguration clientConfiguration =
                (LettucePoolingClientConfiguration) connectionFactory.getClientConfiguration();
        assertEquals(maxActive, clientConfiguration.getPoolConfig().getMaxTotal());
        assertEquals(maxIdle, clientConfiguration.getPoolConfig().getMaxIdle());
        assertEquals(minIdle, clientConfiguration.getPoolConfig().getMinIdle());
        assertEquals(maxWaitMs, clientConfiguration.getPoolConfig().getMaxWaitMillis());
    }
}
