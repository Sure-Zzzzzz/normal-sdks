package io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.test.cases;

import io.envoyproxy.envoy.service.discovery.v3.DiscoveryRequest;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryResponse;
import io.github.surezzzzzz.sdk.iam.client.IamPortalApplicationClient;
import io.github.surezzzzzz.sdk.iam.client.model.IamPortalApplicationPage;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.*;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.configuration.SimpleIamPortalGatewayAdaptorProperties;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.service.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * 目录同步启动边界测试。
 */
@Slf4j
class PortalCatalogSyncServiceTest {

    @Test
    void performsInitialSyncAndPublishesEmptyRemovalSnapshot() {
        IamPortalApplicationClient client = mock(IamPortalApplicationClient.class);
        RouteSnapshotReconciler reconciler = mock(RouteSnapshotReconciler.class);
        XdsSnapshotPort xds = mock(XdsSnapshotPort.class);
        GatewayEventPublisher eventPublisher = mock(GatewayEventPublisher.class);
        SimpleIamPortalGatewayAdaptorProperties properties = new SimpleIamPortalGatewayAdaptorProperties();
        properties.setPageSize(25);

        IamPortalApplicationPage page = IamPortalApplicationPage.builder()
                .catalogRevision(7L)
                .totalElements(0L)
                .page(0)
                .size(25)
                .items(Collections.emptyList())
                .build();
        when(client.listApplications(0, 25, null)).thenReturn(page);
        when(reconciler.reconcile(Collections.emptyList(), 7L))
                .thenReturn(RouteSnapshotBuildResult.accepted(RouteSnapshot.builder()
                        .catalogRevision(7L)
                        .policyVersion("policy")
                        .snapshotVersion("empty-directory")
                        .targetDigest("digest")
                        .routes(Collections.emptyList())
                        .build()));
        when(xds.publish(any(RouteSnapshot.class))).thenReturn(true);

        PortalCatalogSyncService service = new PortalCatalogSyncService(client, reconciler,
                new GatewayRouteSnapshotState(), xds, eventPublisher, properties);
        service.afterPropertiesSet();

        verify(client).listApplications(0, 25, null);
        verify(reconciler).reconcile(Collections.emptyList(), 7L);
        verify(xds).publish(any(RouteSnapshot.class));
        log.info("启动首同步已触发，目录版本={}", page.getCatalogRevision());
    }

    @Test
    void recordsRetainedSnapshotWhenControlPlaneRejectsCandidate() {
        IamPortalApplicationClient client = mock(IamPortalApplicationClient.class);
        RouteSnapshotReconciler reconciler = mock(RouteSnapshotReconciler.class);
        XdsSnapshotPort xds = mock(XdsSnapshotPort.class);
        GatewayEventPublisher eventPublisher = mock(GatewayEventPublisher.class);
        SimpleIamPortalGatewayAdaptorProperties properties = new SimpleIamPortalGatewayAdaptorProperties();
        RouteSnapshot candidate = RouteSnapshot.builder()
                .catalogRevision(8L)
                .policyVersion("policy")
                .snapshotVersion("candidate")
                .targetDigest("digest")
                .routes(Collections.emptyList())
                .build();
        IamPortalApplicationPage page = IamPortalApplicationPage.builder()
                .catalogRevision(8L)
                .totalElements(0L)
                .page(0)
                .size(100)
                .items(Collections.emptyList())
                .build();
        when(client.listApplications(0, 100, null)).thenReturn(page);
        when(reconciler.reconcile(Collections.emptyList(), 8L))
                .thenReturn(RouteSnapshotBuildResult.accepted(candidate));
        when(xds.publish(candidate)).thenReturn(false);

        new PortalCatalogSyncService(client, reconciler, new GatewayRouteSnapshotState(), xds,
                eventPublisher, properties).afterPropertiesSet();

        ArgumentCaptor<GatewayRouteEvent> captor = ArgumentCaptor.forClass(GatewayRouteEvent.class);
        verify(eventPublisher, atLeastOnce()).publish(captor.capture());
        List<GatewayRouteEvent> events = captor.getAllValues();
        assertEquals(GatewayRouteEventType.SNAPSHOT_RETAINED,
                events.get(events.size() - 1).getType());
    }

    @Test
    void ignoresAckForSnapshotSupersededBeforeAck() throws Exception {
        SimpleIamPortalGatewayAdaptorProperties properties = new SimpleIamPortalGatewayAdaptorProperties();
        properties.setXdsBindAddress("127.0.0.1");
        properties.setXdsPort(0);
        GatewayRouteSnapshotState state = new GatewayRouteSnapshotState();
        io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.service.XdsSnapshotService xds =
                new io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.service.XdsSnapshotService(
                        properties,
                        new io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.service.EnvoyXdsSnapshotFactory(
                                properties),
                        state,
                        mock(GatewayEventPublisher.class));
        xds.afterPropertiesSet();
        try {
            RouteSnapshot oldSnapshot = snapshot("old");
            RouteSnapshot newSnapshot = snapshot("new");
            xds.publish(oldSnapshot);

            Method onResponse = xds.getClass().getDeclaredMethod("onResponse", long.class,
                    DiscoveryRequest.class, DiscoveryResponse.class);
            Method onRequest = xds.getClass().getDeclaredMethod("onRequest", long.class,
                    DiscoveryRequest.class);
            onResponse.setAccessible(true);
            onRequest.setAccessible(true);
            onResponse.invoke(xds, 1L,
                    DiscoveryRequest.newBuilder()
                            .setTypeUrl("type.googleapis.com/envoy.config.cluster.v3.Cluster")
                            .build(),
                    DiscoveryResponse.newBuilder().setNonce("old-nonce").setVersionInfo("old").build());

            xds.publish(newSnapshot);
            onRequest.invoke(xds, 1L,
                    DiscoveryRequest.newBuilder().setResponseNonce("old-nonce").build());

            org.junit.jupiter.api.Assertions.assertNull(state.current());
            log.info("过期 ACK 未覆盖新版本，pending={}", xds.pending().getSnapshotVersion());
        } finally {
            xds.destroy();
        }
    }

    private RouteSnapshot snapshot(String version) {
        TrustedApplicationRoute route = TrustedApplicationRoute.builder()
                .applicationCode("orders")
                .applicationName("Orders")
                .kind(RouteKind.PAGE)
                .publicPath("/orders")
                .upstreamPath("/")
                .target(ResolvedTarget.builder().host("orders.example.com").ip("203.0.113.10")
                        .scheme("http").port(80).build())
                .configVersion(1L)
                .forwardAuthorization("REQUEST")
                .build();
        return RouteSnapshot.builder()
                .catalogRevision(1L)
                .policyVersion("policy")
                .snapshotVersion(version)
                .targetDigest("digest-" + version)
                .routes(Collections.singletonList(route))
                .build();
    }
}
