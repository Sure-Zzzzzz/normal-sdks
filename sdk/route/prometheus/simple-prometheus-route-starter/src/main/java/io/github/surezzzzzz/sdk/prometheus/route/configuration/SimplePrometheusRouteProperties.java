package io.github.surezzzzzz.sdk.prometheus.route.configuration;

import io.github.surezzzzzz.sdk.prometheus.route.constant.SimplePrometheusRouteConstant;
import io.github.surezzzzzz.sdk.prometheus.route.model.PrometheusRouteAuthenticationType;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Prometheus Route 配置。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimplePrometheusRouteConstant.CONFIG_PREFIX)
public class SimplePrometheusRouteProperties {

    /**
     * 是否启用 Route。
     */
    private boolean enable;

    /**
     * 关闭时等待已准入请求完成的时长（毫秒）。
     */
    private int shutdownTimeoutMs = SimplePrometheusRouteConstant.DEFAULT_SHUTDOWN_TIMEOUT_MS;

    /**
     * 精确 targetKey 到固定 Prometheus Server 的映射。
     */
    private Map<String, TargetConfig> targets = new LinkedHashMap<String, TargetConfig>();

    /**
     * 单个 target 配置。
     */
    @Data
    @ToString(exclude = {"url", "authentication"})
    public static class TargetConfig {

        /**
         * 固定 Prometheus Server 地址。
         */
        private String url;

        /**
         * target 认证配置。
         */
        private AuthenticationConfig authentication = new AuthenticationConfig();

        /**
         * target 私有 HTTP 配置。
         */
        private HttpConfig http = new HttpConfig();
    }

    /**
     * target 认证配置。
     */
    @Data
    @ToString(exclude = {"username", "password", "token"})
    public static class AuthenticationConfig {

        /**
         * 认证类型。
         */
        private PrometheusRouteAuthenticationType type = PrometheusRouteAuthenticationType.NONE;

        /**
         * BASIC 认证用户名。
         */
        private String username;

        /**
         * BASIC 认证密码。
         */
        private String password;

        /**
         * BEARER 认证令牌。
         */
        private String token;
    }

    /**
     * target 私有 HTTP 配置。
     */
    @Data
    public static class HttpConfig {

        /**
         * 建连超时（毫秒）。
         */
        private int connectTimeoutMs = SimplePrometheusRouteConstant.DEFAULT_CONNECT_TIMEOUT_MS;

        /**
         * 读超时（毫秒）。
         */
        private int socketTimeoutMs = SimplePrometheusRouteConstant.DEFAULT_SOCKET_TIMEOUT_MS;

        /**
         * 从连接池获取连接的超时（毫秒）。
         */
        private int connectionRequestTimeoutMs = SimplePrometheusRouteConstant.DEFAULT_CONNECTION_REQUEST_TIMEOUT_MS;

        /**
         * 复用空闲连接前的校验阈值（毫秒）。
         */
        private int validateAfterInactivityMs = SimplePrometheusRouteConstant.DEFAULT_VALIDATE_AFTER_INACTIVITY_MS;

        /**
         * 连接池最大连接数。
         */
        private int maxTotal = SimplePrometheusRouteConstant.DEFAULT_MAX_TOTAL;

        /**
         * 单路由最大连接数。
         */
        private int maxPerRoute = SimplePrometheusRouteConstant.DEFAULT_MAX_PER_ROUTE;

        /**
         * 最大响应正文长度（字节）。
         */
        private int maxResponseBodyBytes = SimplePrometheusRouteConstant.DEFAULT_MAX_RESPONSE_BODY_BYTES;
    }
}
