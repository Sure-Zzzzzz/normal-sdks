package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import io.github.surezzzzzz.sdk.redis.route.test.SimpleRedisRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis route Cluster 端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
@EnabledIfSystemProperty(named = "redis.route.cluster.test", matches = "true")
@SpringBootTest(classes = SimpleRedisRouteTestApplication.class, properties = {
        "io.github.surezzzzzz.sdk.redis.route.enable=true",
        "io.github.surezzzzzz.sdk.redis.route.default-source=default",
        "io.github.surezzzzzz.sdk.redis.route.sources.default.mode=cluster",
        "io.github.surezzzzzz.sdk.redis.route.sources.default.nodes[0]=localhost:7000",
        "io.github.surezzzzzz.sdk.redis.route.sources.default.nodes[1]=localhost:7001",
        "io.github.surezzzzzz.sdk.redis.route.sources.default.nodes[2]=localhost:7002",
        "io.github.surezzzzzz.sdk.redis.route.sources.default.nodes[3]=localhost:7003",
        "io.github.surezzzzzz.sdk.redis.route.sources.default.nodes[4]=localhost:7004",
        "io.github.surezzzzzz.sdk.redis.route.sources.default.nodes[5]=localhost:7005",
        "io.github.surezzzzzz.sdk.redis.route.sources.default.max-redirects=5",
        "io.github.surezzzzzz.sdk.redis.route.sources.default.timeout-ms=3000",
        "io.github.surezzzzzz.sdk.redis.route.sources.default.connect-timeout-ms=3000",
        "io.github.surezzzzzz.sdk.redis.route.sources.default.lettuce.cluster-adaptive-refresh=true",
        "io.github.surezzzzzz.sdk.redis.route.sources.default.lettuce.cluster-periodic-refresh=true",
        "io.github.surezzzzzz.sdk.redis.route.sources.default.lettuce.cluster-refresh-period-ms=30000",
        "io.github.surezzzzzz.sdk.redis.route.sources.cache.mode=cluster",
        "io.github.surezzzzzz.sdk.redis.route.sources.cache.nodes[0]=localhost:7000",
        "io.github.surezzzzzz.sdk.redis.route.sources.cache.nodes[1]=localhost:7001",
        "io.github.surezzzzzz.sdk.redis.route.sources.cache.nodes[2]=localhost:7002",
        "io.github.surezzzzzz.sdk.redis.route.sources.cache.nodes[3]=localhost:7003",
        "io.github.surezzzzzz.sdk.redis.route.sources.cache.nodes[4]=localhost:7004",
        "io.github.surezzzzzz.sdk.redis.route.sources.cache.nodes[5]=localhost:7005",
        "io.github.surezzzzzz.sdk.redis.route.sources.cache.max-redirects=5",
        "io.github.surezzzzzz.sdk.redis.route.sources.cache.timeout-ms=3000",
        "io.github.surezzzzzz.sdk.redis.route.sources.cache.connect-timeout-ms=3000",
        "io.github.surezzzzzz.sdk.redis.route.sources.cache.lettuce.cluster-adaptive-refresh=true",
        "io.github.surezzzzzz.sdk.redis.route.sources.cache.lettuce.cluster-periodic-refresh=true",
        "io.github.surezzzzzz.sdk.redis.route.sources.cache.lettuce.cluster-refresh-period-ms=30000",
        "io.github.surezzzzzz.sdk.redis.route.rules[0].pattern=cache:",
        "io.github.surezzzzzz.sdk.redis.route.rules[0].type=prefix",
        "io.github.surezzzzzz.sdk.redis.route.rules[0].datasource=cache",
        "io.github.surezzzzzz.sdk.redis.route.rules[0].priority=1"
})
public class RedisRouteClusterEndToEndTest {

    @Autowired
    private RedisRouteTemplate template;

    @AfterEach
    public void cleanUp() {
        template.executeOn("default", redisTemplate -> redisTemplate.delete("route:{cluster-default}:001"));
        template.executeOn("cache", redisTemplate -> redisTemplate.delete("cache:{cluster-user}:001"));
        template.executeOn("cache", redisTemplate -> redisTemplate.delete("cache:{cluster-user}:002"));
    }

    @Test
    public void testClusterRouteReadWrite() {
        StringRedisTemplate cacheTemplate = template.stringTemplate("cache");
        StringRedisTemplate defaultTemplate = template.stringTemplate();

        String value = template.execute("cache:{cluster-user}:001", redisTemplate -> {
            assertSame(cacheTemplate, redisTemplate);
            redisTemplate.opsForValue().set("cache:{cluster-user}:001", "cluster-cache-value");
            return redisTemplate.opsForValue().get("cache:{cluster-user}:001");
        });

        assertEquals("cluster-cache-value", value);
        assertSame(cacheTemplate, template.stringTemplateByKey("cache:{cluster-user}:001"));
        assertNotSame(defaultTemplate, cacheTemplate);
        assertEquals("cluster-cache-value", cacheTemplate.opsForValue().get("cache:{cluster-user}:001"));
        assertEquals("cluster-cache-value", defaultTemplate.opsForValue().get("cache:{cluster-user}:001"));
    }

    @Test
    public void testClusterMultiKeySameDatasourceWithHashTag() {
        Boolean result = template.execute(Arrays.asList("cache:{cluster-user}:001", "cache:{cluster-user}:002"), redisTemplate -> {
            assertSame(template.stringTemplate("cache"), redisTemplate);
            Map<String, String> values = new LinkedHashMap<>();
            values.put("cache:{cluster-user}:001", "cluster-user-1");
            values.put("cache:{cluster-user}:002", "cluster-user-2");
            redisTemplate.opsForValue().multiSet(values);
            return "cluster-user-1".equals(redisTemplate.opsForValue().get("cache:{cluster-user}:001"))
                    && "cluster-user-2".equals(redisTemplate.opsForValue().get("cache:{cluster-user}:002"));
        });

        assertTrue(result);
        assertEquals("cluster-user-1", template.stringTemplate("cache").opsForValue().get("cache:{cluster-user}:001"));
        assertEquals("cluster-user-2", template.stringTemplate("cache").opsForValue().get("cache:{cluster-user}:002"));
    }

    @Test
    public void testClusterDoesNotGuaranteeSameSlotForMultiKeyCommand() {
        assertDoesNotThrow(() -> template.execute(Arrays.asList("cache:{slot-a}:001", "cache:{slot-b}:001"), redisTemplate -> {
            assertSame(template.stringTemplate("cache"), redisTemplate);
            return redisTemplate;
        }));

        assertThrows(Exception.class, () -> template.execute(Arrays.asList("cache:{slot-a}:001", "cache:{slot-b}:001"), redisTemplate -> {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("cache:{slot-a}:001", "slot-a");
            values.put("cache:{slot-b}:001", "slot-b");
            redisTemplate.opsForValue().multiSet(values);
            return null;
        }));
        template.stringTemplate("cache").delete(Collections.singleton("cache:{slot-a}:001"));
        template.stringTemplate("cache").delete(Collections.singleton("cache:{slot-b}:001"));
    }
}
