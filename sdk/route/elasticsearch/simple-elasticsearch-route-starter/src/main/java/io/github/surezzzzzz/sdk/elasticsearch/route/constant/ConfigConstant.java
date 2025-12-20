package io.github.surezzzzzz.sdk.elasticsearch.route.constant;

/**
 * 配置常量
 *
 * @author surezzzzzz
 */
public class ConfigConstant {

    // ========== 默认值 ==========

    public static final String DEFAULT_DATASOURCE_KEY = "primary";
    public static final int DEFAULT_CONNECT_TIMEOUT = 5000;
    public static final int DEFAULT_SOCKET_TIMEOUT = 60000;
    public static final int DEFAULT_KEEP_ALIVE_SECONDS = 300;
    public static final int DEFAULT_MAX_CONN_TOTAL = 100;
    public static final int DEFAULT_MAX_CONN_PER_ROUTE = 10;
    public static final boolean DEFAULT_ENABLE_CONNECTION_REUSE = true;
    public static final int DEFAULT_VERSION_DETECT_TIMEOUT_MS = 1500;

    // ========== 路由优先级范围 ==========

    public static final int PRIORITY_MIN = 1;
    public static final int PRIORITY_MAX = 10000;
    public static final int PRIORITY_DEFAULT = 100;

    // ========== 端口默认值 ==========

    public static final int DEFAULT_HTTP_PORT = 9200;
    public static final int DEFAULT_HTTPS_PORT = 443;

    // ========== 协议 ==========

    public static final String PROTOCOL_HTTP = "http";
    public static final String PROTOCOL_HTTPS = "https";

    // ========== 线程池 ==========

    public static final int VERSION_DETECT_THREAD_POOL_MIN = 1;
    public static final int VERSION_DETECT_THREAD_POOL_MAX = 4;
    public static final String VERSION_DETECT_THREAD_NAME = "es-route-version-detect";

    private ConfigConstant() {
        // 私有构造函数，防止实例化
    }
}
