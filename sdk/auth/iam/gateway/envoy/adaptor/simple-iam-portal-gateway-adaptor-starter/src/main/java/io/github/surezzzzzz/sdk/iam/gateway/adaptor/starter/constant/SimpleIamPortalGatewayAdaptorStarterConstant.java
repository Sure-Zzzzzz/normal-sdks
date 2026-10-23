package io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.constant;

/**
 * Starter 的配置和事件固定值。
 */
public final class SimpleIamPortalGatewayAdaptorStarterConstant {

    /**
     * 配置前缀。
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.iam.portal.gateway.adaptor";
    /**
     * 默认同步周期。
     */
    public static final long DEFAULT_SYNC_INTERVAL_MILLIS = 30_000L;
    /**
     * 默认分页大小。
     */
    public static final int DEFAULT_PAGE_SIZE = 100;
    /**
     * 配置端口的最小值。
     */
    public static final int MIN_PORT = 1;
    /**
     * 配置端口的最大值。
     */
    public static final int MAX_PORT = 65535;
    /**
     * xDS 控制面关闭时等待服务终止的最长时间。
     */
    public static final long XDS_SHUTDOWN_TIMEOUT_SECONDS = 5L;
    /**
     * 当前部署形态固定使用 HTTP 上游。
     */
    public static final String UPSTREAM_HTTP_SCHEME = "http";

    private SimpleIamPortalGatewayAdaptorStarterConstant() {
    }
}
