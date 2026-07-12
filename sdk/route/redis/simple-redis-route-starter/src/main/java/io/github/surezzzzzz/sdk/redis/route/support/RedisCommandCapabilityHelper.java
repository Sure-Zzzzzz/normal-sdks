package io.github.surezzzzzz.sdk.redis.route.support;

import io.github.surezzzzzz.sdk.redis.route.model.RedisServerInfo;

/**
 * Redis 命令能力判断 Helper（基于 Server 版本的保守策略）
 *
 * <p>所有方法在 info 为 null 或 known=false 时统一返回 false（保守策略）。
 * 能力名称常量集中在此类，避免调用方散落字符串。
 *
 * @author surezzzzzz
 */
public final class RedisCommandCapabilityHelper {

    /**
     * ACL 命令（6.0+）
     */
    public static final String CAPABILITY_ACL = "ACL";

    /**
     * UNLINK 命令（4.0+，异步 DEL）
     */
    public static final String CAPABILITY_UNLINK = "UNLINK";

    /**
     * GETEX 命令（6.2+）
     */
    public static final String CAPABILITY_GETEX = "GETEX";

    /**
     * SET GET 选项（6.2+，SET key value GET 返回旧值）
     */
    public static final String CAPABILITY_SET_GET = "SET_GET";

    /**
     * SET KEEPTTL 选项（6.0+）
     */
    public static final String CAPABILITY_KEEPTTL = "KEEPTTL";

    /**
     * ZPOPMIN / ZPOPMAX 命令（5.0+）
     */
    public static final String CAPABILITY_ZPOP = "ZPOP";

    /**
     * LMOVE 命令（6.2+）
     */
    public static final String CAPABILITY_LMOVE = "LMOVE";

    private RedisCommandCapabilityHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 是否支持 ACL 命令（Redis 6.0+）。
     *
     * @param info Redis Server 信息
     * @return true 表示支持，false 表示不支持或版本未知
     */
    public static boolean supportsAcl(RedisServerInfo info) {
        return RedisServerVersionHelper.isAtLeast(info, 6, 0);
    }

    /**
     * 是否支持 UNLINK 命令（Redis 4.0+）。
     *
     * @param info Redis Server 信息
     * @return true 表示支持，false 表示不支持或版本未知
     */
    public static boolean supportsUnlink(RedisServerInfo info) {
        return RedisServerVersionHelper.isAtLeast(info, 4, 0);
    }

    /**
     * 是否支持 GETEX 命令（Redis 6.2+）。
     *
     * @param info Redis Server 信息
     * @return true 表示支持，false 表示不支持或版本未知
     */
    public static boolean supportsGetEx(RedisServerInfo info) {
        return RedisServerVersionHelper.isAtLeast(info, 6, 2);
    }

    /**
     * 是否支持 SET key value GET 选项（Redis 6.2+）。
     *
     * @param info Redis Server 信息
     * @return true 表示支持，false 表示不支持或版本未知
     */
    public static boolean supportsSetGet(RedisServerInfo info) {
        return RedisServerVersionHelper.isAtLeast(info, 6, 2);
    }

    /**
     * 是否支持 SET KEEPTTL 选项（Redis 6.0+）。
     *
     * @param info Redis Server 信息
     * @return true 表示支持，false 表示不支持或版本未知
     */
    public static boolean supportsKeepTtl(RedisServerInfo info) {
        return RedisServerVersionHelper.isAtLeast(info, 6, 0);
    }

    /**
     * 是否支持 ZPOPMIN / ZPOPMAX 命令（Redis 5.0+）。
     *
     * @param info Redis Server 信息
     * @return true 表示支持，false 表示不支持或版本未知
     */
    public static boolean supportsZPop(RedisServerInfo info) {
        return RedisServerVersionHelper.isAtLeast(info, 5, 0);
    }

    /**
     * 是否支持 LMOVE 命令（Redis 6.2+）。
     *
     * @param info Redis Server 信息
     * @return true 表示支持，false 表示不支持或版本未知
     */
    public static boolean supportsLMove(RedisServerInfo info) {
        return RedisServerVersionHelper.isAtLeast(info, 6, 2);
    }

    /**
     * 按能力名称常量判断是否支持。
     *
     * @param info           Redis Server 信息
     * @param capabilityName 能力名称常量
     * @return true 表示支持，false 表示不支持、能力未知或版本未知
     */
    public static boolean supports(RedisServerInfo info, String capabilityName) {
        if (capabilityName == null) {
            return false;
        }
        switch (capabilityName) {
            case CAPABILITY_ACL:
                return supportsAcl(info);
            case CAPABILITY_UNLINK:
                return supportsUnlink(info);
            case CAPABILITY_GETEX:
                return supportsGetEx(info);
            case CAPABILITY_SET_GET:
                return supportsSetGet(info);
            case CAPABILITY_KEEPTTL:
                return supportsKeepTtl(info);
            case CAPABILITY_ZPOP:
                return supportsZPop(info);
            case CAPABILITY_LMOVE:
                return supportsLMove(info);
            default:
                return false;
        }
    }
}
