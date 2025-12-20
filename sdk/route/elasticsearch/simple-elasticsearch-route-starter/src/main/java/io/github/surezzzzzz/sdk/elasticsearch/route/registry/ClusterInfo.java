package io.github.surezzzzzz.sdk.elasticsearch.route.registry;

import lombok.Getter;
import lombok.ToString;

/**
 * ES 集群信息（按 datasource 维度）
 *
 * @author surezzzzzz
 */
@Getter
@ToString
public class ClusterInfo {

    private final String datasourceKey;
    private final ServerVersion configuredVersion;
    private final ServerVersion detectedVersion;
    private final ServerVersion effectiveVersion;
    private final boolean versionMismatch;
    private final String detectError;
    private final Long detectedAtMillis;

    private ClusterInfo(String datasourceKey,
                        ServerVersion configuredVersion,
                        ServerVersion detectedVersion,
                        String detectError,
                        Long detectedAtMillis) {
        this.datasourceKey = datasourceKey;
        this.configuredVersion = configuredVersion;
        this.detectedVersion = detectedVersion;
        this.effectiveVersion = configuredVersion != null ? configuredVersion : detectedVersion;
        this.versionMismatch = configuredVersion != null
                && detectedVersion != null
                && !configuredVersion.equals(detectedVersion);
        this.detectError = detectError;
        this.detectedAtMillis = detectedAtMillis;
    }

    public static ClusterInfo initial(String datasourceKey, ServerVersion configuredVersion) {
        return new ClusterInfo(datasourceKey, configuredVersion, null, null, null);
    }

    public ClusterInfo withDetected(ServerVersion detectedVersion, Long detectedAtMillis) {
        return new ClusterInfo(datasourceKey, configuredVersion, detectedVersion, null, detectedAtMillis);
    }

    public ClusterInfo withDetectError(String detectError, Long detectedAtMillis) {
        return new ClusterInfo(datasourceKey, configuredVersion, null, detectError, detectedAtMillis);
    }
}

