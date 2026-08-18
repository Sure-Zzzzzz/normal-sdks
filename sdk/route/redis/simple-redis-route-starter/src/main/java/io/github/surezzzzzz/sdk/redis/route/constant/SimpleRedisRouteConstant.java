package io.github.surezzzzzz.sdk.redis.route.constant;

/**
 * Simple Redis Route 常量
 *
 * @author surezzzzzz
 */
public final class SimpleRedisRouteConstant {

    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.redis.route";
    public static final String REDIS_CONNECTION_FACTORY_BEAN_NAME = "redisConnectionFactory";
    public static final String STRING_REDIS_TEMPLATE_BEAN_NAME = "stringRedisTemplate";
    public static final String REDIS_TEMPLATE_BEAN_NAME = "redisTemplate";

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
    public static final int DEFAULT_LETTUCE_POOL_MAX_ACTIVE = 8;
    public static final int DEFAULT_LETTUCE_POOL_MAX_IDLE = 8;
    public static final int DEFAULT_LETTUCE_POOL_MIN_IDLE = 0;
    public static final long DEFAULT_LETTUCE_POOL_MAX_WAIT_MS = -1L;
    public static final long DEFAULT_LETTUCE_POOL_TIME_BETWEEN_EVICTION_RUNS_MS = -1L;
    public static final boolean DEFAULT_SSL_VERIFY_PEER = true;
    public static final boolean DEFAULT_LETTUCE_CLUSTER_ADAPTIVE_REFRESH = true;
    public static final boolean DEFAULT_LETTUCE_CLUSTER_PERIODIC_REFRESH = true;
    public static final boolean DEFAULT_LETTUCE_CLUSTER_DYNAMIC_REFRESH_SOURCES = true;
    public static final boolean DEFAULT_LETTUCE_CLUSTER_CLOSE_STALE_CONNECTIONS = true;
    public static final String DEFAULT_LETTUCE_READ_FROM = RedisReadFrom.MASTER.getCode();

    /**
     * 默认是否探测 Redis Server 信息
     */
    public static final boolean DEFAULT_PROBE_SERVER_INFO = true;

    private SimpleRedisRouteConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}
