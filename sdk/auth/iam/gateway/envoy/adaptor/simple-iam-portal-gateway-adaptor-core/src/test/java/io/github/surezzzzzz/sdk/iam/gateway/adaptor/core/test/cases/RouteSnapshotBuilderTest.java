package io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.test.cases;

import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.PortalApplicationRoute;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.ResolvedTarget;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.RouteSnapshotBuildResult;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.RouteValidationReason;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.TargetUrlPolicy;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.service.RouteSnapshotBuilder;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.spi.DnsResolver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * core 路由契约的边界测试。
 */
@Slf4j
class RouteSnapshotBuilderTest {

    private final RouteSnapshotBuilder builder = new RouteSnapshotBuilder();

    @Test
    void buildsStablePageAndApiSnapshot() {
        PortalApplicationRoute route = route("orders", "/app/orders", "REQUEST");
        RouteSnapshotBuildResult result = builder.build(7L, TargetUrlPolicy.defaults(),
                Collections.singletonList(route), resolver());

        log.info("快照构建结果: accepted={}, routeCount={}", result.getValidation().isAccepted(),
                result.getSnapshot() == null ? 0 : result.getSnapshot().getRoutes().size());
        assertTrue(result.getValidation().isAccepted());
        assertNotNull(result.getSnapshot());
        assertEquals(2, result.getSnapshot().getRoutes().size());
        assertNotNull(result.getSnapshot().getTargetDigest());
        assertEquals("/api", result.getSnapshot().getRoutes().get(0).getPublicPath());
        assertEquals("/api", result.getSnapshot().getRoutes().get(0).getUpstreamPath());
        assertEquals("/app/orders", result.getSnapshot().getRoutes().get(1).getPublicPath());
        assertEquals("/app/orders", result.getSnapshot().getRoutes().get(1).getUpstreamPath());
        assertTrue(result.getSnapshot().getRoutes().stream()
                .allMatch(item -> "REQUEST".equals(item.getForwardAuthorization())));
    }

    @Test
    void forwardingPolicyChangesSnapshotVersion() {
        PortalApplicationRoute none = route("orders", "/app/orders", "NONE");
        PortalApplicationRoute request = route("orders", "/app/orders", "REQUEST");

        RouteSnapshotBuildResult first = builder.build(7L, TargetUrlPolicy.defaults(),
                Collections.singletonList(none), resolver());
        RouteSnapshotBuildResult second = builder.build(7L, TargetUrlPolicy.defaults(),
                Collections.singletonList(request), resolver());

        assertTrue(first.getValidation().isAccepted());
        assertTrue(second.getValidation().isAccepted());
        assertFalse(first.getSnapshot().getSnapshotVersion().equals(second.getSnapshot().getSnapshotVersion()));
    }

    @Test
    void rejectsConflictingPublicPath() {
        List<PortalApplicationRoute> routes = Arrays.asList(
                route("orders", "/app/orders", "NONE"),
                route("billing", "/app/billing", "NONE"));
        PortalApplicationRoute second = PortalApplicationRoute.builder()
                .applicationCode("billing")
                .applicationName("Billing")
                .status(1)
                .portalEnabled(true)
                .routePrefix("/app/orders/api")
                .entry("/app/orders/index.html")
                .apiBase("/api/orders")
                .targetOrigin("http://example.test")
                .configVersion(1L)
                .forwardAuthorization("NONE")
                .build();
        routes.set(1, second);

        RouteSnapshotBuildResult result = builder.build(7L, TargetUrlPolicy.defaults(), routes, resolver());

        assertFalse(result.getValidation().isAccepted());
        assertEquals(RouteValidationReason.ROUTE_PREFIX_CONFLICT,
                result.getValidation().getReason());
    }

    @Test
    void rejectsPrivateAndNonLiteralTargetsByDefault() {
        TargetUrlPolicy policy = TargetUrlPolicy.defaults();

        assertFalse(policy.isTargetAllowed("127.0.0.1"));
        assertFalse(policy.isTargetAllowed("10.0.0.5"));
        assertFalse(policy.isTargetAllowed("::ffff:10.0.0.5"));
        assertFalse(policy.isTargetAllowed("example.test"));
        assertFalse(policy.isTargetAllowed("0.0.0.0"));
    }

