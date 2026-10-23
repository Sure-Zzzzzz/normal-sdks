package io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.test.cases;

import com.google.rpc.Status;
import io.envoyproxy.envoy.config.core.v3.Node;
import io.envoyproxy.envoy.service.discovery.v3.AggregatedDiscoveryServiceGrpc;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryRequest;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryResponse;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.*;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.configuration.SimpleIamPortalGatewayAdaptorProperties;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.service.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 xDS nonce 和全量 ACK 在真实 ADS/SotW gRPC stream 上按 Envoy 副本隔离。
 */
@Slf4j
class XdsSnapshotServiceTest {

    private static final List<String> REQUIRED_TYPE_URLS = Arrays.asList(
            "type.googleapis.com/envoy.config.cluster.v3.Cluster",
            "type.googleapis.com/envoy.config.endpoint.v3.ClusterLoadAssignment",
            "type.googleapis.com/envoy.config.route.v3.RouteConfiguration",
            "type.googleapis.com/envoy.config.listener.v3.Listener");

    private TestFixture fixture;

    @AfterEach
    void closeFixture() {
        if (fixture != null) {
            fixture.close();
            fixture = null;
        }
    }

    @Test
    void doesNotCombineAcknowledgementsFromDifferentEnvoyStreams() throws Exception {
        fixture = new TestFixture();
        fixture.publish("snapshot-1");
        StreamSession first = fixture.openStream("envoy-one");
        StreamSession second = fixture.openStream("envoy-two");

        DiscoveryResponse firstCluster = first.request(REQUIRED_TYPE_URLS.get(0));
        DiscoveryResponse firstEndpoint = first.request(REQUIRED_TYPE_URLS.get(1));
        DiscoveryResponse secondRoute = second.request(REQUIRED_TYPE_URLS.get(2));
        DiscoveryResponse secondListener = second.request(REQUIRED_TYPE_URLS.get(3));

        first.acknowledge(firstCluster);
        first.acknowledge(firstEndpoint);
        second.acknowledge(secondRoute);
        second.acknowledge(secondListener);

        log.info("两个 Envoy stream 各确认部分资源后仍未完成快照，firstNode={}, secondNode={}",
                "envoy-one", "envoy-two");
        assertNull(fixture.state.current());
        assertFalse(fixture.health.isReady());

        first.acknowledge(first.request(REQUIRED_TYPE_URLS.get(2)));
        first.acknowledge(first.request(REQUIRED_TYPE_URLS.get(3)));

        assertTrue(fixture.awaitEvent(GatewayRouteEventType.SNAPSHOT_APPLIED));
        assertEquals("snapshot-1", fixture.state.current().getSnapshotVersion());
        assertTrue(fixture.health.isReady());
    }

    @Test
    void nackedSnapshotStaysUnreadyUntilANewVersionIsFullyAcknowledged() throws Exception {
        fixture = new TestFixture();
        fixture.publish("snapshot-1");
        StreamSession first = fixture.openStream("envoy-one");

        first.reject(first.request(REQUIRED_TYPE_URLS.get(0)), 3);
        assertTrue(fixture.awaitEvent(GatewayRouteEventType.XDS_NACK));
        for (int index = 1; index < REQUIRED_TYPE_URLS.size(); index++) {
            first.acknowledge(first.request(REQUIRED_TYPE_URLS.get(index)));
        }

        log.info("Envoy NACK 后同版本其余 ACK 不得将快照标记为已应用");
        assertNull(fixture.state.current());
        assertEquals("envoy-nack-3", fixture.state.failureReason());
        assertFalse(fixture.health.isReady());

        fixture.publish("snapshot-2");
        assertFalse(fixture.health.isReady());
        StreamSession second = fixture.openStream("envoy-two");
        for (String typeUrl : REQUIRED_TYPE_URLS) {
            second.acknowledge(second.request(typeUrl));
        }

        assertTrue(fixture.awaitEvent(GatewayRouteEventType.SNAPSHOT_APPLIED));
        assertEquals("snapshot-2", fixture.state.current().getSnapshotVersion());
        assertNull(fixture.state.failureReason());
        assertTrue(fixture.health.isReady());
    }

    @Test
    void keepsTheLastAppliedSnapshotReadyDuringConfiguredStaleWindow() throws Exception {
        fixture = new TestFixture();
        fixture.properties.setMaxStaleMillis(10_000L);
        fixture.publish("snapshot-1");
        StreamSession first = fixture.openStream("envoy-one");
        for (String typeUrl : REQUIRED_TYPE_URLS) {
            first.acknowledge(first.request(typeUrl));
        }
        assertTrue(fixture.awaitEvent(GatewayRouteEventType.SNAPSHOT_APPLIED));
        assertTrue(fixture.health.isReady());

        fixture.publish("snapshot-2");
        StreamSession second = fixture.openStream("envoy-two");
        second.reject(second.request(REQUIRED_TYPE_URLS.get(0)), 3);

        log.info("新快照 NACK 时保留已确认快照: staleReady={}", fixture.health.isReady());
        assertTrue(fixture.awaitEvent(GatewayRouteEventType.XDS_NACK));
        assertTrue(fixture.health.isReady());
        assertEquals("snapshot-1", fixture.state.current().getSnapshotVersion());
    }

