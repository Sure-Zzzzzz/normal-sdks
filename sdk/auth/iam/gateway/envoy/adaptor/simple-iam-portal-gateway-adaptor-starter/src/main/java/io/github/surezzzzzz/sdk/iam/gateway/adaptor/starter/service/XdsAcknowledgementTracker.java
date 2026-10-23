package io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.service;

import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.RouteSnapshot;

import java.util.*;

/**
 * 按 ADS stream 隔离 Envoy 响应 nonce 和快照 ACK。
 */
final class XdsAcknowledgementTracker {

    private static final Set<String> REQUIRED_TYPE_URLS = new HashSet<>(Arrays.asList(
            "type.googleapis.com/envoy.config.cluster.v3.Cluster",
            "type.googleapis.com/envoy.config.endpoint.v3.ClusterLoadAssignment",
            "type.googleapis.com/envoy.config.listener.v3.Listener",
            "type.googleapis.com/envoy.config.route.v3.RouteConfiguration"));

    private final Map<Long, StreamState> streams = new HashMap<>();
    private final Map<StreamNonceKey, ResponseState> responses = new HashMap<>();
    private final Map<AcknowledgementKey, AcknowledgementState> acknowledgements = new HashMap<>();
    private RouteSnapshot pendingSnapshot;

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    synchronized void onStreamOpen(long streamId, String typeUrl) {
        clearStream(streamId);
        streams.put(streamId, new StreamState(typeUrl == null || typeUrl.isEmpty()));
    }

    synchronized void onDeltaRequest(long streamId) {
        StreamState stream = streams.get(streamId);
        if (stream != null) {
            stream.delta = true;
            clearResponses(streamId, null);
            clearAcknowledgements(streamId);
        }
    }

    synchronized boolean onResponse(long streamId, String nodeId, String nonce,
                                    String version, String typeUrl) {
        StreamState stream = bindNode(streamId, nodeId);
        if (!isTrackable(stream) || isEmpty(stream.nodeId) || isEmpty(nonce) || isEmpty(version)
                || isEmpty(typeUrl) || !REQUIRED_TYPE_URLS.contains(typeUrl)
                || pendingSnapshot == null || !pendingSnapshot.getSnapshotVersion().equals(version)) {
            return false;
        }
        responses.put(new StreamNonceKey(streamId, nonce), new ResponseState(
                stream.nodeId, version, typeUrl, pendingSnapshot));
        return true;
    }

    synchronized AcknowledgementResult onRequest(long streamId, String nodeId, String nonce,
                                                 String typeUrl, boolean nack) {
        StreamState stream = bindNode(streamId, nodeId);
        if (!isTrackable(stream) || isEmpty(stream.nodeId) || isEmpty(nonce) || isEmpty(typeUrl)) {
            return AcknowledgementResult.ignored();
        }
        StreamNonceKey responseKey = new StreamNonceKey(streamId, nonce);
        ResponseState response = responses.get(responseKey);
        if (response == null || !typeUrl.equals(response.typeUrl)) {
            return AcknowledgementResult.ignored();
        }
        responses.remove(responseKey);
        if (pendingSnapshot == null || !pendingSnapshot.getSnapshotVersion().equals(response.version)) {
            return AcknowledgementResult.ignored();
        }

        AcknowledgementKey acknowledgementKey = new AcknowledgementKey(
                response.nodeId, streamId, response.version);
        AcknowledgementState acknowledgement = acknowledgements.get(acknowledgementKey);
        if (acknowledgement == null) {
            acknowledgement = new AcknowledgementState(response.snapshot);
            acknowledgements.put(acknowledgementKey, acknowledgement);
        }
        if (nack) {
            acknowledgement.failed = true;
            clearResponses(streamId, response.version);
            return AcknowledgementResult.nack(response.nodeId, streamId, response.version,
                    response.typeUrl, response.snapshot);
        }
        if (acknowledgement.failed) {
            return AcknowledgementResult.ignored();
        }

        acknowledgement.types.add(response.typeUrl);
        if (acknowledgement.types.containsAll(REQUIRED_TYPE_URLS)) {
            acknowledgements.remove(acknowledgementKey);
            return AcknowledgementResult.applied(response.nodeId, streamId, response.version,
                    response.typeUrl, acknowledgement.snapshot);
        }
        return AcknowledgementResult.acknowledged(response.nodeId, streamId, response.version,
                response.typeUrl, acknowledgement.snapshot);
    }

    synchronized void onStreamClose(long streamId) {
        clearStream(streamId);
    }

    synchronized void onSnapshotPublished(RouteSnapshot snapshot) {
        pendingSnapshot = snapshot;
        responses.clear();
        acknowledgements.clear();
    }

    synchronized void clear() {
        streams.clear();
        responses.clear();
        acknowledgements.clear();
        pendingSnapshot = null;
    }

    private StreamState bindNode(long streamId, String nodeId) {
        StreamState stream = streams.get(streamId);
        if (stream == null) {
            return null;
        }
        if (!isEmpty(nodeId)) {
            if (!isEmpty(stream.nodeId) && !stream.nodeId.equals(nodeId)) {
                clearResponses(streamId, null);
                clearAcknowledgements(streamId);
            }
            stream.nodeId = nodeId;
        }
        return stream;
    }

