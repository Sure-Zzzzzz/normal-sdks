package io.github.surezzzzzz.sdk.redis.route.test.factory;

import io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteProperties;
import io.github.surezzzzzz.sdk.redis.route.factory.DefaultRedisConnectionFactoryFactory;
import io.lettuce.core.resource.DnsResolver;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 拓扑端到端测试专用 DNS 连接工厂
 *
 * @author surezzzzzz
 */
public class TopologyDnsRedisConnectionFactoryFactory extends DefaultRedisConnectionFactoryFactory {

    private static final String TOPOLOGY_HOST_SUFFIX = ".redis-topology-headless.route-test.svc.cluster.local";

    @Override
    public RedisConnectionFactory create(String datasourceKey, SimpleRedisRouteProperties.DataSourceConfig config) {
        if (!config.isClusterTopologyAddressFollowNodes()) {
            return super.create(datasourceKey, config);
        }
        DnsResolver dnsResolver = host -> {
            if (!host.matches("redis-node-[0-5]" + TOPOLOGY_HOST_SUFFIX.replace(".", "\\."))) {
                throw new UnknownHostException(host);
            }
            return new InetAddress[]{InetAddress.getByAddress(host, new byte[]{127, 0, 0, 1})};
        };
        try {
            Method method = DefaultRedisConnectionFactoryFactory.class.getDeclaredMethod("create",
                    String.class, SimpleRedisRouteProperties.DataSourceConfig.class, Object.class);
            method.setAccessible(true);
            return (RedisConnectionFactory) method.invoke(this, datasourceKey, config, dnsResolver);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new AssertionError("创建 topology 测试连接工厂失败", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("创建 topology 测试连接工厂失败", e);
        }
    }
}
