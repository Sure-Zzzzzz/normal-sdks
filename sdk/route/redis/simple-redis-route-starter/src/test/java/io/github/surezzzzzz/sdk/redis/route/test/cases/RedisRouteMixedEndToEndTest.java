package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.exception.RouteException;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import io.github.surezzzzzz.sdk.redis.route.test.SimpleRedisRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis route 混合部署端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
@EnabledIfSystemProperty(named = "redis.route.cluster.test", matches = "true")
@SpringBootTest(classes = SimpleRedisRouteTestApplication.class, properties = {
        "io.github.surezzzzzz.sdk.redis.route.enable=true",
        "io.github.surezzzzzz.sdk.redis.route.default-source=primary",
        "io.github.surezzzzzz.sdk.redis.route.sources.primary.mode=cluster",
        "io.github.surezzzzzz.sdk.redis.route.sources.primary.nodes[0]=localhost:7000",
        "io.github.surezzzzzz.sdk.redis.route.sources.primary.nodes[1]=localhost:7001",
        "io.github.surezzzzzz.sdk.redis.route.sources.primary.nodes[2]=localhost:7002",
        "io.github.surezzzzzz.sdk.redis.route.sources.primary.nodes[3]=localhost:7003",
        "io.github.surezzzzzz.sdk.redis.route.sources.primary.nodes[4]=localhost:7004",
        "io.github.surezzzzzz.sdk.redis.route.sources.primary.nodes[5]=localhost:7005",
        "io.github.surezzzzzz.sdk.redis.route.sources.primary.max-redirects=5",
        "io.github.surezzzzzz.sdk.redis.route.sources.primary.timeout-ms=3000",
        "io.github.surezzzzzz.sdk.redis.route.sources.primary.connect-timeout-ms=3000",
        "io.github.surezzzzzz.sdk.redis.route.sources.primary.lettuce.cluster-adaptive-refresh=true",
        "io.github.surezzzzzz.sdk.redis.route.sources.primary.lettuce.cluster-periodic-refresh=true",
        "io.github.surezzzzzz.sdk.redis.route.sources.primary.lettuce.cluster-refresh-period-ms=30000",
        "io.github.surezzzzzz.sdk.redis.route.sources.secondary.mode=standalone",
        "io.github.surezzzzzz.sdk.redis.route.sources.secondary.host=localhost",
        "io.github.surezzzzzz.sdk.redis.route.sources.secondary.port=6379",
        "io.github.surezzzzzz.sdk.redis.route.sources.secondary.database=1",
        "io.github.surezzzzzz.sdk.redis.route.sources.secondary.timeout-ms=3000",
        "io.github.surezzzzzz.sdk.redis.route.sources.secondary.connect-timeout-ms=3000",
        "io.github.surezzzzzz.sdk.redis.route.rules[0].pattern=secondary:",
        "io.github.surezzzzzz.sdk.redis.route.rules[0].type=prefix",
        "io.github.surezzzzzz.sdk.redis.route.rules[0].datasource=secondary",
        "io.github.surezzzzzz.sdk.redis.route.rules[0].priority=1"
})
public class RedisRouteMixedEndToEndTest {

    @Autowired
    private RedisRouteTemplate template;

    @AfterEach
    public void cleanUp() {
        template.executeOn("primary", redisTemplate -> redisTemplate.delete("primary:{mixed}:001"));
        template.executeOn("secondary", redisTemplate -> redisTemplate.delete("secondary:mixed:001"));
    }

    @Test
    public void testPrimaryClusterAndSecondaryStandaloneRouteTogether() {
        StringRedisTemplate primaryTemplate = template.stringTemplate("primary");
        StringRedisTemplate secondaryTemplate = template.stringTemplate("secondary");

        String primaryValue = template.execute("primary:{mixed}:001", redisTemplate -> {
            assertSame(primaryTemplate, redisTemplate);
            redisTemplate.opsForValue().set("primary:{mixed}:001", "primary-cluster-value");
            return redisTemplate.opsForValue().get("primary:{mixed}:001");
        });
        String secondaryValue = template.execute("secondary:mixed:001", redisTemplate -> {
            assertSame(secondaryTemplate, redisTemplate);
            redisTemplate.opsForValue().set("secondary:mixed:001", "secondary-standalone-value");
            return redisTemplate.opsForValue().get("secondary:mixed:001");
        });

        assertEquals("primary-cluster-value", primaryValue);
        assertEquals("secondary-standalone-value", secondaryValue);
        assertSame(primaryTemplate, template.stringTemplateByKey("primary:{mixed}:001"));
        assertSame(secondaryTemplate, template.stringTemplateByKey("secondary:mixed:001"));
        assertNotSame(primaryTemplate, secondaryTemplate);
        assertTrue(template.connectionFactory("primary") instanceof LettuceConnectionFactory);
        assertTrue(template.connectionFactory("secondary") instanceof LettuceConnectionFactory);
        assertNotSame(template.connectionFactory("primary"), template.connectionFactory("secondary"));
        LettuceConnectionFactory primaryFactory = (LettuceConnectionFactory) template.connectionFactory("primary");
        LettuceConnectionFactory secondaryFactory = (LettuceConnectionFactory) template.connectionFactory("secondary");
        assertNull(primaryFactory.getStandaloneConfiguration());
        assertNotNull(primaryFactory.getClusterConfiguration());
        assertEquals(6, primaryFactory.getClusterConfiguration().getClusterNodes().size());
        assertEquals(Integer.valueOf(5), primaryFactory.getClusterConfiguration().getMaxRedirects());
        assertNotNull(secondaryFactory.getStandaloneConfiguration());
        assertNull(secondaryFactory.getClusterConfiguration());
        assertEquals("localhost", secondaryFactory.getStandaloneConfiguration().getHostName());
        assertEquals(6379, secondaryFactory.getStandaloneConfiguration().getPort());
        assertEquals(1, secondaryFactory.getStandaloneConfiguration().getDatabase());
        assertEquals("primary-cluster-value", primaryTemplate.opsForValue().get("primary:{mixed}:001"));
        assertEquals("secondary-standalone-value", secondaryTemplate.opsForValue().get("secondary:mixed:001"));
        assertNull(secondaryTemplate.opsForValue().get("primary:{mixed}:001"));
        assertNull(primaryTemplate.opsForValue().get("secondary:mixed:001"));
    }

    @Test
    public void testMixedMultiKeyCrossDatasourceThrows() {
        RouteException exception = assertThrows(RouteException.class,
                () -> template.execute(Arrays.asList("primary:{mixed}:001", "secondary:mixed:001"), redisTemplate -> redisTemplate));
        assertEquals(ErrorCode.REDIS_ROUTE_009, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("primary"));
        assertTrue(exception.getMessage().contains("secondary"));
        assertTrue(exception.getMessage().contains("primary:{mixed}:001"));
        assertTrue(exception.getMessage().contains("secondary:mixed:001"));
    }
}
