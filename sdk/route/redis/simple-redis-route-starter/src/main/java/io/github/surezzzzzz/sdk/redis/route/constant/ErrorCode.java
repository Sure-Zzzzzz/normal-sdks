package io.github.surezzzzzz.sdk.redis.route.constant;

/**
 * 错误码常量
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    public static final String REDIS_ROUTE_001 = "REDIS_ROUTE_001";
    public static final String REDIS_ROUTE_002 = "REDIS_ROUTE_002";
    public static final String REDIS_ROUTE_003 = "REDIS_ROUTE_003";
    public static final String REDIS_ROUTE_004 = "REDIS_ROUTE_004";
    public static final String REDIS_ROUTE_005 = "REDIS_ROUTE_005";
    public static final String REDIS_ROUTE_006 = "REDIS_ROUTE_006";
    public static final String REDIS_ROUTE_007 = "REDIS_ROUTE_007";
    public static final String REDIS_ROUTE_008 = "REDIS_ROUTE_008";
    public static final String REDIS_ROUTE_009 = "REDIS_ROUTE_009";
    public static final String REDIS_ROUTE_010 = "REDIS_ROUTE_010";

    /**
     * Redis Server info 探测失败
     */
    public static final String REDIS_ROUTE_011 = "REDIS_ROUTE_011";

    /**
     * Redis Server 版本号格式不合法
     */
    public static final String REDIS_ROUTE_012 = "REDIS_ROUTE_012";

    /**
     * Redis Server 能力不满足要求
     */
    public static final String REDIS_ROUTE_013 = "REDIS_ROUTE_013";

    private ErrorCode() {
        throw new UnsupportedOperationException("Utility class");
    }
}
