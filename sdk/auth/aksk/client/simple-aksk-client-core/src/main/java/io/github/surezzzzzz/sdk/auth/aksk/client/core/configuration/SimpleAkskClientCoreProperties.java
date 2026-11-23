package io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simple AKSK Client Core Properties
 * <p>
 * 所有 AKSK Client 模块的通用配置
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimpleAkskClientCoreConstant.CONFIG_PREFIX)
public class SimpleAkskClientCoreProperties {

    /**
     * 是否启用（默认 false）
     */
    private boolean enable = false;

    /**
     * AKSK Server URL
     */
    private String serverUrl;

    /**
     * Token 端点（默认值引用常量）
     */
    private String tokenEndpoint = SimpleAkskClientCoreConstant.DEFAULT_TOKEN_ENDPOINT;

    /**
     * Client ID
     */
    private String clientId;

    /**
     * Client Secret
     */
    @ToString.Exclude
    private String clientSecret;

    /**
     * HTTP 请求配置
     */
    private HttpConfig http = new HttpConfig();

    /**
     * 命名 AKP Profile 配置；default 是保留名，默认凭据只使用顶层字段
     */
    private Map<String, ProfileConfig> profiles = new LinkedHashMap<>();

    @Data
    public static class HttpConfig {
        /**
         * 连接超时（毫秒），默认 5000
         */
        private int connectTimeoutMs = SimpleAkskClientCoreConstant.DEFAULT_CONNECT_TIMEOUT_MS;

        /**
         * 读取超时（毫秒），默认 15000
         */
        private int readTimeoutMs = SimpleAkskClientCoreConstant.DEFAULT_READ_TIMEOUT_MS;
    }

    /**
     * 命名 Profile 的独立 AKP 凭据配置
     */
    @Data
    public static class ProfileConfig {
        /**
         * AKSK Server origin，只允许协议、主机和端口
         */
        private String serverUrl;

        /**
         * Token 端点，默认值引用常量
         */
        private String tokenEndpoint = SimpleAkskClientCoreConstant.DEFAULT_TOKEN_ENDPOINT;

        /**
         * AKP Client ID
         */
        private String clientId;

        /**
         * AKP Client Secret
         */
        @ToString.Exclude
        private String clientSecret;

        /**
         * Token 请求 HTTP 配置
         */
        private HttpConfig http = new HttpConfig();
    }
}
