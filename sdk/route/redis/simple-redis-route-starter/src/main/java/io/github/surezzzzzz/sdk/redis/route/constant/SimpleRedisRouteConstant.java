package io.github.surezzzzzz.sdk.redis.route.constant;

/**
 * Simple Redis Route 常量
 *
 * @author surezzzzzz
 */
public final class SimpleRedisRouteConstant {

    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.redis.route";

    public static final String DEFAULT_DATASOURCE_KEY = "default";
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 6379;
    public static final int DEFAULT_DATABASE = 0;
    public static final int DEFAULT_CLUSTER_MAX_REDIRECTS = 3;
    public static final int DEFAULT_RULE_PRIORITY = 1000;
    public static final long DEFAULT_TIMEOUT_MS = 2000L;
    public static final long DEFAULT_CONNECT_TIMEOUT_MS = 2000L;
    public static final long DEFAULT_LETTUCE_SHUTDOWN_TIMEOUT_MS = 100L;
    public static final int DEFAULT_LETTUCE_REQUEST_QUEUE_SIZE = 10000;
    public static final long DEFAULT_LETTUCE_CLUSTER_REFRESH_PERIOD_MS = 60000L;

    /**
     * 默认是否探测 Redis Server 信息
     */
    public static final boolean DEFAULT_PROBE_SERVER_INFO = true;

    private SimpleRedisRouteConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}