    @Test
    void rejectsHttpsTargetOriginAndAbsoluteApiBase() {
        PortalApplicationRoute httpsTarget = PortalApplicationRoute.builder()
                .applicationCode("orders")
                .applicationName("orders")
                .status(1)
                .portalEnabled(true)
                .routePrefix("/app/orders")
                .entry("/app/orders/index.html")
                .apiBase("/api/orders")
                .targetOrigin("https://example.test")
                .configVersion(1L)
                .forwardAuthorization("NONE")
                .build();
        RouteSnapshotBuildResult targetResult = builder.build(7L, TargetUrlPolicy.defaults(),
                Collections.singletonList(httpsTarget), resolver());

        PortalApplicationRoute httpsApi = PortalApplicationRoute.builder()
                .applicationCode("orders")
                .applicationName("orders")
                .status(1)
                .portalEnabled(true)
                .routePrefix("/app/orders")
                .entry("/app/orders/index.html")
                .apiBase("https://api.example.test/v1")
                .targetOrigin("http://example.test")
                .configVersion(1L)
                .forwardAuthorization("NONE")
                .build();
        RouteSnapshotBuildResult apiResult = builder.build(7L, TargetUrlPolicy.defaults(),
                Collections.singletonList(httpsApi), resolver());

        assertEquals(RouteValidationReason.INVALID_TARGET_ORIGIN, targetResult.getValidation().getReason());
        assertEquals(RouteValidationReason.INVALID_API_BASE, apiResult.getValidation().getReason());
    }

    @Test
    void rejectsEntryOutsideApplicationRoutePrefix() {
        PortalApplicationRoute route = PortalApplicationRoute.builder()
                .applicationCode("orders")
                .applicationName("Orders")
                .status(1)
                .portalEnabled(true)
                .routePrefix("/app/orders")
                .entry("/app/billing/index.html")
                .apiBase("/api/orders")
                .targetOrigin("http://example.test")
                .configVersion(1L)
                .forwardAuthorization("REQUEST")
                .build();

        RouteSnapshotBuildResult result = builder.build(7L, TargetUrlPolicy.defaults(),
                Collections.singletonList(route), resolver());

        assertEquals(RouteValidationReason.INVALID_ENTRY, result.getValidation().getReason());
    }

    @Test
    void rejectsBuiltInAkskPageAndApiPrefixes() {
        PortalApplicationRoute pageRoute = route("orders", "/app/aksk/other", "NONE");
        RouteSnapshotBuildResult pageResult = builder.build(7L, TargetUrlPolicy.defaults(),
                Collections.singletonList(pageRoute), resolver());

        PortalApplicationRoute apiRoute = PortalApplicationRoute.builder()
                .applicationCode("orders")
                .applicationName("orders")
                .status(1)
                .portalEnabled(true)
                .routePrefix("/app/orders")
                .entry("/app/orders/index.html")
                .apiBase("/api/client")
                .targetOrigin("http://example.test")
                .configVersion(1L)
                .forwardAuthorization("NONE")
                .build();
        RouteSnapshotBuildResult apiResult = builder.build(7L, TargetUrlPolicy.defaults(),
                Collections.singletonList(apiRoute), resolver());

        assertEquals(RouteValidationReason.RESERVED_ROUTE_PREFIX, pageResult.getValidation().getReason());
        assertEquals(RouteValidationReason.RESERVED_API_BASE, apiResult.getValidation().getReason());
    }

    @Test
    void rejectsMissingOrNonContractAuthorizationPolicy() {
        for (String policy : Arrays.asList(null, "request", "BEARER")) {
            PortalApplicationRoute route = route("orders", "/app/orders", policy);
            RouteSnapshotBuildResult result = builder.build(7L, TargetUrlPolicy.defaults(),
                    Collections.singletonList(route), resolver());
            assertEquals(RouteValidationReason.INVALID_FORWARD_POLICY, result.getValidation().getReason());
        }
    }

