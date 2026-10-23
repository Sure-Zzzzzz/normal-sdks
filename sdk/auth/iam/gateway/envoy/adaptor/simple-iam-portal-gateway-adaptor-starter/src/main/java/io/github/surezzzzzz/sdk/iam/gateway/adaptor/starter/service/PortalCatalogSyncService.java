package io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.service;

import io.github.surezzzzzz.sdk.iam.client.IamPortalApplicationClient;
import io.github.surezzzzzz.sdk.iam.client.model.IamPortalApplicationPage;
import io.github.surezzzzzz.sdk.iam.client.model.IamPortalApplicationRoute;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.GatewayRouteEvent;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.GatewayRouteEventType;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.RouteSnapshot;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.RouteSnapshotBuildResult;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.annotation.SimpleIamPortalGatewayAdaptorComponent;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.configuration.SimpleIamPortalGatewayAdaptorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.*;

/**
 * 读取 IAM 连续分页并以版本闸门替换整份路由快照。
 */
@SimpleIamPortalGatewayAdaptorComponent
public class PortalCatalogSyncService implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(PortalCatalogSyncService.class);

    private final IamPortalApplicationClient client;
    private final RouteSnapshotReconciler reconciler;
    private final GatewayRouteSnapshotState state;
    private final XdsSnapshotPort xds;
    private final GatewayEventPublisher eventPublisher;
    private final SimpleIamPortalGatewayAdaptorProperties properties;

    public PortalCatalogSyncService(IamPortalApplicationClient client,
                                    RouteSnapshotReconciler reconciler,
                                    GatewayRouteSnapshotState state,
                                    XdsSnapshotPort xds,
                                    GatewayEventPublisher eventPublisher,
                                    SimpleIamPortalGatewayAdaptorProperties properties) {
        this.client = client;
        this.reconciler = reconciler;
        this.state = state;
        this.xds = xds;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    /**
     * 启动时先尝试建立首份目录快照；失败由同步入口记录并交给定时重试，不能阻断宿主启动。
     */
    @Override
    public void afterPropertiesSet() {
        syncOnce();
    }

    /**
     * 定时同步入口；外部 runtime 也可显式调用该方法。
     */
    @Scheduled(fixedDelayString = "${io.github.surezzzzzz.sdk.iam.portal.gateway.adaptor.sync-interval-millis:30000}")
    public synchronized void syncOnce() {
        long startedAt = System.nanoTime();
        String correlationId = UUID.randomUUID().toString();
        LOGGER.debug("Portal Gateway 目录同步开始，correlationId={}", correlationId);
        publish(GatewayRouteEventType.CATALOG_SYNC_STARTED, correlationId, null, null, 0, null, null);
        try {
            DirectoryRead directory = readDirectory();
            RouteSnapshotBuildResult result = reconciler.reconcile(directory.routes, directory.revision);
            if (!result.getValidation().isAccepted() || result.getSnapshot() == null) {
                state.recordFailure(result.getValidation().getReason().getCode());
                publish(GatewayRouteEventType.ROUTE_REJECTED, correlationId, directory.revision, null,
                        directory.routes.size(), null, result.getValidation().getReason().getCode());
                return;
            }
            RouteSnapshot snapshot = result.getSnapshot();
            if (!xds.publish(snapshot)) {
                state.recordFailure("xds-publish-rejected");
                publish(GatewayRouteEventType.SNAPSHOT_RETAINED, correlationId, directory.revision,
                        snapshot.getSnapshotVersion(), snapshot.getRoutes().size(), snapshot.getTargetDigest(),
                        "xds-publish-rejected");
                return;
            }
            publish(GatewayRouteEventType.SNAPSHOT_PUBLISHED, correlationId, directory.revision,
                    snapshot.getSnapshotVersion(), snapshot.getRoutes().size(), snapshot.getTargetDigest(), null);
            LOGGER.debug("Portal Gateway 目录快照已交给 xDS，correlationId={}, revision={}, snapshotVersion={}, routeCount={}, elapsedMillis={}",
                    correlationId, directory.revision, snapshot.getSnapshotVersion(), snapshot.getRoutes().size(),
                    (System.nanoTime() - startedAt) / 1_000_000L);
        } catch (RuntimeException ex) {
            state.recordFailure("catalog-sync-failed");
            LOGGER.warn("Portal Gateway 目录同步失败，保留上一份快照，reason={}, elapsedMillis={}",
                    ex.getClass().getSimpleName(), (System.nanoTime() - startedAt) / 1_000_000L);
            publish(GatewayRouteEventType.SNAPSHOT_RETAINED, correlationId, null, null, 0,
                    null, "catalog-sync-failed");
        }
    }

    private DirectoryRead readDirectory() {
        IamPortalApplicationPage first = client.listApplications(0, properties.getPageSize(), null);
        if (first == null || first.getCatalogRevision() == null || first.getPage() != 0) {
            throw new SyncFailure("directory-incomplete");
        }
        List<IamPortalApplicationRoute> routes = new ArrayList<>();
        Set<String> applicationCodes = new HashSet<>();
        addItems(first, routes, applicationCodes);
        long total = first.getTotalElements();
        int pageSize = first.getSize();
        if (total < 0L || pageSize <= 0) {
            throw new SyncFailure("directory-incomplete");
        }
        int page = 1;
        while (routes.size() < total) {
            IamPortalApplicationPage next = client.listApplications(page, properties.getPageSize(),
                    first.getCatalogRevision());
            if (next == null || !first.getCatalogRevision().equals(next.getCatalogRevision())
                    || next.getPage() != page || next.getTotalElements() != total
                    || next.getSize() != pageSize) {
                throw new SyncFailure("catalog-revision-mismatch");
            }
            int before = routes.size();
            addItems(next, routes, applicationCodes);
            if (routes.size() == before) {
                throw new SyncFailure("directory-incomplete");
            }
            page++;
        }
        if (routes.size() != total) {
            throw new SyncFailure("directory-total-mismatch");
        }
        return new DirectoryRead(first.getCatalogRevision(), routes);
    }

    private void addItems(IamPortalApplicationPage page, List<IamPortalApplicationRoute> routes,
                          Set<String> applicationCodes) {
        if (page.getItems() != null) {
            for (IamPortalApplicationRoute item : page.getItems()) {
                if (item == null || item.getApplicationCode() == null
                        || !applicationCodes.add(item.getApplicationCode())) {
                    throw new SyncFailure("directory-duplicate-application");
                }
                routes.add(item);
            }
        }
    }

    private void publish(GatewayRouteEventType type, String correlationId, Long revision,
                         String snapshotVersion, int routeCount, String targetDigest, String reason) {
        eventPublisher.publish(GatewayRouteEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .correlationId(correlationId)
                .type(type)
                .catalogRevision(revision)
                .snapshotVersion(snapshotVersion)
                .routeCount(routeCount)
                .targetDigest(targetDigest)
                .reasonCode(reason)
                .occurredAt(Instant.now())
                .build());
    }

    private static final class SyncFailure extends RuntimeException {

        private SyncFailure(String message) {
            super(message);
        }
    }

    private static final class DirectoryRead {

        private final long revision;
        private final List<IamPortalApplicationRoute> routes;

        private DirectoryRead(long revision, List<IamPortalApplicationRoute> routes) {
            this.revision = revision;
            this.routes = routes;
        }
    }
}
