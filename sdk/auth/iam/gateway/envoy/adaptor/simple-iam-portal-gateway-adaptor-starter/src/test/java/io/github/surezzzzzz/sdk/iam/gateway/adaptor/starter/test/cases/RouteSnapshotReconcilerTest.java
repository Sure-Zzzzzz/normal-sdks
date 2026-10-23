package io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.test.cases;

import io.github.surezzzzzz.sdk.iam.client.model.IamPortalApplicationRoute;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.constant.SimpleIamPortalGatewayAdaptorConstant;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.ResolvedTarget;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.RouteValidationReason;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.spi.DnsResolver;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.configuration.SimpleIamPortalGatewayAdaptorProperties;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.service.RouteSnapshotReconciler;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * starter 到 core 的模型映射测试。
 */
@Slf4j
class RouteSnapshotReconcilerTest {

    @Test
    void mapsPortalProjectionToCoreSnapshot() {
        RouteSnapshotReconciler reconciler = new RouteSnapshotReconciler(resolver(),
                new SimpleIamPortalGatewayAdaptorProperties());
        IamPortalApplicationRoute route = IamPortalApplicationRoute.builder()
                .applicationCode("orders")
                .applicationName("Orders")
                .status(1)
                .portalEnabled(true)
                .routePrefix("/app/orders")
                .entry("/app/orders/index.html")
                .apiBase("/api/orders")
                .targetOrigin("http://example.test")
                .configVersion(3L)
                .forwardAuthorization("REQUEST")
                .forwardCookieNames(Collections.<String>emptyList())
                .build();

        io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.RouteSnapshotBuildResult result =
                reconciler.reconcile(Collections.singletonList(route), 9L);

        log.info("starter 映射结果: accepted={}", result.getValidation().isAccepted());
        assertTrue(result.getValidation().isAccepted());
        assertNotNull(result.getSnapshot());
        assertEquals("REQUEST", result.getSnapshot().getRoutes().get(0).getForwardAuthorization());
        assertEquals(3L, result.getSnapshot().getRoutes().get(0).getConfigVersion());
    }

    @Test
    void rejectsPlatformRouteBeforeSnapshot() {
        RouteSnapshotReconciler reconciler = new RouteSnapshotReconciler(resolver(),
                new SimpleIamPortalGatewayAdaptorProperties());
        IamPortalApplicationRoute route = IamPortalApplicationRoute.builder()
                .applicationCode("admin")
                .applicationName("Admin")
                .status(1)
                .portalEnabled(true)
                .routePrefix("/iam/admin")
                .entry("/app/admin/index.html")
                .apiBase("/api/admin")
                .targetOrigin("http://example.test")
                .configVersion(1L)
                .forwardAuthorization("REQUEST")
                .build();

        io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.RouteSnapshotBuildResult result =
                reconciler.reconcile(Collections.singletonList(route), 9L);

        assertEquals(RouteValidationReason.RESERVED_ROUTE_PREFIX, result.getValidation().getReason());
    }

    @Test
    void rejectsAllAkskPlatformPrefixesBeforeSnapshot() {
        List<String> reservedPrefixes = Arrays.asList(
                SimpleIamPortalGatewayAdaptorConstant.RESERVED_AKSK_APP,
                SimpleIamPortalGatewayAdaptorConstant.RESERVED_AKSK_CLIENT_API,
                SimpleIamPortalGatewayAdaptorConstant.RESERVED_AKSK_PERSONAL_CLIENT_API,
                SimpleIamPortalGatewayAdaptorConstant.RESERVED_AKSK_TOKEN_API,
                SimpleIamPortalGatewayAdaptorConstant.RESERVED_AKSK_APPLICATION_AUTHORIZATION_API);
        for (String prefix : reservedPrefixes) {
            RouteSnapshotReconciler reconciler = new RouteSnapshotReconciler(resolver(),
                    new SimpleIamPortalGatewayAdaptorProperties());
            IamPortalApplicationRoute route = IamPortalApplicationRoute.builder()
                    .applicationCode("reserved-" + prefix.replace('/', '-'))
                    .applicationName("Reserved")
                    .status(1)
                    .portalEnabled(true)
                    .routePrefix(prefix)
                    .entry(prefix + "/index.html")
                    .apiBase("/api/reserved")
                    .targetOrigin("http://example.test")
                    .configVersion(1L)
                    .forwardAuthorization("REQUEST")
                    .build();

            io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.RouteSnapshotBuildResult result =
                    reconciler.reconcile(Collections.singletonList(route), 9L);

            log.info("AKSK 固定前缀拒绝结果: prefix={}, reason={}", prefix,
                    result.getValidation().getReason().getCode());
            assertEquals(RouteValidationReason.RESERVED_ROUTE_PREFIX, result.getValidation().getReason());
        }
    }

    @Test
    void rejectsHttpsTargetOrigin() {
        RouteSnapshotReconciler reconciler = new RouteSnapshotReconciler(resolver(),
                new SimpleIamPortalGatewayAdaptorProperties());
        IamPortalApplicationRoute route = IamPortalApplicationRoute.builder()
                .applicationCode("orders")
                .applicationName("Orders")
                .status(1)
                .portalEnabled(true)
                .routePrefix("/app/orders")
                .entry("/app/orders/index.html")
                .apiBase("/api/orders")
                .targetOrigin("https://example.test")
                .configVersion(1L)
                .forwardAuthorization("REQUEST")
                .build();

        io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.RouteSnapshotBuildResult result =
                reconciler.reconcile(Collections.singletonList(route), 9L);

        log.info("HTTPS 页面上游拒绝结果: accepted={}, reason={}", result.getValidation().isAccepted(),
                result.getValidation().getReason().getCode());
        assertEquals(RouteValidationReason.INVALID_TARGET_ORIGIN, result.getValidation().getReason());
    }

    @Test
    void rejectsHttpsApiBase() {
        RouteSnapshotReconciler reconciler = new RouteSnapshotReconciler(resolver(),
                new SimpleIamPortalGatewayAdaptorProperties());
        IamPortalApplicationRoute route = IamPortalApplicationRoute.builder()
                .applicationCode("orders")
                .applicationName("Orders")
                .status(1)
                .portalEnabled(true)
                .routePrefix("/app/orders")
                .entry("/app/orders/index.html")
                .apiBase("https://api.example.test/v1")
                .targetOrigin("http://example.test")
                .configVersion(1L)
                .forwardAuthorization("REQUEST")
                .build();

        io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.RouteSnapshotBuildResult result =
                reconciler.reconcile(Collections.singletonList(route), 9L);

        log.info("HTTPS API 上游拒绝结果: accepted={}, reason={}", result.getValidation().isAccepted(),
                result.getValidation().getReason().getCode());
        assertEquals(RouteValidationReason.INVALID_API_BASE, result.getValidation().getReason());
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