    @Test
    void rejectsApplicationCodeOutsideIamContract() {
        for (String code : Arrays.asList("orders.v1", "orders/one", "orders|one", repeat("a", 65), "订单")) {
            RouteSnapshotBuildResult result = builder.build(7L, TargetUrlPolicy.defaults(),
                    Collections.singletonList(route(code, "/app/orders", "NONE")), resolver());

            log.info("应用编码校验结果: code={}, accepted={}, reason={}", code,
                    result.getValidation().isAccepted(), result.getValidation().getReason());
            assertEquals(RouteValidationReason.INVALID_APPLICATION_CODE, result.getValidation().getReason());
        }
    }

    @Test
    void rejectsBlankOrOversizedApplicationName() {
        for (String name : Arrays.asList(null, "   ", repeat("名", 129))) {
            PortalApplicationRoute route = route("orders", "/app/orders", "NONE");
            route = PortalApplicationRoute.builder()
                    .applicationCode(route.getApplicationCode())
                    .applicationName(name)
                    .status(route.getStatus())
                    .portalEnabled(route.getPortalEnabled())
                    .routePrefix(route.getRoutePrefix())
                    .entry(route.getEntry())
                    .apiBase(route.getApiBase())
                    .targetOrigin(route.getTargetOrigin())
                    .configVersion(route.getConfigVersion())
                    .forwardAuthorization(route.getForwardAuthorization())
                    .build();
            RouteSnapshotBuildResult result = builder.build(7L, TargetUrlPolicy.defaults(),
                    Collections.singletonList(route), resolver());

            log.info("应用名称校验结果: accepted={}, reason={}", result.getValidation().isAccepted(),
                    result.getValidation().getReason());
            assertEquals(RouteValidationReason.INVALID_APPLICATION_NAME, result.getValidation().getReason());
        }
    }

    @Test
    void rejectsWhitespaceAndOversizedPublicPaths() {
        PortalApplicationRoute whitespace = route("orders", "/app/order s", "NONE");
        PortalApplicationRoute oversized = route("orders", "/" + repeat("a", 128), "NONE");

        RouteSnapshotBuildResult whitespaceResult = builder.build(7L, TargetUrlPolicy.defaults(),
                Collections.singletonList(whitespace), resolver());
        RouteSnapshotBuildResult oversizedResult = builder.build(7L, TargetUrlPolicy.defaults(),
                Collections.singletonList(oversized), resolver());

        log.info("公共路径边界结果: whitespace={}, oversized={}", whitespaceResult.getValidation().getReason(),
                oversizedResult.getValidation().getReason());
        assertEquals(RouteValidationReason.INVALID_ROUTE_PREFIX, whitespaceResult.getValidation().getReason());
        assertEquals(RouteValidationReason.INVALID_ROUTE_PREFIX, oversizedResult.getValidation().getReason());
    }

    @Test
    void rejectsSpecialTargetAddressRanges() {
        TargetUrlPolicy policy = TargetUrlPolicy.defaults();

        for (String ip : Arrays.asList("224.0.0.1", "255.255.255.255", "100.64.0.1",
                "::ffff:224.0.0.1", "::ffff:100.64.0.1")) {
            log.info("目标地址策略结果: ip={}, allowed={}", ip, policy.isTargetAllowed(ip));
            assertFalse(policy.isTargetAllowed(ip));
        }
    }

    @Test
    void allowsExplicitLocalTargetPolicy() {
        TargetUrlPolicy policy = TargetUrlPolicy.builder()
                .allowLoopback(true)
                .allowPrivateNetwork(true)
                .allowMetadata(true)
                .build();

        log.info("显式本地目标策略结果: loopback={}, private={}, metadata={}",
                policy.isTargetAllowed("127.0.0.1"), policy.isTargetAllowed("10.0.0.5"),
                policy.isTargetAllowed("169.254.169.254"));
        assertTrue(policy.isTargetAllowed("127.0.0.1"));
        assertTrue(policy.isTargetAllowed("10.0.0.5"));
        assertTrue(policy.isTargetAllowed("169.254.169.254"));
    }

