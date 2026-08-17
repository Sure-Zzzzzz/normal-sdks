package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteProperties;
import io.github.surezzzzzz.sdk.redis.route.constant.RedisSourceMode;
import io.github.surezzzzzz.sdk.redis.route.factory.DefaultRedisConnectionFactoryFactory;
import io.lettuce.core.RedisURI;
import io.lettuce.core.resource.ClientResources;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 默认 Redis 连接工厂配置测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultRedisConnectionFactoryFactoryTest {

    private static final String NODES_TOPOLOGY_FACTORY_CLASS =
            "io.github.surezzzzzz.sdk.redis.route.factory.NodesTopologyLettuceConnectionFactory";
    private static final String POOLED_CLUSTER_FACTORY_CLASS =
            "io.github.surezzzzzz.sdk.redis.route.factory.PooledClusterLettuceConnectionFactory";

    @Test
    public void testClusterClientOptionsEnableTopologyRefresh() {
        SimpleRedisRouteProperties.DataSourceConfig config = new SimpleRedisRouteProperties.DataSourceConfig();
        config.setMode(RedisSourceMode.CLUSTER.getCode());
        config.setNodes(Arrays.asList("localhost:7000", "localhost:7001", "localhost:7002"));
        config.getLettuce().setClusterRefreshPeriodMs(45000L);

        LettuceClientConfiguration clientConfiguration = createClientConfiguration(config, RedisSourceMode.CLUSTER);
        Object clientOptions = clientConfiguration.getClientOptions().orElse(null);
        assertNotNull(clientOptions);
        Object topologyRefreshOptions = invoke(clientOptions, "getTopologyRefreshOptions");
        log.info("cluster clientOptions={}，refreshPeriod={}", clientOptions.getClass().getName(),
                invoke(topologyRefreshOptions, "getRefreshPeriod"));
        assertEquals("io.lettuce.core.cluster.ClusterClientOptions", clientOptions.getClass().getName());
        assertEquals(Boolean.TRUE, invoke(clientOptions, "isAutoReconnect"));
        assertEquals(10000, invoke(clientOptions, "getRequestQueueSize"));
        assertEquals("REJECT_COMMANDS", String.valueOf(invoke(clientOptions, "getDisconnectedBehavior")));
        assertNotNull(topologyRefreshOptions);
        assertEquals(Boolean.TRUE, invoke(topologyRefreshOptions, "isPeriodicRefreshEnabled"));
        assertEquals(Duration.ofMillis(45000L), invoke(topologyRefreshOptions, "getRefreshPeriod"));
    }

    @Test
    public void testClusterNodesTopologyAddsSocketAddressResolver() throws Exception {
        LettuceConnectionFactory connectionFactory = createClusterFactoryWithNodesTopology();
        try {
            ClientResources clientResources = connectionFactory.getClientConfiguration().getClientResources().orElse(null);
            assertNotNull(clientResources);
            Object socketAddressResolver = invoke(clientResources, "socketAddressResolver");
            Method resolve = socketAddressResolver.getClass().getMethod("resolve", RedisURI.class);
            SocketAddress address = (SocketAddress) resolve.invoke(socketAddressResolver,
                    RedisURI.Builder.redis("redis-cluster-4.redis-cluster-headless", 6379).build());
            InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
            log.info("解析后的主机名为 {}，端口为 {}", inetSocketAddress.getHostString(), inetSocketAddress.getPort());
            assertEquals("redis-cluster-4.redis-cluster-headless.route-test", inetSocketAddress.getHostString());
            assertEquals(6379, inetSocketAddress.getPort());
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    public void testClusterNodesTopologyUsesResourceOwningFactory() {
        LettuceConnectionFactory connectionFactory = createClusterFactoryWithNodesTopology();
        try {
            log.info("nodes 跟随 datasource 使用的连接工厂为 {}", connectionFactory.getClass().getName());
            assertEquals(NODES_TOPOLOGY_FACTORY_CLASS, connectionFactory.getClass().getName());
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    public void testResourceOwningFactoryShutsDownResourcesOnce() throws Exception {
        ClientResources clientResources = spy(ClientResources.create());
        LettuceConnectionFactory connectionFactory =
                (LettuceConnectionFactory) createResourceOwningFactory(clientResources);
        connectionFactory.afterPropertiesSet();
        Method destroy = connectionFactory.getClass().getMethod("destroy");
        destroy.setAccessible(true);

        destroy.invoke(connectionFactory);
        destroy.invoke(connectionFactory);

        log.info("nodes 跟随 ClientResources 已执行重复关闭验证");
        verify(clientResources).shutdown();
    }

    @Test
    public void testStandaloneDoesNotCreateNodesTopologyResourceOwningFactory() {
        SimpleRedisRouteProperties.DataSourceConfig config = new SimpleRedisRouteProperties.DataSourceConfig();
        RedisConnectionFactory connectionFactory = new DefaultRedisConnectionFactoryFactory().create("standalone", config);
        try {
            log.info("standalone 使用的连接工厂为 {}", connectionFactory.getClass().getName());
            assertFalse(NODES_TOPOLOGY_FACTORY_CLASS.equals(connectionFactory.getClass().getName()));
        } finally {
            ((LettuceConnectionFactory) connectionFactory).destroy();
        }
    }

    @Test
    public void testDisabledClusterNodesTopologyDoesNotCreateResourceOwningFactory() {
        SimpleRedisRouteProperties.DataSourceConfig config = new SimpleRedisRouteProperties.DataSourceConfig();
        config.setMode(RedisSourceMode.CLUSTER.getCode());
        config.setNodes(Arrays.asList("localhost:7000", "localhost:7001", "localhost:7002"));
        config.setClusterTopologyAddressFollowNodes(false);
        LettuceConnectionFactory connectionFactory = (LettuceConnectionFactory) new DefaultRedisConnectionFactoryFactory()
                .create("cluster", config);
        try {
            assertFalse(NODES_TOPOLOGY_FACTORY_CLASS.equals(connectionFactory.getClass().getName()));
            assertFalse(POOLED_CLUSTER_FACTORY_CLASS.equals(connectionFactory.getClass().getName()));
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    public void testPooledClusterUsesPoolShutdownCompatibleFactory() {
        SimpleRedisRouteProperties.DataSourceConfig config = new SimpleRedisRouteProperties.DataSourceConfig();
        config.setMode(RedisSourceMode.CLUSTER.getCode());
        config.setNodes(Arrays.asList("localhost:7000", "localhost:7001", "localhost:7002"));
        configurePool(config, 8, 8, 0, -1L);
        LettuceConnectionFactory connectionFactory = (LettuceConnectionFactory) new DefaultRedisConnectionFactoryFactory()
                .create("cluster", config);
        try {
            assertEquals(POOLED_CLUSTER_FACTORY_CLASS, connectionFactory.getClass().getName());
            assertTrue(connectionFactory.getClientConfiguration() instanceof LettucePoolingClientConfiguration);
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    public void testPooledClusterNodesTopologyKeepsResourceOwningFactory() {
        SimpleRedisRouteProperties.DataSourceConfig config = new SimpleRedisRouteProperties.DataSourceConfig();
        config.setMode(RedisSourceMode.CLUSTER.getCode());
        config.setNodes(Arrays.asList("redis-cluster-0.redis-cluster-headless.route-test:6379"));
        config.setClusterTopologyAddressFollowNodes(true);
        configurePool(config, 8, 8, 0, -1L);
        LettuceConnectionFactory connectionFactory = (LettuceConnectionFactory) new DefaultRedisConnectionFactoryFactory()
                .create("cluster", config);
        try {
            assertEquals(NODES_TOPOLOGY_FACTORY_CLASS, connectionFactory.getClass().getName());
            assertTrue(connectionFactory.getClientConfiguration() instanceof LettucePoolingClientConfiguration);
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    public void testStandaloneClientOptionsRejectDisconnectedCommands() {
        SimpleRedisRouteProperties.DataSourceConfig config = new SimpleRedisRouteProperties.DataSourceConfig();
        config.getLettuce().setAutoReconnect(false);
        config.getLettuce().setRejectCommandsWhenDisconnected(false);

        LettuceClientConfiguration clientConfiguration = createClientConfiguration(config, RedisSourceMode.STANDALONE);
        Object clientOptions = clientConfiguration.getClientOptions().orElse(null);
        assertNotNull(clientOptions);
        log.info("standalone clientOptions={}，断连行为={}", clientOptions.getClass().getName(),
                invoke(clientOptions, "getDisconnectedBehavior"));
        assertEquals("io.lettuce.core.ClientOptions", clientOptions.getClass().getName());
        assertEquals(Boolean.FALSE, invoke(clientOptions, "isAutoReconnect"));
        assertEquals(10000, invoke(clientOptions, "getRequestQueueSize"));
        assertEquals("DEFAULT", String.valueOf(invoke(clientOptions, "getDisconnectedBehavior")));
    }

    @Test
    public void testDisabledPoolKeepsNonPooledClientConfiguration() {
        SimpleRedisRouteProperties.DataSourceConfig config = new SimpleRedisRouteProperties.DataSourceConfig();

        LettuceClientConfiguration clientConfiguration = createClientConfiguration(config, RedisSourceMode.STANDALONE);

        assertFalse(clientConfiguration instanceof LettucePoolingClientConfiguration);
    }

    @Test
    public void testPooledStandaloneClientConfigurationUsesConfiguredCapacity() {
        SimpleRedisRouteProperties.DataSourceConfig config = new SimpleRedisRouteProperties.DataSourceConfig();
        configurePool(config, 32, 16, 4, 1000L);

        LettuceClientConfiguration clientConfiguration = createClientConfiguration(config, RedisSourceMode.STANDALONE);

        assertTrue(clientConfiguration instanceof LettucePoolingClientConfiguration);
        LettucePoolingClientConfiguration pooledConfiguration =
                (LettucePoolingClientConfiguration) clientConfiguration;
        assertEquals(32, pooledConfiguration.getPoolConfig().getMaxTotal());
        assertEquals(16, pooledConfiguration.getPoolConfig().getMaxIdle());
        assertEquals(4, pooledConfiguration.getPoolConfig().getMinIdle());
        assertEquals(1000L, pooledConfiguration.getPoolConfig().getMaxWaitMillis());
        assertEquals(Duration.ofMillis(config.getTimeoutMs()), pooledConfiguration.getCommandTimeout());
        assertEquals(Duration.ofMillis(config.getLettuce().getShutdownTimeoutMs()),
                pooledConfiguration.getShutdownTimeout());
    }

    @Test
    public void testPooledClusterClientConfigurationKeepsTopologyRefresh() {
        SimpleRedisRouteProperties.DataSourceConfig config = new SimpleRedisRouteProperties.DataSourceConfig();
        config.setMode(RedisSourceMode.CLUSTER.getCode());
        config.setNodes(Arrays.asList("localhost:7000", "localhost:7001", "localhost:7002"));
        config.getLettuce().setClusterRefreshPeriodMs(45000L);
        configurePool(config, 24, 12, 3, -1L);

        LettuceClientConfiguration clientConfiguration = createClientConfiguration(config, RedisSourceMode.CLUSTER);

        assertTrue(clientConfiguration instanceof LettucePoolingClientConfiguration);
        LettucePoolingClientConfiguration pooledConfiguration =
                (LettucePoolingClientConfiguration) clientConfiguration;
        assertEquals(24, pooledConfiguration.getPoolConfig().getMaxTotal());
        assertEquals(12, pooledConfiguration.getPoolConfig().getMaxIdle());
        assertEquals(3, pooledConfiguration.getPoolConfig().getMinIdle());
        assertEquals(-1L, pooledConfiguration.getPoolConfig().getMaxWaitMillis());
        Object clientOptions = pooledConfiguration.getClientOptions().orElse(null);
        assertNotNull(clientOptions);
        Object topologyRefreshOptions = invoke(clientOptions, "getTopologyRefreshOptions");
        assertNotNull(topologyRefreshOptions);
        assertEquals(Boolean.TRUE, invoke(topologyRefreshOptions, "isPeriodicRefreshEnabled"));
        assertEquals(Duration.ofMillis(45000L), invoke(topologyRefreshOptions, "getRefreshPeriod"));
    }

    @Test
    public void testLettuceProductionSafetyDefaults() {
        SimpleRedisRouteProperties.DataSourceConfig config = new SimpleRedisRouteProperties.DataSourceConfig();
        log.info("Lettuce 默认配置：自动重连={}，拒绝断连命令={}，队列上限={}",
                config.getLettuce().isAutoReconnect(), config.getLettuce().isRejectCommandsWhenDisconnected(),
                config.getLettuce().getRequestQueueSize());
        assertTrue(config.getLettuce().isAutoReconnect());
        assertTrue(config.getLettuce().isRejectCommandsWhenDisconnected());
        assertEquals(10000, config.getLettuce().getRequestQueueSize());
        assertFalse(config.isClusterTopologyAddressFollowNodes());
        assertTrue(config.getLettuce().isClusterAdaptiveRefresh());
        assertTrue(config.getLettuce().isClusterPeriodicRefresh());
        assertEquals(60000L, config.getLettuce().getClusterRefreshPeriodMs());
        assertFalse(config.getLettuce().getPool().isEnabled());
        assertEquals(8, config.getLettuce().getPool().getMaxActive());
        assertEquals(8, config.getLettuce().getPool().getMaxIdle());
        assertEquals(0, config.getLettuce().getPool().getMinIdle());
        assertEquals(-1L, config.getLettuce().getPool().getMaxWaitMs());
    }

    private void configurePool(SimpleRedisRouteProperties.DataSourceConfig config,
                               int maxActive,
                               int maxIdle,
                               int minIdle,
                               long maxWaitMs) {
        SimpleRedisRouteProperties.PoolConfig pool = config.getLettuce().getPool();
        pool.setEnabled(true);
        pool.setMaxActive(maxActive);
        pool.setMaxIdle(maxIdle);
        pool.setMinIdle(minIdle);
        pool.setMaxWaitMs(maxWaitMs);
    }

    private LettuceConnectionFactory createClusterFactoryWithNodesTopology() {
        SimpleRedisRouteProperties.DataSourceConfig config = new SimpleRedisRouteProperties.DataSourceConfig();
        config.setMode(RedisSourceMode.CLUSTER.getCode());
        config.setNodes(Arrays.asList("redis-cluster-0.redis-cluster-headless.route-test:6379"));
        config.setClusterTopologyAddressFollowNodes(true);
        return (LettuceConnectionFactory) new DefaultRedisConnectionFactoryFactory().create("cluster", config);
    }

    private Object createResourceOwningFactory(ClientResources clientResources) throws Exception {
        Class<?> factoryClass = Class.forName(NODES_TOPOLOGY_FACTORY_CLASS);
        Constructor<?> constructor = factoryClass.getDeclaredConstructor(RedisClusterConfiguration.class,
                LettuceClientConfiguration.class, ClientResources.class);
        constructor.setAccessible(true);
        RedisClusterConfiguration clusterConfiguration = new RedisClusterConfiguration(Arrays.asList("localhost:7000"));
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = LettuceClientConfiguration.builder();
        Method clientResourcesMethod = ReflectionUtils.findMethod(builder.getClass(), "clientResources", ClientResources.class);
        assertNotNull(clientResourcesMethod);
        ReflectionUtils.makeAccessible(clientResourcesMethod);
        ReflectionUtils.invokeMethod(clientResourcesMethod, builder, clientResources);
        Method buildMethod = ReflectionUtils.findMethod(builder.getClass(), "build");
        assertNotNull(buildMethod);
        LettuceClientConfiguration clientConfiguration = (LettuceClientConfiguration) ReflectionUtils.invokeMethod(buildMethod, builder);
        return constructor.newInstance(clusterConfiguration, clientConfiguration, clientResources);
    }

    private LettuceClientConfiguration createClientConfiguration(SimpleRedisRouteProperties.DataSourceConfig config,
                                                                 RedisSourceMode mode) {
        DefaultRedisConnectionFactoryFactory factory = new DefaultRedisConnectionFactoryFactory();
        Method method = ReflectionUtils.findMethod(DefaultRedisConnectionFactoryFactory.class,
                "createClientConfiguration", SimpleRedisRouteProperties.DataSourceConfig.class, RedisSourceMode.class);
        assertNotNull(method);
        ReflectionUtils.makeAccessible(method);
        return (LettuceClientConfiguration) ReflectionUtils.invokeMethod(method, factory, config, mode);
    }

    private Object invoke(Object target, String methodName) {
        Method method = ReflectionUtils.findMethod(target.getClass(), methodName);
        assertNotNull(method);
        ReflectionUtils.makeAccessible(method);
        return ReflectionUtils.invokeMethod(method, target);
    }
}