    @Test
    void becomesUnreadyWhenTheOnlyAppliedStreamCloses() throws Exception {
        fixture = new TestFixture();
        fixture.publish("snapshot-1");
        StreamSession stream = fixture.openStream("envoy-one");
        for (String typeUrl : REQUIRED_TYPE_URLS) {
            stream.acknowledge(stream.request(typeUrl));
        }
        assertTrue(fixture.awaitEvent(GatewayRouteEventType.SNAPSHOT_APPLIED));
        assertTrue(fixture.health.isReady());

        stream.cancel();
        assertTrue(stream.awaitClosed());
        assertTrue(fixture.awaitNoTrackedState());
        log.info("唯一已确认 xDS stream 关闭后 readiness 变为未就绪");
        assertFalse(fixture.health.isReady());
    }

    @Test
    void cancelledAdsStreamClearsItsTrackedState() throws Exception {
        fixture = new TestFixture();
        fixture.publish("snapshot-1");
        StreamSession stream = fixture.openStream("envoy-one");
        stream.request(REQUIRED_TYPE_URLS.get(0));

        assertTrue(fixture.trackedStateCount() > 0);
        stream.cancel();

        assertTrue(stream.awaitClosed());
        assertTrue(fixture.awaitNoTrackedState());
    }

    @Test
    void destroyTerminatesTheServerWithAnActiveAdsStream() throws Exception {
        fixture = new TestFixture();
        fixture.publish("snapshot-1");
        StreamSession stream = fixture.openStream("envoy-one");
        stream.request(REQUIRED_TYPE_URLS.get(0));

        fixture.service.destroy();

        assertFalse(fixture.service.isRunning());
        assertTrue(stream.awaitClosed());
        assertTrue(fixture.isXdsPortAvailable());
    }

    private static final class TestFixture implements AutoCloseable {

        private final SimpleIamPortalGatewayAdaptorProperties properties =
                new SimpleIamPortalGatewayAdaptorProperties();
        private final GatewayRouteSnapshotState state = new GatewayRouteSnapshotState();
        private final BlockingQueue<GatewayRouteEvent> events = new LinkedBlockingQueue<>();
        private final XdsSnapshotService service;
        private final GatewayHealthEndpoint health;
        private ManagedChannel channel;

        private TestFixture() throws IOException {
            properties.setXdsBindAddress("127.0.0.1");
            properties.setXdsPort(availablePort());
            EnvoyXdsSnapshotFactory snapshotFactory = new EnvoyXdsSnapshotFactory(properties);
            service = new XdsSnapshotService(properties, snapshotFactory, state,
                    new GatewayEventPublisher(event -> events.offer((GatewayRouteEvent) event)));
            service.afterPropertiesSet();
            channel = ManagedChannelBuilder.forAddress(properties.getXdsBindAddress(), properties.getXdsPort())
                    .usePlaintext()
                    .build();
            health = new GatewayHealthEndpoint(state, properties, service);
        }

        private void publish(String version) {
            assertTrue(service.publish(snapshot(version)));
        }

        private StreamSession openStream(String nodeId) {
            return new StreamSession(channel, nodeId);
        }

        private boolean awaitEvent(GatewayRouteEventType type) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
            while (System.nanoTime() < deadline) {
                GatewayRouteEvent event = events.poll(100L, TimeUnit.MILLISECONDS);
                if (event != null && event.getType() == type) {
                    return true;
                }
            }
            return false;
        }

        private int trackedStateCount() {
            Object tracker = ReflectionTestUtils.getField(service, "acknowledgementTracker");
            Map<?, ?> streamIdsByContext = (Map<?, ?>) ReflectionTestUtils.getField(service, "streamIdsByContext");
            synchronized (tracker) {
                Map<?, ?> streams = (Map<?, ?>) ReflectionTestUtils.getField(tracker, "streams");
                Map<?, ?> responses = (Map<?, ?>) ReflectionTestUtils.getField(tracker, "responses");
                Map<?, ?> acknowledgements = (Map<?, ?>) ReflectionTestUtils.getField(tracker, "acknowledgements");
                return streamIdsByContext.size() + streams.size() + responses.size() + acknowledgements.size();
            }
        }

