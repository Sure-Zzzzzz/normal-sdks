package io.github.surezzzzzz.sdk.redis.route.model;

import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.redis.route.exception.ConfigurationException;
import lombok.Getter;

/**
 * Redis Server 版本号，仅支持 major.minor.patch 三段结构（patch 可选）
 *
 * @author surezzzzzz
 */
@Getter
public final class RedisServerVersion {

    private final int major;
    private final int minor;
    private final int patch;

    /**
     * 原始字符串，去掉 metadata 后缀
     */
    private final String raw;

    private RedisServerVersion(int major, int minor, int patch, String raw) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.raw = raw;
    }

    /**
     * 唯一解析入口，支持 "7.2.6"、"6.2"、"7.2.6-rc1" 等格式。
     *
     * @param raw 原始版本号字符串
     * @return Redis Server 版本号对象
     * @throws ConfigurationException 版本号为空或格式不合法时抛出
     */
    public static RedisServerVersion parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_012,
                    String.format(ErrorMessage.SERVER_VERSION_INVALID, raw));
        }
        // 去掉 metadata 后缀（如 -rc1、+build.1）
        String trimmed = raw.trim();
        int dashIdx = trimmed.indexOf('-');
        int plusIdx = trimmed.indexOf('+');
        int cutIdx = -1;
        if (dashIdx >= 0 && plusIdx >= 0) {
            cutIdx = Math.min(dashIdx, plusIdx);
        } else if (dashIdx >= 0) {
            cutIdx = dashIdx;
        } else if (plusIdx >= 0) {
            cutIdx = plusIdx;
        }
        String core = cutIdx >= 0 ? trimmed.substring(0, cutIdx) : trimmed;

        String[] parts = core.split("\\.");
        if (parts.length < 2 || parts.length > 3) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_012,
                    String.format(ErrorMessage.SERVER_VERSION_INVALID, raw));
        }
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = parts.length == 3 ? Integer.parseInt(parts[2]) : 0;
            if (major < 0 || minor < 0 || patch < 0) {
                throw new ConfigurationException(ErrorCode.REDIS_ROUTE_012,
                        String.format(ErrorMessage.SERVER_VERSION_INVALID, raw));
            }
            return new RedisServerVersion(major, minor, patch, core);
        } catch (NumberFormatException e) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_012,
                    String.format(ErrorMessage.SERVER_VERSION_INVALID, raw), e);
        }
    }

    /**
     * 当前版本是否大于等于目标版本。
     *
     * @param targetMajor 目标 major
     * @param targetMinor 目标 minor
     * @return true 表示当前版本大于等于目标版本，false 表示小于目标版本
     */
    public boolean isAtLeast(int targetMajor, int targetMinor) {
        return isAtLeast(targetMajor, targetMinor, 0);
    }

    /**
     * 当前版本是否大于等于目标版本。
     *
     * @param targetMajor 目标 major
     * @param targetMinor 目标 minor
     * @param targetPatch 目标 patch
     * @return true 表示当前版本大于等于目标版本，false 表示小于目标版本
     */
    public boolean isAtLeast(int targetMajor, int targetMinor, int targetPatch) {
        if (this.major != targetMajor) {
            return this.major > targetMajor;
        }
        if (this.minor != targetMinor) {
            return this.minor > targetMinor;
        }
        return this.patch >= targetPatch;
    }

    /**
     * 当前版本是否严格小于目标版本。
     *
     * @param targetMajor 目标 major
     * @param targetMinor 目标 minor
     * @return true 表示当前版本严格小于目标版本，false 表示大于等于目标版本
     */
    public boolean isBefore(int targetMajor, int targetMinor) {
        return isBefore(targetMajor, targetMinor, 0);
    }

    /**
     * 当前版本是否严格小于目标版本。
     *
     * @param targetMajor 目标 major
     * @param targetMinor 目标 minor
     * @param targetPatch 目标 patch
     * @return true 表示当前版本严格小于目标版本，false 表示大于等于目标版本
     */
    public boolean isBefore(int targetMajor, int targetMinor, int targetPatch) {
        return !isAtLeast(targetMajor, targetMinor, targetPatch);
    }

    @Override
    public String toString() {
        return raw;
    }
}
