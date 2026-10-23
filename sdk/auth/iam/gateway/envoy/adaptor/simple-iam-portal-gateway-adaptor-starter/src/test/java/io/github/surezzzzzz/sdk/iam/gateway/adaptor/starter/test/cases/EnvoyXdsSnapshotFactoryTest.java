package io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.test.cases;

import io.envoyproxy.controlplane.cache.v3.Snapshot;
import io.envoyproxy.envoy.config.cluster.v3.Cluster;
import io.envoyproxy.envoy.config.listener.v3.Listener;
import io.envoyproxy.envoy.config.route.v3.Route;
import io.envoyproxy.envoy.config.route.v3.RouteConfiguration;
import io.envoyproxy.envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.ResolvedTarget;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.RouteKind;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.RouteSnapshot;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.TrustedApplicationRoute;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.configuration.SimpleIamPortalGatewayAdaptorProperties;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.service.EnvoyXdsSnapshotFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Envoy xDS 资源工厂的安全和依赖完整性测试。
 */
@Slf4j
class EnvoyXdsSnapshotFactoryTest {

    @Test
    void buildsHttpHostRewritePathSeparatedPrefixAndRouterFilter() throws Exception {
        TrustedApplicationRoute shortRoute = route("/app/orders", "http", "/", "api.example.com", "REQUEST");
        TrustedApplicationRoute longRoute = route("/app/audit/detail", "http", "/v1", "api.example.com", "NONE");

        SimpleIamPortalGatewayAdaptorProperties properties = new SimpleIamPortalGatewayAdaptorProperties();
        Snapshot snapshot = new EnvoyXdsSnapshotFactory(properties).build(RouteSnapshot.builder()
                .catalogRevision(1L)
                .policyVersion("policy")
                .snapshotVersion("snapshot")
                .targetDigest("digest")
                .routes(Arrays.asList(shortRoute, longRoute))
                .build());
        snapshot.ensureConsistent();

        Cluster cluster = snapshot.clusters().resources().values().iterator().next();
        assertFalse(cluster.hasTransportSocket());

        RouteConfiguration routeConfiguration = snapshot.routes().resources().values().iterator().next();
        Route first = routeConfiguration.getVirtualHosts(0).getRoutes(0);
        assertEquals("/app/audit/detail", first.getMatch().getPathSeparatedPrefix());
        assertEquals("api.example.com", first.getRoute().getHostRewriteLiteral());
        assertTrue(first.getRequestHeadersToRemoveList().contains("authorization"));
        assertTrue(first.getRequestHeadersToRemoveList().contains("cookie"));
        Route second = routeConfiguration.getVirtualHosts(0).getRoutes(1);
        assertFalse(second.getRequestHeadersToRemoveList().contains("authorization"));
        assertTrue(second.getRequestHeadersToRemoveList().contains("cookie"));

        Listener listener = snapshot.listeners().resources().values().iterator().next();
        HttpConnectionManager manager = listener.getFilterChains(0).getFilters(0)
                .getTypedConfig().unpack(HttpConnectionManager.class);
        assertEquals("envoy.http_connection_manager",
                listener.getFilterChains(0).getFilters(0).getName());
        assertTrue(manager.getHttpFiltersList().stream()
                .anyMatch(filter -> "envoy.filters.http.router".equals(filter.getName())));
        log.info("HTTP xDS 资源验证通过: clusters={}, routes={}", snapshot.clusters().resources().size(),
                routeConfiguration.getVirtualHosts(0).getRoutesCount());
    }

    @Test
    void rejectsHttpsUpstreamWhenFactoryIsCalledDirectly() {
        TrustedApplicationRoute route = route("/app", "https", "/", "orders.example.com", "REQUEST");

        Snapshot snapshot = factory().build(RouteSnapshot.builder()
                .catalogRevision(1L)
                .policyVersion("policy")
                .snapshotVersion("snapshot")
                .targetDigest("digest")
                .routes(Collections.singletonList(route))
                .build());

        log.info("直接传入 HTTPS 上游时不生成 xDS 快照: snapshotPresent={}", snapshot != null);
        assertNull(snapshot);
    }

    @Test
    void rejectsCookieWhitelistUntilItCanBeAppliedWithoutLeakingCookies() {
        TrustedApplicationRoute route = TrustedApplicationRoute.builder()
                .applicationCode("orders")
                .applicationName("Orders")
                .kind(RouteKind.PAGE)
                .publicPath("/orders")
                .upstreamPath("/")
                .target(ResolvedTarget.builder().host("orders.example.com").ip("203.0.113.10")
                        .scheme("http").port(80).build())
                .upstreamHost("orders.example.com")
                .configVersion(1L)
                .forwardCookieName("session")
                .build();

        assertNull(factory().build(RouteSnapshot.builder()
                .catalogRevision(1L)
                .policyVersion("policy")
                .snapshotVersion("snapshot")
                .targetDigest("digest")
                .routes(Collections.singletonList(route))
                .build()));
    }

    @Test
    void rejectsUnknownAuthorizationPolicy() {
        TrustedApplicationRoute route = route("/app", "http", "/", "orders.example.com", "request");

        assertNull(factory().build(RouteSnapshot.builder()
                .catalogRevision(1L)
                .policyVersion("policy")
                .snapshotVersion("snapshot")
                .targetDigest("digest")
                .routes(Collections.singletonList(route))
                .build()));
    }

    private EnvoyXdsSnapshotFactory factory() {
        return new EnvoyXdsSnapshotFactory(new SimpleIamPortalGatewayAdaptorProperties());
    }

    private TrustedApplicationRoute route(String publicPath, String scheme, String upstreamPath,
                                          String host, String forwardAuthorization) {
        return TrustedApplicationRoute.builder()
                .applicationCode("orders")
                .applicationName("Orders")
                .kind(RouteKind.PAGE)
                .publicPath(publicPath)
                .upstreamPath(upstreamPath)
                .target(ResolvedTarget.builder().host(host).ip("203.0.113.10")
                        .scheme(scheme).port("https".equals(scheme) ? 443 : 80)
                        .build())
                .upstreamHost(host)
                .configVersion(1L)
                .forwardAuthorization(forwardAuthorization)
                .build();
    }
}
