package io.github.surezzzzzz.sdk.redis.route.factory;

import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.redis.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.redis.route.exception.RouteException;
import io.github.surezzzzzz.sdk.redis.route.support.RedisRouteStringHelper;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Redis Cluster 拓扑地址 nodes 跟随解析器工厂
 *
 * @author surezzzzzz
 */
final class NodesTopologySocketAddressResolverFactory {

    private static final String MAPPING_SOCKET_ADDRESS_RESOLVER_CLASS =
            "io.lettuce.core.resource.MappingSocketAddressResolver";
    private static final String DNS_RESOLVER_CLASS = "io.lettuce.core.resource.DnsResolver";
    private static final String HOST_AND_PORT_CLASS = "io.lettuce.core.internal.HostAndPort";
    private static final Pattern HOSTNAME_LABEL =
            Pattern.compile("^[a-z0-9](?:[-a-z0-9]*[a-z0-9])?$");

    private NodesTopologySocketAddressResolverFactory() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 从 nodes 创建拓扑地址解析器。
     *
     * @param nodes Redis Cluster 初始节点
     * @return Lettuce 地址解析器
     */
    static Object create(List<String> nodes) {
        return create(nodes, null);
    }

    static Object create(List<String> nodes, Object dnsResolver) {
        try {
            AddressMapper mapper = new AddressMapper(createServiceTails(nodes));
            Class<?> resolverClass = Class.forName(MAPPING_SOCKET_ADDRESS_RESOLVER_CLASS);
            Class<?> dnsResolverClass = Class.forName(DNS_RESOLVER_CLASS);
            Function<Object, Object> mappingFunction = mapper::map;
            if (dnsResolver != null) {
                Method createMethod = resolverClass.getMethod("create", dnsResolverClass, Function.class);
                return createMethod.invoke(null, dnsResolver, mappingFunction);
            }
            try {
                Method createMethod = resolverClass.getMethod("create", Function.class);
                return createMethod.invoke(null, mappingFunction);
            } catch (NoSuchMethodException ignored) {
                Method unresolvedResolverMethod = dnsResolverClass.getMethod("unresolved");
                Method createMethod = resolverClass.getMethod("create", dnsResolverClass, Function.class);
                return createMethod.invoke(null, unresolvedResolverMethod.invoke(null), mappingFunction);
            }
        } catch (ConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_006,
                    ErrorMessage.TOPOLOGY_ADDRESS_RESOLVER_UNAVAILABLE, e);
        }
    }

    private static Map<String, String> createServiceTails(List<String> nodes) {
        Map<String, String> serviceTails = new HashMap<>();
        for (String node : nodes) {
            String normalizedNode = node.trim();
            String host = normalizedNode.substring(0, normalizedNode.lastIndexOf(':'));
            String[] labels = host.split("\\.", -1);
            if (!isSupportedNodesHostname(labels)) {
                continue;
            }
            String service = labels[1];
            String tail = tail(labels);
            String previous = serviceTails.putIfAbsent(service, tail);
            if (previous != null && !previous.equals(tail)) {
                throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                        ErrorMessage.TOPOLOGY_ADDRESS_MAPPING_AMBIGUOUS);
            }
        }
        if (serviceTails.isEmpty()) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                    ErrorMessage.TOPOLOGY_ADDRESS_MAPPING_MISSING);
        }
        return serviceTails;
    }

    private static boolean isSupportedNodesHostname(String[] labels) {
        if (labels.length != 3 && labels.length != 6) {
            return false;
        }
        for (String label : labels) {
            if (!HOSTNAME_LABEL.matcher(label).matches()) {
                return false;
            }
        }
        return labels.length == 3 || ("svc".equals(labels[3])
                && "cluster".equals(labels[4]) && "local".equals(labels[5]));
    }

    private static String tail(String[] labels) {
        StringBuilder builder = new StringBuilder();
        for (int i = 2; i < labels.length; i++) {
            builder.append('.').append(labels[i]);
        }
        return builder.toString();
    }

    private static final class AddressMapper {

        private final Map<String, String> serviceTails;

        private AddressMapper(Map<String, String> serviceTails) {
            this.serviceTails = serviceTails;
        }

        private Object map(Object source) {
            try {
                Method hostMethod = source.getClass().getMethod("getHostText");
                Method portMethod = source.getClass().getMethod("getPort");
                String host = (String) hostMethod.invoke(source);
                int port = ((Number) portMethod.invoke(source)).intValue();
                String tail = topologyTail(host);
                if (tail == null) {
                    return source;
                }
                Class<?> hostAndPortClass = Class.forName(HOST_AND_PORT_CLASS);
                Method ofMethod = hostAndPortClass.getMethod("of", String.class, int.class);
                return ofMethod.invoke(null, host + tail, port);
            } catch (RouteException e) {
                throw e;
            } catch (Exception e) {
                throw new RouteException(ErrorCode.REDIS_ROUTE_014,
                        ErrorMessage.TOPOLOGY_ADDRESS_MAPPING_FAILED, e);
            }
        }

        private String topologyTail(String host) {
            if (!RedisRouteStringHelper.hasText(host) || host.indexOf(':') >= 0) {
                return null;
            }
            String[] labels = host.split("\\.", -1);
            if (labels.length != 2) {
                return null;
            }
            if (!HOSTNAME_LABEL.matcher(labels[0]).matches()
                    || !HOSTNAME_LABEL.matcher(labels[1]).matches()) {
                return null;
            }
            return serviceTails.get(labels[1]);
        }
    }
}
