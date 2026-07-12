package io.github.surezzzzzz.sdk.redis.route.support;

import io.github.surezzzzzz.sdk.redis.route.model.RedisServerInfo;

/**
 * Redis Server 版本判断 Helper（无状态）
 *
 * @author surezzzzzz
 */
public final class RedisServerVersionHelper {

    private RedisServerVersionHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 版本信息是否已知（探测成功）。
     *
     * @param info Redis Server 信息
     * @return true 表示版本信息已知，false 表示版本信息未知
     */
    public static boolean isKnown(RedisServerInfo info) {
        return info != null && info.isKnown() && info.getVersion() != null;
    }

    /**
     * 版本信息是否未知（探测失败或未探测）。
     *
     * @param info Redis Server 信息
     * @return true 表示版本信息未知，false 表示版本信息已知
     */
    public static boolean isUnknown(RedisServerInfo info) {
        return !isKnown(info);
    }

    /**
     * Server 版本是否大于等于 targetMajor.targetMinor。
     * info 为 null 或 known=false 时返回 false。
     *
     * @param info        Redis Server 信息
     * @param targetMajor 目标 major
     * @param targetMinor 目标 minor
     * @return true 表示版本满足要求，false 表示不满足或版本未知
     */
    public static boolean isAtLeast(RedisServerInfo info, int targetMajor, int targetMinor) {
        if (isUnknown(info)) {
            return false;
        }
        return info.getVersion().isAtLeast(targetMajor, targetMinor);
    }

    /**
     * Server 版本是否大于等于 targetMajor.targetMinor.targetPatch。
     * info 为 null 或 known=false 时返回 false。
     *
     * @param info        Redis Server 信息
     * @param targetMajor 目标 major
     * @param targetMinor 目标 minor
     * @param targetPatch 目标 patch
     * @return true 表示版本满足要求，false 表示不满足或版本未知
     */
    public static boolean isAtLeast(RedisServerInfo info, int targetMajor, int targetMinor, int targetPatch) {
        if (isUnknown(info)) {
            return false;
        }
        return info.getVersion().isAtLeast(targetMajor, targetMinor, targetPatch);
    }

    /**
     * Server 版本是否严格小于 targetMajor.targetMinor。
     * info 为 null 或 known=false 时返回 false（保守策略：未知时不允许降级判断）。
     *
     * @param info        Redis Server 信息
     * @param targetMajor 目标 major
     * @param targetMinor 目标 minor
     * @return true 表示版本严格小于目标版本，false 表示不小于或版本未知
     */
    public static boolean isBefore(RedisServerInfo info, int targetMajor, int targetMinor) {
        if (isUnknown(info)) {
            return false;
        }
        return info.getVersion().isBefore(targetMajor, targetMinor);
    }

    /**
     * Server 版本是否严格小于 targetMajor.targetMinor.targetPatch。
     * info 为 null 或 known=false 时返回 false。
     *
     * @param info        Redis Server 信息
     * @param targetMajor 目标 major
     * @param targetMinor 目标 minor
     * @param targetPatch 目标 patch
     * @return true 表示版本严格小于目标版本，false 表示不小于或版本未知
     */
    public static boolean isBefore(RedisServerInfo info, int targetMajor, int targetMinor, int targetPatch) {
        if (isUnknown(info)) {
            return false;
        }
        return info.getVersion().isBefore(targetMajor, targetMinor, targetPatch);
    }
}
