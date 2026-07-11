package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteProperties;
import io.github.surezzzzzz.sdk.redis.route.constant.RedisSourceMode;
import io.github.surezzzzzz.sdk.redis.route.factory.DefaultRedisConnectionFactoryFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 默认 Redis 连接工厂配置测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultRedisConnectionFactoryFactoryTest {

    @Test
    public void testClusterClientOptionsEnableTopologyRefresh() throws Exception {
        SimpleRedisRouteProperties.DataSourceConfig config = new SimpleRedisRouteProperties.DataSourceConfig();
        config.setMode(RedisSourceMode.CLUSTER.getCode());
        config.setNodes(Arrays.asList("localhost:7000", "localhost:7001", "localhost:7002"));
        config.getLettuce().setClusterRefreshPeriodMs(45000L);

        LettuceClientConfiguration clientConfiguration = createClientConfiguration(config, RedisSourceMode.CLUSTER);

        Object clientOptions = clientConfiguration.getClientOptions().orElse(null);
        assertNotNull(clientOptions);
        assertEquals("io.lettuce.core.cluster.ClusterClientOptions", clientOptions.getClass().getName());
        assertEquals(Boolean.TRUE, invoke(clientOptions, "isAutoReconnect"));
        assertEquals(10000, invoke(clientOptions, "getRequestQueueSize"));
        assertEquals("REJECT_COMMANDS", String.valueOf(invoke(clientOptions, "getDisconnectedBehavior")));
        Object topologyRefreshOptions = invoke(clientOptions, "getTopologyRefreshOptions");
        assertNotNull(topologyRefreshOptions);
        assertEquals(Boolean.TRUE, invoke(topologyRefreshOptions, "isPeriodicRefreshEnabled"));
        assertEquals(Duration.ofMillis(45000L), invoke(topologyRefreshOptions, "getRefreshPeriod"));
    }

    @Test
    public void testStandaloneClientOptionsRejectDisconnectedCommands() throws Exception {
        SimpleRedisRouteProperties.DataSourceConfig config = new SimpleRedisRouteProperties.DataSourceConfig();
        config.getLettuce().setAutoReconnect(false);
        config.getLettuce().setRejectCommandsWhenDisconnected(false);

        LettuceClientConfiguration clientConfiguration = createClientConfiguration(config, RedisSourceMode.STANDALONE);

        Object clientOptions = clientConfiguration.getClientOptions().orElse(null);
        assertNotNull(clientOptions);
        assertEquals("io.lettuce.core.ClientOptions", clientOptions.getClass().getName());
        assertEquals(Boolean.FALSE, invoke(clientOptions, "isAutoReconnect"));
        assertEquals(10000, invoke(clientOptions, "getRequestQueueSize"));
        assertEquals("DEFAULT", String.valueOf(invoke(clientOptions, "getDisconnectedBehavior")));
    }

    @Test
    public void testLettuceProductionSafetyDefaults() {
        SimpleRedisRouteProperties.DataSourceConfig config = new SimpleRedisRouteProperties.DataSourceConfig();
        assertTrue(config.getLettuce().isAutoReconnect());
        assertTrue(config.getLettuce().isRejectCommandsWhenDisconnected());
        assertEquals(10000, config.getLettuce().getRequestQueueSize());
        assertTrue(config.getLettuce().isClusterAdaptiveRefresh());
        assertTrue(config.getLettuce().isClusterPeriodicRefresh());
        assertEquals(60000L, config.getLettuce().getClusterRefreshPeriodMs());
    }

    private LettuceClientConfiguration createClientConfiguration(SimpleRedisRouteProperties.DataSourceConfig config,
                                                                 RedisSourceMode mode) throws Exception {
        DefaultRedisConnectionFactoryFactory factory = new DefaultRedisConnectionFactoryFactory();
        Method method = ReflectionUtils.findMethod(DefaultRedisConnectionFactoryFactory.class,
                "createClientConfiguration", SimpleRedisRouteProperties.DataSourceConfig.class, RedisSourceMode.class);
        assertNotNull(method);
        ReflectionUtils.makeAccessible(method);
        return (LettuceClientConfiguration) ReflectionUtils.invokeMethod(method, factory, config, mode);
    }

    private Object invoke(Object target, String methodName) throws Exception {
        Method method = ReflectionUtils.findMethod(target.getClass(), methodName);
        assertNotNull(method);
        ReflectionUtils.makeAccessible(method);
        return ReflectionUtils.invokeMethod(method, target);
    }
}
