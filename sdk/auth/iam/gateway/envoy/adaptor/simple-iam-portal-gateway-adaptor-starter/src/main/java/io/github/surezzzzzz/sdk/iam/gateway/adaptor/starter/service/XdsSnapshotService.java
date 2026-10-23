package io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.service;

import io.envoyproxy.controlplane.cache.SnapshotConsistencyException;
import io.envoyproxy.controlplane.cache.v3.SimpleCache;
import io.envoyproxy.controlplane.cache.v3.Snapshot;
import io.envoyproxy.controlplane.server.DiscoveryServerCallbacks;
import io.envoyproxy.controlplane.server.V3DiscoveryServer;
import io.envoyproxy.envoy.service.discovery.v3.DeltaDiscoveryRequest;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryRequest;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryResponse;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.GatewayRouteEvent;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.GatewayRouteEventType;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.RouteSnapshot;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.annotation.SimpleIamPortalGatewayAdaptorComponent;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.configuration.SimpleIamPortalGatewayAdaptorProperties;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.constant.SimpleIamPortalGatewayAdaptorStarterConstant;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 在 adaptor 进程内提供 Envoy xDS v3 ADS/SotW 控制面。
 */
@SimpleIamPortalGatewayAdaptorComponent
public class XdsSnapshotService implements XdsSnapshotPort, org.springframework.beans.factory.InitializingBean,
        org.springframework.beans.factory.DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(XdsSnapshotService.class);
    private static final String DEFAULT_NODE_GROUP = "default";

    private final AtomicReference<RouteSnapshot> pending = new AtomicReference<>();
    private final XdsAcknowledgementTracker acknowledgementTracker = new XdsAcknowledgementTracker();
    private final ConcurrentMap<Context, Long> streamIdsByContext = new ConcurrentHashMap<>();
    private final Set<Long> appliedStreamIds = ConcurrentHashMap.newKeySet();
    private final SimpleIamPortalGatewayAdaptorProperties properties;
    private final EnvoyXdsSnapshotFactory snapshotFactory;
    private final SimpleCache<String> snapshotCache;
    private final GatewayRouteSnapshotState state;
    private final GatewayEventPublisher eventPublisher;
    private final Server server;
    private volatile boolean running;

    public XdsSnapshotService(SimpleIamPortalGatewayAdaptorProperties properties,
                              EnvoyXdsSnapshotFactory snapshotFactory,
                              GatewayRouteSnapshotState state,
                              GatewayEventPublisher eventPublisher) {
        this.properties = properties;
        this.snapshotFactory = snapshotFactory;
        this.state = state;
        this.eventPublisher = eventPublisher;
        this.snapshotCache = new SimpleCache<>(node -> DEFAULT_NODE_GROUP);
        DiscoveryServerCallbacks callbacks = new DiscoveryServerCallbacks() {
            @Override
            public void onStreamOpen(long streamId, String typeUrl) {
                acknowledgementTracker.onStreamOpen(streamId, typeUrl);
                streamIdsByContext.put(Context.current(), streamId);
                LOGGER.debug("IAM Portal Gateway xDS stream 已打开，streamId={}, typeUrl={}", streamId, typeUrl);
            }

            @Override
            public void onStreamClose(long streamId, String typeUrl) {
                closeStream(streamId);
                LOGGER.debug("IAM Portal Gateway xDS stream 已关闭，streamId={}, typeUrl={}", streamId, typeUrl);
            }

            @Override
            public void onStreamCloseWithError(long streamId, String typeUrl, Throwable error) {
                closeStream(streamId);
                LOGGER.debug("IAM Portal Gateway xDS stream 异常关闭，streamId={}, typeUrl={}, errorType={}",
                        streamId, typeUrl, error == null ? null : error.getClass().getSimpleName());
            }

            @Override
            public void onV3StreamRequest(long streamId, DiscoveryRequest request) {
                onRequest(streamId, request);
            }

            @Override
            public void onV3StreamDeltaRequest(long streamId, DeltaDiscoveryRequest request) {
                acknowledgementTracker.onDeltaRequest(streamId);
                LOGGER.debug("IAM Portal Gateway xDS 收到未参与快照 ACK 的 Delta 请求，streamId={}, typeUrl={}",
                        streamId, request == null ? null : request.getTypeUrl());
            }

            @Override
            public void onV3StreamResponse(long streamId, DiscoveryRequest request,
                                           DiscoveryResponse response) {
                onResponse(streamId, request, response);
            }
        };
        V3DiscoveryServer discoveryServer = new V3DiscoveryServer(
                Collections.singletonList(callbacks), snapshotCache);
        this.server = createServer(discoveryServer);
    }

    private Server createServer(V3DiscoveryServer discoveryServer) {
        return NettyServerBuilder
                .forAddress(new InetSocketAddress(properties.getXdsBindAddress(), properties.getXdsPort()))
                .addService(withStreamLifecycle(discoveryServer.getAggregatedDiscoveryServiceImpl()))
                .addService(withStreamLifecycle(discoveryServer.getClusterDiscoveryServiceImpl()))
                .addService(withStreamLifecycle(discoveryServer.getEndpointDiscoveryServiceImpl()))
                .addService(withStreamLifecycle(discoveryServer.getListenerDiscoveryServiceImpl()))
                .addService(withStreamLifecycle(discoveryServer.getRouteDiscoveryServiceImpl()))
                .build();
    }

    private ServerServiceDefinition withStreamLifecycle(io.grpc.BindableService service) {
        return ServerInterceptors.intercept(service, new ServerInterceptor() {
            @Override
            public <RequestT, ResponseT> ServerCall.Listener<RequestT> interceptCall(
                    ServerCall<RequestT, ResponseT> call,
                    Metadata headers,
                    ServerCallHandler<RequestT, ResponseT> next) {
                Context streamContext = Context.current();
                ServerCall.Listener<RequestT> listener = next.startCall(call, headers);
                return new ForwardingServerCallListener.SimpleForwardingServerCallListener<RequestT>(listener) {
                    @Override
                    public void onCancel() {
                        try {
                            super.onCancel();
                        } finally {
                            closeCancelledStream(streamContext);
                        }
                    }
                };
            }
        });
    }

    private void closeCancelledStream(Context streamContext) {
        Long streamId = streamIdsByContext.remove(streamContext);
        if (streamId != null) {
            closeStream(streamId);
            LOGGER.debug("IAM Portal Gateway xDS stream 已取消并清理状态，streamId={}", streamId);
        }
    }

    private void closeStream(long streamId) {
        acknowledgementTracker.onStreamClose(streamId);
        appliedStreamIds.remove(streamId);
        for (Map.Entry<Context, Long> entry : streamIdsByContext.entrySet()) {
            if (Long.valueOf(streamId).equals(entry.getValue())) {
                streamIdsByContext.remove(entry.getKey(), streamId);
            }
        }
    }

    @Override
    public void afterPropertiesSet() {
        try {
            server.start();
            running = true;
            LOGGER.info("IAM Portal Gateway xDS 控制面已启动，bindAddress={}, port={}",
                    properties.getXdsBindAddress(), properties.getXdsPort());
        } catch (IOException ex) {
            running = false;
            LOGGER.error("IAM Portal Gateway xDS 控制面启动失败，bindAddress={}, port={}",
                    properties.getXdsBindAddress(), properties.getXdsPort(), ex);
            throw new BeanCreationException("IAM Portal Gateway xDS 控制面启动失败", ex);
        }
    }

    @Override
    public void destroy() {
        running = false;
        server.shutdownNow();
        try {
            if (!server.awaitTermination(SimpleIamPortalGatewayAdaptorStarterConstant.XDS_SHUTDOWN_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS)) {
                LOGGER.warn("IAM Portal Gateway xDS 控制面未能在限定时间内终止");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.warn("等待 IAM Portal Gateway xDS 控制面终止时线程被中断");
        } finally {
            acknowledgementTracker.clear();
            streamIdsByContext.clear();
            appliedStreamIds.clear();
            LOGGER.info("IAM Portal Gateway xDS 控制面已停止");
        }
    }

    @Override
    public synchronized boolean publish(RouteSnapshot snapshot) {
        if (snapshot == null || !running) {
            return false;
        }
        RouteSnapshot previous = pending.get();
        if (previous != null && previous.getSnapshotVersion().equals(snapshot.getSnapshotVersion())) {
            return true;
        }
        Snapshot xdsSnapshot = snapshotFactory.build(snapshot);
        if (xdsSnapshot == null) {
            return false;
        }
        try {
            xdsSnapshot.ensureConsistent();
        } catch (SnapshotConsistencyException ex) {
            LOGGER.warn("IAM Portal Gateway xDS 快照依赖不一致，拒绝发布，version={}",
                    snapshot.getSnapshotVersion());
            return false;
        }
        pending.set(snapshot);
        appliedStreamIds.clear();
        acknowledgementTracker.onSnapshotPublished(snapshot);
        snapshotCache.setSnapshot(DEFAULT_NODE_GROUP, xdsSnapshot);
        LOGGER.debug("IAM Portal Gateway xDS 快照已发布，version={}, routeCount={}",
                snapshot.getSnapshotVersion(), snapshot.getRoutes().size());
        return true;
    }

    public RouteSnapshot pending() {
        return pending.get();
    }

    /**
     * 返回 ADS/SotW 控制面是否已监听。
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 至少一个 Envoy ADS/SotW stream 已完整确认最新快照；不代表其他副本均已收敛。
     */
    public boolean hasAppliedLatestSnapshot() {
        RouteSnapshot latest = pending.get();
        RouteSnapshot applied = state.current();
        return latest != null && applied != null && !appliedStreamIds.isEmpty()
                && latest.getSnapshotVersion().equals(applied.getSnapshotVersion());
    }

    private void onResponse(long streamId, DiscoveryRequest request, DiscoveryResponse response) {
        if (response == null || request == null) {
            return;
        }
        String nodeId = request.hasNode() ? request.getNode().getId() : null;
        boolean tracked = acknowledgementTracker.onResponse(streamId, nodeId, response.getNonce(),
                response.getVersionInfo(), response.getTypeUrl());
        if (tracked) {
            LOGGER.debug("IAM Portal Gateway xDS response 已关联，streamId={}, version={}, typeUrl={}",
                    streamId, response.getVersionInfo(), response.getTypeUrl());
        }
    }

    private void onRequest(long streamId, DiscoveryRequest request) {
        if (request == null) {
            return;
        }
        String nodeId = request.hasNode() ? request.getNode().getId() : null;
        String typeUrl = request.getTypeUrl();
        boolean nack = request.hasErrorDetail();
        XdsAcknowledgementTracker.AcknowledgementResult result = acknowledgementTracker.onRequest(
                streamId, nodeId, request.getResponseNonce(), typeUrl, nack);
        if (result.getOutcome() == XdsAcknowledgementTracker.Outcome.IGNORED) {
            return;
        }
        RouteSnapshot responseSnapshot = result.getSnapshot();
        if (result.getOutcome() == XdsAcknowledgementTracker.Outcome.NACK) {
            String reason = "envoy-nack-" + request.getErrorDetail().getCode();
            LOGGER.warn("IAM Portal Gateway xDS 收到 NACK，streamId={}, version={}, typeUrl={}, reasonCode={}",
                    streamId, result.getVersion(), result.getTypeUrl(), reason);
            state.recordFailure(reason);
            publishEvent(GatewayRouteEventType.XDS_NACK, result.getVersion(), reason, responseSnapshot);
            return;
        }

        LOGGER.debug("IAM Portal Gateway xDS 收到 ACK，streamId={}, version={}, typeUrl={}, outcome={}",
                streamId, result.getVersion(), result.getTypeUrl(), result.getOutcome());
        publishEvent(GatewayRouteEventType.XDS_ACK, result.getVersion(), null, responseSnapshot);
        if (result.getOutcome() == XdsAcknowledgementTracker.Outcome.APPLIED) {
            synchronized (this) {
                if (pending.get() != responseSnapshot) {
                    return;
                }
                state.replace(responseSnapshot);
                appliedStreamIds.add(streamId);
            }
            LOGGER.debug("IAM Portal Gateway xDS 快照已由单个 Envoy stream 完整确认，streamId={}, version={}",
                    streamId, result.getVersion());
            publishEvent(GatewayRouteEventType.SNAPSHOT_APPLIED, result.getVersion(), null, responseSnapshot);
        }
    }

    private void publishEvent(GatewayRouteEventType type, String snapshotVersion, String reason) {
        publishEvent(type, snapshotVersion, reason, pending.get());
    }

    private void publishEvent(GatewayRouteEventType type, String snapshotVersion, String reason,
                              RouteSnapshot snapshot) {
        eventPublisher.publish(GatewayRouteEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .type(type)
                .catalogRevision(snapshot == null ? null : snapshot.getCatalogRevision())
                .snapshotVersion(snapshotVersion)
                .routeCount(snapshot == null ? 0 : snapshot.getRoutes().size())
                .targetDigest(snapshot == null ? null : snapshot.getTargetDigest())
                .reasonCode(reason)
                .occurredAt(Instant.now())
                .build());
    }

}
