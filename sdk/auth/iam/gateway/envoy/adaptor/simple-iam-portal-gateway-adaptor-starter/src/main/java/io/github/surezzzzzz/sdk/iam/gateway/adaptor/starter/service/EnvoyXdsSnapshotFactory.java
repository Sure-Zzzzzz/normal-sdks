package io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.service;

import com.google.protobuf.Any;
import com.google.protobuf.Duration;
import io.envoyproxy.controlplane.cache.v3.Snapshot;
import io.envoyproxy.envoy.config.cluster.v3.Cluster;
import io.envoyproxy.envoy.config.core.v3.Address;
import io.envoyproxy.envoy.config.core.v3.AggregatedConfigSource;
import io.envoyproxy.envoy.config.core.v3.ConfigSource;
import io.envoyproxy.envoy.config.core.v3.SocketAddress;
import io.envoyproxy.envoy.config.endpoint.v3.ClusterLoadAssignment;
import io.envoyproxy.envoy.config.endpoint.v3.Endpoint;
import io.envoyproxy.envoy.config.endpoint.v3.LbEndpoint;
import io.envoyproxy.envoy.config.endpoint.v3.LocalityLbEndpoints;
import io.envoyproxy.envoy.config.listener.v3.Filter;
import io.envoyproxy.envoy.config.listener.v3.FilterChain;
import io.envoyproxy.envoy.config.listener.v3.Listener;
import io.envoyproxy.envoy.config.route.v3.*;
import io.envoyproxy.envoy.extensions.filters.http.router.v3.Router;
import io.envoyproxy.envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager;
import io.envoyproxy.envoy.extensions.filters.network.http_connection_manager.v3.HttpFilter;
import io.envoyproxy.envoy.extensions.filters.network.http_connection_manager.v3.Rds;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.constant.SimpleIamPortalGatewayAdaptorConstant;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.ResolvedTarget;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.RouteSnapshot;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.TrustedApplicationRoute;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.annotation.SimpleIamPortalGatewayAdaptorComponent;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.configuration.SimpleIamPortalGatewayAdaptorProperties;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.constant.SimpleIamPortalGatewayAdaptorStarterConstant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 将 core 快照编码为 Envoy Java Control Plane 的 v3 资源快照。
 */
@SimpleIamPortalGatewayAdaptorComponent
public class EnvoyXdsSnapshotFactory {

    static final String LISTENER_NAME = "portal-gateway-listener";
    static final String ROUTE_CONFIGURATION_NAME = "portal-gateway-routes";
    static final String HTTP_CONNECTION_MANAGER_NAME = "portal-gateway-http";
    /* xDS 1.0.52 的控制面资源引用提取器使用这个 Envoy 过滤器注册名。 */
    private static final String HTTP_FILTER_NAME = "envoy.http_connection_manager";
    private static final String ROUTER_FILTER_NAME = "envoy.filters.http.router";
    private static final String AUTHORIZATION_HEADER = "authorization";
    private static final String COOKIE_HEADER = "cookie";

    private final SimpleIamPortalGatewayAdaptorProperties properties;

    public EnvoyXdsSnapshotFactory(SimpleIamPortalGatewayAdaptorProperties properties) {
        this.properties = properties;
    }

    /**
     * 构建同一版本的 LDS/RDS/CDS/EDS 快照。
     */
    public Snapshot build(RouteSnapshot source) {
        if (source == null || source.getSnapshotVersion() == null) {
            return null;
        }
        Map<String, List<ResolvedTarget>> targetsByCluster = new LinkedHashMap<>();
        List<TrustedApplicationRoute> routes = new ArrayList<>(source.getRoutes());
        Collections.sort(routes, new Comparator<TrustedApplicationRoute>() {
            @Override
            public int compare(TrustedApplicationRoute left, TrustedApplicationRoute right) {
                int length = Integer.compare(right.getPublicPath().length(), left.getPublicPath().length());
                return length != 0 ? length : left.getPublicPath().compareTo(right.getPublicPath());
            }
        });
        for (TrustedApplicationRoute route : routes) {
            if (route.getForwardCookieNames() != null && !route.getForwardCookieNames().isEmpty()) {
                return null;
            }
            String forwardAuthorization = route.getForwardAuthorization();
            if (!SimpleIamPortalGatewayAdaptorConstant.FORWARD_AUTHORIZATION_NONE.equals(forwardAuthorization)
                    && !SimpleIamPortalGatewayAdaptorConstant.FORWARD_AUTHORIZATION_REQUEST.equals(forwardAuthorization)) {
                return null;
            }
            ResolvedTarget target = route.getTarget();
            if (target == null || !SimpleIamPortalGatewayAdaptorStarterConstant.UPSTREAM_HTTP_SCHEME
                    .equalsIgnoreCase(target.getScheme())) {
                return null;
            }
        }
        List<Route> envoyRoutes = new ArrayList<>();
        for (TrustedApplicationRoute route : routes) {
            String clusterName = clusterName(route.getTarget());
            List<ResolvedTarget> targets = targetsByCluster.get(clusterName);
            if (targets == null) {
                targets = new ArrayList<>();
                targetsByCluster.put(clusterName, targets);
            }
            addTarget(targets, route.getTarget());
            envoyRoutes.add(toRoute(route, clusterName));
        }

        List<Cluster> clusters = new ArrayList<>();
        List<ClusterLoadAssignment> endpoints = new ArrayList<>();
        for (Map.Entry<String, List<ResolvedTarget>> entry : targetsByCluster.entrySet()) {
            clusters.add(toCluster(entry.getKey(), entry.getValue().get(0)));
            endpoints.add(toEndpoints(entry.getKey(), entry.getValue()));
        }
        RouteConfiguration routeConfiguration = RouteConfiguration.newBuilder()
                .setName(ROUTE_CONFIGURATION_NAME)
                .addVirtualHosts(VirtualHost.newBuilder()
                        .setName("portal-gateway-default")
                        .addDomains("*")
                        .addAllRoutes(envoyRoutes)
                        .build())
                .build();
        Listener listener = toListener();
        return Snapshot.create(clusters, endpoints, Collections.singletonList(listener),
                Collections.singletonList(routeConfiguration), Collections.emptyList(), source.getSnapshotVersion());
    }

