package io.github.surezzzzzz.sdk.elasticsearch.route.support;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ServerVersion;

/**
 * Elasticsearch 版本兼容 helper
 *
 * @author surezzzzzz
 */
public final class ElasticsearchVersionHelper {

    private ElasticsearchVersionHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static ServerVersion effectiveVersion(ClusterInfo clusterInfo) {
        return clusterInfo == null ? null : clusterInfo.getEffectiveVersion();
    }

    public static boolean isKnown(ClusterInfo clusterInfo) {
        return effectiveVersion(clusterInfo) != null;
    }

    public static boolean isUnknown(ClusterInfo clusterInfo) {
        return !isKnown(clusterInfo);
    }

    public static boolean isEs6(ClusterInfo clusterInfo) {
        ServerVersion version = effectiveVersion(clusterInfo);
        return version != null && version.isEs6();
    }

    public static boolean isEs7(ClusterInfo clusterInfo) {
        ServerVersion version = effectiveVersion(clusterInfo);
        return version != null && version.isEs7();
    }

    public static boolean isSameMajor(ClusterInfo clusterInfo, int major) {
        ServerVersion version = effectiveVersion(clusterInfo);
        return version != null && version.isSameMajor(major);
    }

    public static boolean isAtLeast(ClusterInfo clusterInfo, int major) {
        ServerVersion version = effectiveVersion(clusterInfo);
        return version != null && version.isAtLeast(major);
    }

    public static boolean isAtLeast(ClusterInfo clusterInfo, int major, int minor) {
        ServerVersion version = effectiveVersion(clusterInfo);
        return version != null && version.isAtLeast(major, minor);
    }

    public static boolean isBefore(ClusterInfo clusterInfo, int major) {
        ServerVersion version = effectiveVersion(clusterInfo);
        return version != null && version.isBefore(major);
    }

    public static boolean isBefore(ClusterInfo clusterInfo, int major, int minor) {
        ServerVersion version = effectiveVersion(clusterInfo);
        return version != null && version.isBefore(major, minor);
    }

    public static boolean supportsPointInTime(ClusterInfo clusterInfo) {
        return isAtLeast(clusterInfo, 7, 10)
                && ElasticsearchReflectionHelper.isClassPresent(SimpleElasticsearchRouteConstant.CLASS_POINT_IN_TIME_BUILDER);
    }

    public static boolean supportsCompositeAggregation(ClusterInfo clusterInfo) {
        ServerVersion version = effectiveVersion(clusterInfo);
        if (version == null) {
            return false;
        }
        return version.isAtLeast(7) || version.isAtLeast(6, 1);
    }

    public static boolean supportsStableCompositeAfterKey(ClusterInfo clusterInfo) {
        return isAtLeast(clusterInfo, 7);
    }

    public static boolean supportsCompositeMissingBucket(ClusterInfo clusterInfo) {
        return isAtLeast(clusterInfo, 7);
    }
}
