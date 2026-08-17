package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.exception.ConfigurationException;
import io.lettuce.core.RedisURI;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Redis Cluster 拓扑地址 nodes 跟随映射测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class NodesTopologySocketAddressResolverFactoryTest {

    private static final String RESOLVER_FACTORY_CLASS =
            "io.github.surezzzzzz.sdk.redis.route.factory.NodesTopologySocketAddressResolverFactory";

    private final Object resolver = createResolver(Arrays.asList(
            "redis-cluster-0.redis-cluster-headless.route-test:6379",
            "redis-cluster-1.redis-cluster-headless.route-test:6379"));

    @Test
    public void testMapsTwoLabelClusterNodeAddressAndPreservesTopologyPort() throws Exception {
        InetSocketAddress address = resolve(resolver, "redis-cluster-4.redis-cluster-headless", 16379);

        log.info("解析后的主机名为 {}，端口为 {}", address.getHostString(), address.getPort());
        assertEquals("redis-cluster-4.redis-cluster-headless.route-test", address.getHostString());
        assertEquals(16379, address.getPort());
    }

    @Test
    public void testFullHostnameSeedMapsToItsOwnTail() throws Exception {
        Object fqdnResolver = createResolver(Collections.singletonList(
                "redis-cluster-0.redis-cluster-headless.route-test.svc.cluster.local:6379"));

        InetSocketAddress address = resolve(fqdnResolver, "redis-cluster-4.redis-cluster-headless", 6379);

        assertEquals("redis-cluster-4.redis-cluster-headless.route-test.svc.cluster.local", address.getHostString());
        assertEquals(6379, address.getPort());
    }

    @Test
    public void testKeepsMappingsIndependentForDifferentServices() throws Exception {
        Object multiServiceResolver = createResolver(Arrays.asList(
                "redis-cluster-0.redis-cluster-headless.route-test:6379",
                "redis-exporter-0.redis-exporter-headless.ops.svc.cluster.local:6379"));

        assertEquals("redis-cluster-4.redis-cluster-headless.route-test",
                resolve(multiServiceResolver, "redis-cluster-4.redis-cluster-headless", 6379).getHostString());
        assertEquals("redis-exporter-4.redis-exporter-headless.ops.svc.cluster.local",
                resolve(multiServiceResolver, "redis-exporter-4.redis-exporter-headless", 6379).getHostString());
    }

    @Test
    public void testDoesNotMapQualifiedOrUnsupportedTopologyAddress() throws Exception {
        assertHostUnchanged("redis-cluster-4.redis-cluster-headless.route-test");
        assertHostUnchanged("redis-cluster-4.redis-cluster-headless.route-test.svc.cluster.local");
        assertHostUnchanged("redis-cluster-4.redis-cluster-headless.");
        assertHostUnchanged("redis-cluster-4.unknown-headless");
        assertHostUnchanged("localhost");
        assertHostUnchanged("cache");
        assertHostUnchanged("10.244.157.196");
        assertHostUnchanged("2001:db8::1");
        assertHostUnchanged("Redis-Cluster.redis-cluster-headless");
        assertHostUnchanged("redis_cluster.redis-cluster-headless");
    }

    @Test
    public void testRejectsAmbiguousOrUnsupportedNodesMapping() {
        assertThrows(ConfigurationException.class, () -> createResolver(Arrays.asList(
                "redis-cluster-0.redis-cluster-headless.route-test:6379",
                "redis-cluster-1.redis-cluster-headless.other:6379")));
        assertThrows(ConfigurationException.class, () -> createResolver(Collections.singletonList(
                "redis-cluster-0.redis-cluster-headless.route-test.svc.example:6379")));
    }

    private void assertHostUnchanged(String host) throws Exception {
        InetSocketAddress address = resolve(resolver, host, 6379);
        log.info("未改写主机名 {}，端口为 {}", address.getHostString(), address.getPort());
        assertEquals(host, address.getHostString());
        assertEquals(6379, address.getPort());
    }

    private Object createResolver(List<String> nodes) {
        try {
            Class<?> factoryClass = Class.forName(RESOLVER_FACTORY_CLASS);
            Method method = factoryClass.getDeclaredMethod("create", List.class);
            method.setAccessible(true);
            return method.invoke(null, nodes);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new AssertionError("创建 nodes 地址解析器失败", e.getCause());
        } catch (Exception e) {
            throw new AssertionError("创建 nodes 地址解析器失败", e);
        }
    }

    private InetSocketAddress resolve(Object targetResolver, String host, int port) throws Exception {
        Method method = targetResolver.getClass().getMethod("resolve", RedisURI.class);
        SocketAddress address = (SocketAddress) method.invoke(targetResolver,
                RedisURI.Builder.redis(host, port).build());
        return (InetSocketAddress) address;
    }
}
