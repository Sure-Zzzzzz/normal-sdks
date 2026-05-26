package io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
    private String clientSecret;

    /**
     * HTTP 请求配置
     */
    private HttpConfig http = new HttpConfig();

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
}