    private Route toRoute(TrustedApplicationRoute route, String clusterName) {
        RouteAction.Builder action = RouteAction.newBuilder()
                .setCluster(clusterName);
        if (route.getUpstreamHost() != null && !route.getUpstreamHost().trim().isEmpty()) {
            action.setHostRewriteLiteral(route.getUpstreamHost());
        }
        Route.Builder builder = Route.newBuilder()
                .setMatch(RouteMatch.newBuilder()
                        .setPathSeparatedPrefix(normalizePath(route.getPublicPath()))
                        .build())
                .setRoute(action.build());
        if (!SimpleIamPortalGatewayAdaptorConstant.FORWARD_AUTHORIZATION_REQUEST
                .equals(route.getForwardAuthorization())) {
            builder.addRequestHeadersToRemove(AUTHORIZATION_HEADER);
        }
        builder.addRequestHeadersToRemove(COOKIE_HEADER);
        return builder.build();
    }

    private Cluster toCluster(String clusterName, ResolvedTarget target) {
        Cluster.Builder builder = Cluster.newBuilder()
                .setName(clusterName)
                .setType(Cluster.DiscoveryType.EDS)
                .setConnectTimeout(Duration.newBuilder().setSeconds(5L).build())
                .setEdsClusterConfig(Cluster.EdsClusterConfig.newBuilder()
                        .setEdsConfig(adsConfig())
                        .build());
        return builder.build();
    }

    private ClusterLoadAssignment toEndpoints(String clusterName, List<ResolvedTarget> targets) {
        LocalityLbEndpoints.Builder locality = LocalityLbEndpoints.newBuilder();
        for (ResolvedTarget target : targets) {
            SocketAddress socket = SocketAddress.newBuilder()
                    .setAddress(target.getIp())
                    .setPortValue(target.getPort())
                    .setProtocol(SocketAddress.Protocol.TCP)
                    .build();
            locality.addLbEndpoints(LbEndpoint.newBuilder()
                    .setEndpoint(Endpoint.newBuilder()
                            .setAddress(Address.newBuilder().setSocketAddress(socket).build())
                            .build())
                    .build());
        }
        return ClusterLoadAssignment.newBuilder()
                .setClusterName(clusterName)
                .addEndpoints(locality.build())
                .build();
    }

    private Listener toListener() {
        HttpConnectionManager manager = HttpConnectionManager.newBuilder()
                .setStatPrefix(HTTP_CONNECTION_MANAGER_NAME)
                .setRds(Rds.newBuilder()
                        .setRouteConfigName(ROUTE_CONFIGURATION_NAME)
                        .setConfigSource(adsConfig())
                        .build())
                .addHttpFilters(HttpFilter.newBuilder()
                        .setName(ROUTER_FILTER_NAME)
                        .setTypedConfig(Any.pack(Router.newBuilder().build()))
                        .build())
                .build();
        Filter filter = Filter.newBuilder()
                .setName(HTTP_FILTER_NAME)
                .setTypedConfig(Any.pack(manager))
                .build();
        return Listener.newBuilder()
                .setName(LISTENER_NAME)
                .setAddress(Address.newBuilder()
                        .setSocketAddress(SocketAddress.newBuilder()
                                .setAddress(properties.getListenerBindAddress())
                                .setPortValue(properties.getListenerPort())
                                .setProtocol(SocketAddress.Protocol.TCP)
                                .build())
                        .build())
                .addFilterChains(FilterChain.newBuilder().addFilters(filter).build())
                .build();
    }

    private ConfigSource adsConfig() {
        return ConfigSource.newBuilder()
                .setAds(AggregatedConfigSource.newBuilder().build())
                .build();
    }

    private void addTarget(List<ResolvedTarget> targets, ResolvedTarget candidate) {
        for (ResolvedTarget target : targets) {
            if (target.getIp().equals(candidate.getIp()) && target.getPort() == candidate.getPort()) {
                return;
            }
        }
        targets.add(candidate);
    }

    private String clusterName(ResolvedTarget target) {
        String key = target.getScheme() + "|" + target.getHost() + "|" + target.getPort();
        String digest = sha256(key);
        return "portal-cluster-" + digest.substring(0, Math.min(32, digest.length()));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            return "unknown";
        }
    }

    private String normalizePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