    private boolean isTrackable(StreamState stream) {
        return stream != null && stream.ads && !stream.delta;
    }

    private void clearStream(long streamId) {
        streams.remove(streamId);
        clearResponses(streamId, null);
        clearAcknowledgements(streamId);
    }

    private void clearResponses(long streamId, String version) {
        Iterator<Map.Entry<StreamNonceKey, ResponseState>> iterator = responses.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<StreamNonceKey, ResponseState> entry = iterator.next();
            if (entry.getKey().streamId == streamId
                    && (version == null || version.equals(entry.getValue().version))) {
                iterator.remove();
            }
        }
    }

    private void clearAcknowledgements(long streamId) {
        Iterator<AcknowledgementKey> iterator = acknowledgements.keySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().streamId == streamId) {
                iterator.remove();
            }
        }
    }

    enum Outcome {
        IGNORED,
        ACKNOWLEDGED,
        NACK,
        APPLIED
    }

    static final class AcknowledgementResult {

        private final Outcome outcome;
        private final String nodeId;
        private final long streamId;
        private final String version;
        private final String typeUrl;
        private final RouteSnapshot snapshot;

        private AcknowledgementResult(Outcome outcome, String nodeId, long streamId,
                                      String version, String typeUrl, RouteSnapshot snapshot) {
            this.outcome = outcome;
            this.nodeId = nodeId;
            this.streamId = streamId;
            this.version = version;
            this.typeUrl = typeUrl;
            this.snapshot = snapshot;
        }

        static AcknowledgementResult ignored() {
            return new AcknowledgementResult(Outcome.IGNORED, null, -1L, null, null, null);
        }

        static AcknowledgementResult acknowledged(String nodeId, long streamId, String version,
                                                  String typeUrl, RouteSnapshot snapshot) {
            return new AcknowledgementResult(Outcome.ACKNOWLEDGED, nodeId, streamId, version,
                    typeUrl, snapshot);
        }

        static AcknowledgementResult nack(String nodeId, long streamId, String version,
                                          String typeUrl, RouteSnapshot snapshot) {
            return new AcknowledgementResult(Outcome.NACK, nodeId, streamId, version,
                    typeUrl, snapshot);
        }

        static AcknowledgementResult applied(String nodeId, long streamId, String version,
                                             String typeUrl, RouteSnapshot snapshot) {
            return new AcknowledgementResult(Outcome.APPLIED, nodeId, streamId, version,
                    typeUrl, snapshot);
        }

        Outcome getOutcome() {
            return outcome;
        }

        String getNodeId() {
            return nodeId;
        }

        long getStreamId() {
            return streamId;
        }

        String getVersion() {
            return version;
        }

        String getTypeUrl() {
            return typeUrl;
        }

        RouteSnapshot getSnapshot() {
            return snapshot;
        }
    }

    private static final class StreamState {

        private final boolean ads;
        private boolean delta;
        private String nodeId;

        private StreamState(boolean ads) {
            this.ads = ads;
        }
    }

    private static final class StreamNonceKey {

        private final long streamId;
        private final String nonce;

        private StreamNonceKey(long streamId, String nonce) {
            this.streamId = streamId;
            this.nonce = nonce;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof StreamNonceKey)) {
                return false;
            }
            StreamNonceKey that = (StreamNonceKey) other;
            return streamId == that.streamId && nonce.equals(that.nonce);
        }

        @Override
        public int hashCode() {
            int result = Long.valueOf(streamId).hashCode();
            return 31 * result + nonce.hashCode();
        }
    }

    private static final class AcknowledgementKey {

        private final String nodeId;
        private final long streamId;
        private final String version;

        private AcknowledgementKey(String nodeId, long streamId, String version) {
            this.nodeId = nodeId;
            this.streamId = streamId;
            this.version = version;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof AcknowledgementKey)) {
                return false;
            }
            AcknowledgementKey that = (AcknowledgementKey) other;
            return streamId == that.streamId && nodeId.equals(that.nodeId) && version.equals(that.version);
        }

        @Override
        public int hashCode() {
            int result = nodeId.hashCode();
            result = 31 * result + Long.valueOf(streamId).hashCode();
            return 31 * result + version.hashCode();
        }
    }

    private static final class ResponseState {

        private final String nodeId;
        private final String version;
        private final String typeUrl;
        private final RouteSnapshot snapshot;

        private ResponseState(String nodeId, String version, String typeUrl, RouteSnapshot snapshot) {
            this.nodeId = nodeId;
            this.version = version;
            this.typeUrl = typeUrl;
            this.snapshot = snapshot;
        }
    }

    private static final class AcknowledgementState {

        private final RouteSnapshot snapshot;
        private final Set<String> types = new HashSet<>();
        private boolean failed;

        private AcknowledgementState(RouteSnapshot snapshot) {
            this.snapshot = snapshot;
        }
    }
}
