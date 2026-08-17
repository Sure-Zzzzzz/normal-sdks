package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteProperties;
import io.github.surezzzzzz.sdk.redis.route.factory.RedisConnectionFactoryFactory;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import io.github.surezzzzzz.sdk.redis.route.test.SimpleRedisRouteTestApplication;
import io.github.surezzzzzz.sdk.redis.route.test.factory.TopologyDnsRedisConnectionFactoryFactory;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis Cluster 短 hostname 拓扑端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
@ActiveProfiles("redis-route-topology-e2e")
@SpringBootTest(classes = {SimpleRedisRouteTestApplication.class,
        RedisRouteClusterTopologyAddressEndToEndTest.TopologyDnsTestConfiguration.class})
public class RedisRouteClusterTopologyAddressEndToEndTest {

    private static final String DATASOURCE_KEY = "topologyCluster";
    private static final String SEED_HOST = "redis-node-0.redis-topology-headless.route-test.svc.cluster.local";
    private static final String SHORT_HOST = "redis-node-2.redis-topology-headless";
    private static final String FULL_HOST = SHORT_HOST + ".route-test.svc.cluster.local";
    private static final int SEED_PORT = 17120;
    private static final int DYNAMIC_NODE_PORT = 17122;

    @Autowired
    private RedisRouteTemplate template;

    @Autowired
    private SimpleRedisRouteRegistry registry;

    @Autowired
    private SimpleRedisRouteProperties properties;

    @AfterEach
    public void cleanUp() {
        if (isLettuce52()) {
            return;
        }
        String key = keyForSecondPrimary();
        template.executeOn(DATASOURCE_KEY, redisTemplate -> {
            redisTemplate.delete(key);
            return null;
        });
    }

    @Test
    public void testShortHostnameTopologyUsesNodesAddressSystemAcrossPeriodicRefresh() throws Exception {
        assertShortHostnameBoundary();
        assertNodesFollowConfiguration();
        if (isLettuce52()) {
            assertLettuce52Redis7TopologyBoundary();
            return;
        }

        String key = keyForSecondPrimary();
        template.executeOn(DATASOURCE_KEY, redisTemplate -> {
            redisTemplate.opsForValue().set(key, "before-refresh");
            return null;
        });
        assertEquals("before-refresh", template.executeOn(DATASOURCE_KEY,
                redisTemplate -> redisTemplate.opsForValue().get(key)));

        Thread.sleep(1500L);

        template.executeOn(DATASOURCE_KEY, redisTemplate -> {
            redisTemplate.opsForValue().set(key, "after-refresh");
            return null;
        });
        assertEquals("after-refresh", template.executeOn(DATASOURCE_KEY,
                redisTemplate -> redisTemplate.opsForValue().get(key)));
    }

    private void assertShortHostnameBoundary() throws Exception {
        assertThrows(UnknownHostException.class, () -> InetAddress.getByName(SHORT_HOST));
        assertThrows(UnknownHostException.class, () -> InetAddress.getByName(FULL_HOST));
    }

    private void assertNodesFollowConfiguration() throws Exception {
        SimpleRedisRouteProperties.DataSourceConfig config = properties.getSources().get(DATASOURCE_KEY);
        assertNotNull(config);
        assertTrue(config.isClusterTopologyAddressFollowNodes());
        assertEquals(1, config.getNodes().size());
        assertEquals(SEED_HOST + ":" + SEED_PORT, config.getNodes().get(0));
        assertTrue(registry.getConnectionFactory(DATASOURCE_KEY).getClass().getSimpleName()
                .contains("NodesTopologyLettuceConnectionFactory"));
        Object connectionFactory = registry.getConnectionFactory(DATASOURCE_KEY);
        Method clientConfigurationMethod = connectionFactory.getClass().getMethod("getClientConfiguration");
        clientConfigurationMethod.setAccessible(true);
        Object clientConfiguration = clientConfigurationMethod.invoke(connectionFactory);
        Method clientResourcesMethod = clientConfiguration.getClass().getMethod("getClientResources");
        clientResourcesMethod.setAccessible(true);
        Object clientResources = ((Optional<?>) clientResourcesMethod.invoke(clientConfiguration)).orElse(null);
        assertNotNull(clientResources);
        Method resolverMethod = clientResources.getClass().getMethod("socketAddressResolver");
        resolverMethod.setAccessible(true);
        Object resolver = resolverMethod.invoke(clientResources);
        Method resolveMethod = resolver.getClass().getMethod("resolve", RedisURI.class);
        resolveMethod.setAccessible(true);
        SocketAddress address = (SocketAddress) resolveMethod.invoke(resolver,
                RedisURI.Builder.redis(SHORT_HOST, DYNAMIC_NODE_PORT).build());
        assertEquals("127.0.0.1", ((InetSocketAddress) address).getAddress().getHostAddress());
        assertEquals(DYNAMIC_NODE_PORT, ((InetSocketAddress) address).getPort());
    }

    private boolean isLettuce52() {
        return RedisClient.class.getPackage().getImplementationVersion().startsWith("5.2.");
    }

    private void assertLettuce52Redis7TopologyBoundary() {
        RedisConnectionFailureException exception = assertThrows(RedisConnectionFailureException.class,
                () -> template.executeOn(DATASOURCE_KEY, redisTemplate -> {
                    redisTemplate.opsForValue().get("topology-e2e-boundary");
                    return null;
                }));
        assertTrue(hasCause(exception, UnsupportedOperationException.class));
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private String keyForSecondPrimary() {
        for (int index = 0; index < 100000; index++) {
            String tag = "topology-e2e-" + index;
            int slot = slot(tag);
            if (slot >= 5461 && slot <= 10922) {
                return "{" + tag + "}:value";
            }
        }
        throw new AssertionError("无法生成第二个 primary 的 Redis Cluster key");
    }

    private int slot(String value) {
        int crc = 0;
        for (byte current : value.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            crc ^= (current & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) == 0 ? crc << 1 : (crc << 1) ^ 0x1021;
                crc &= 0xFFFF;
            }
        }
        return crc & 0x3FFF;
    }

    @TestConfiguration
    static class TopologyDnsTestConfiguration {

        @Bean
        RedisConnectionFactoryFactory redisConnectionFactoryFactory() {
            return new TopologyDnsRedisConnectionFactoryFactory();
        }
    }
}