    @Test
    void rejectsDnsFailureAndInconsistentResolvedTarget() {
        DnsResolver throwingResolver = (scheme, host, port) -> {
            throw new RuntimeException("dns unavailable");
        };
        RouteSnapshotBuildResult throwingResult = builder.build(7L, TargetUrlPolicy.defaults(),
                Collections.singletonList(route("orders", "/app/orders", "NONE")), throwingResolver);
        DnsResolver inconsistentResolver = (scheme, host, port) -> Collections.singletonList(ResolvedTarget.builder()
                .host("other.test")
                .ip("203.0.113.10")
                .scheme(scheme)
                .port(port)
                .build());
        RouteSnapshotBuildResult inconsistentResult = builder.build(7L, TargetUrlPolicy.defaults(),
                Collections.singletonList(route("orders", "/app/orders", "NONE")), inconsistentResolver);

        log.info("DNS 失败结果: throwing={}, inconsistent={}", throwingResult.getValidation().getReason(),
                inconsistentResult.getValidation().getReason());
        assertEquals(RouteValidationReason.DNS_RESOLUTION_FAILED, throwingResult.getValidation().getReason());
        assertEquals(RouteValidationReason.DNS_RESOLUTION_FAILED, inconsistentResult.getValidation().getReason());
    }

    @Test
    void distinguishesCanonicalValuesContainingDelimiters() {
        PortalApplicationRoute first = routeWithName("orders", "/app/orders", "a|1");
        PortalApplicationRoute second = routeWithName("orders", "/app/orders", "a");

        RouteSnapshotBuildResult firstResult = builder.build(7L, TargetUrlPolicy.defaults(),
                Collections.singletonList(first), resolver());
        RouteSnapshotBuildResult secondResult = builder.build(7L, TargetUrlPolicy.defaults(),
                Collections.singletonList(second), resolver());

        log.info("快照摘要区分结果: first={}, second={}", firstResult.getSnapshot().getSnapshotVersion(),
                secondResult.getSnapshot().getSnapshotVersion());
        assertNotEquals(firstResult.getSnapshot().getSnapshotVersion(),
                secondResult.getSnapshot().getSnapshotVersion());
    }

    @Test
    void acceptsEmptyDirectoryAsRouteRemovalSnapshot() {
        RouteSnapshotBuildResult result = builder.build(7L, TargetUrlPolicy.defaults(),
                Collections.<PortalApplicationRoute>emptyList(), resolver());

        log.info("空目录快照结果: accepted={}, routeCount={}", result.getValidation().isAccepted(),
                result.getSnapshot() == null ? 0 : result.getSnapshot().getRoutes().size());
        assertTrue(result.getValidation().isAccepted());
        assertNotNull(result.getSnapshot());
        assertTrue(result.getSnapshot().getRoutes().isEmpty());
    }

    private PortalApplicationRoute route(String code, String prefix, String authorization) {
        return PortalApplicationRoute.builder()
                .applicationCode(code)
                .applicationName(code)
                .status(1)
                .portalEnabled(true)
                .routePrefix(prefix)
                .entry("/app/" + code + "/index.html")
                .apiBase("/api")
                .targetOrigin("http://example.test")
                .configVersion(1L)
                .forwardAuthorization(authorization)
                .build();
    }

    private PortalApplicationRoute routeWithName(String code, String prefix, String name) {
        PortalApplicationRoute route = route(code, prefix, "NONE");
        return PortalApplicationRoute.builder()
                .applicationCode(route.getApplicationCode())
                .applicationName(name)
                .status(route.getStatus())
                .portalEnabled(route.getPortalEnabled())
                .routePrefix(route.getRoutePrefix())
                .entry(route.getEntry())
                .apiBase(route.getApiBase())
                .targetOrigin(route.getTargetOrigin())
                .configVersion(route.getConfigVersion())
                .forwardAuthorization(route.getForwardAuthorization())
                .build();
    }

    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }

    private DnsResolver resolver() {
        return (scheme, host, port) -> Collections.singletonList(ResolvedTarget.builder()
                .host(host)
                .ip("203.0.113.10")
                .scheme(scheme)
                .port(port)
                .build());
    }
}