        private boolean awaitNoTrackedState() throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
            while (System.nanoTime() < deadline) {
                if (trackedStateCount() == 0) {
                    return true;
                }
                TimeUnit.MILLISECONDS.sleep(25L);
            }
            return trackedStateCount() == 0;
        }

        private boolean isXdsPortAvailable() {
            try (ServerSocket socket = new ServerSocket(properties.getXdsPort())) {
                return true;
            } catch (IOException ex) {
                return false;
            }
        }

        @Override
        public void close() {
            if (channel != null) {
                channel.shutdownNow();
                try {
                    channel.awaitTermination(5L, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                channel = null;
            }
            service.destroy();
        }

        private int availablePort() throws IOException {
            try (ServerSocket socket = new ServerSocket(0)) {
                return socket.getLocalPort();
            }
        }

        private RouteSnapshot snapshot(String version) {
            TrustedApplicationRoute route = TrustedApplicationRoute.builder()
                    .applicationCode("sample")
                    .applicationName("Sample")
                    .kind(RouteKind.PAGE)
                    .publicPath("/app/sample")
                    .upstreamPath("/")
                    .target(ResolvedTarget.builder().host("sample.internal").ip("192.0.2.10")
                            .scheme("http").port(80).build())
                    .upstreamHost("sample.internal")
                    .configVersion(1L)
                    .forwardAuthorization("REQUEST")
                    .build();
            return RouteSnapshot.builder()
                    .catalogRevision(1L)
                    .policyVersion("policy")
                    .snapshotVersion(version)
                    .targetDigest("digest")
                    .routes(Arrays.asList(route))
                    .build();
        }
    }

    private static final class StreamSession {

        private final String nodeId;
        private final BlockingQueue<DiscoveryResponse> responses = new LinkedBlockingQueue<>();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final CountDownLatch closed = new CountDownLatch(1);
        private final StreamObserver<DiscoveryRequest> requests;
        private final AtomicReference<ClientCallStreamObserver<DiscoveryRequest>> call = new AtomicReference<>();

        private StreamSession(ManagedChannel channel, String nodeId) {
            this.nodeId = nodeId;
            ClientResponseObserver<DiscoveryRequest, DiscoveryResponse> responseObserver =
                    new ClientResponseObserver<DiscoveryRequest, DiscoveryResponse>() {
                        @Override
                        public void beforeStart(ClientCallStreamObserver<DiscoveryRequest> requestStream) {
                            call.set(requestStream);
                        }

                        @Override
                        public void onNext(DiscoveryResponse response) {
                            responses.offer(response);
                        }

                        @Override
                        public void onError(Throwable error) {
                            failure.set(error);
                            closed.countDown();
                        }

                        @Override
                        public void onCompleted() {
                            closed.countDown();
                        }
                    };
            requests = AggregatedDiscoveryServiceGrpc.newStub(channel)
                    .streamAggregatedResources(responseObserver);
        }

        private DiscoveryResponse request(String typeUrl) throws InterruptedException {
            requests.onNext(DiscoveryRequest.newBuilder()
                    .setNode(Node.newBuilder().setId(nodeId).build())
                    .setTypeUrl(typeUrl)
                    .build());
            return nextResponse(typeUrl);
        }

        private void acknowledge(DiscoveryResponse response) {
            requests.onNext(acknowledgement(response, false, 0));
        }

        private void reject(DiscoveryResponse response, int code) {
            requests.onNext(acknowledgement(response, true, code));
        }

        private void cancel() {
            ClientCallStreamObserver<DiscoveryRequest> requestStream = call.get();
            assertNotNull(requestStream);
            requestStream.cancel("test cancellation", null);
        }

        private boolean awaitClosed() throws InterruptedException {
            return closed.await(5L, TimeUnit.SECONDS);
        }

        private DiscoveryRequest acknowledgement(DiscoveryResponse response, boolean nack, int code) {
            DiscoveryRequest.Builder request = DiscoveryRequest.newBuilder()
                    .setTypeUrl(response.getTypeUrl())
                    .setVersionInfo(response.getVersionInfo())
                    .setResponseNonce(response.getNonce());
            if (nack) {
                request.setErrorDetail(Status.newBuilder().setCode(code).build());
            }
            return request.build();
        }

        private DiscoveryResponse nextResponse(String typeUrl) throws InterruptedException {
            DiscoveryResponse response = responses.poll(10L, TimeUnit.SECONDS);
            if (response == null) {
                Throwable error = failure.get();
                assertNull(error, "xDS stream failed before returning " + typeUrl);
            }
            assertNotNull(response, "xDS stream timed out waiting for " + typeUrl);
            assertEquals(typeUrl, response.getTypeUrl());
            return response;
        }
    }
}
