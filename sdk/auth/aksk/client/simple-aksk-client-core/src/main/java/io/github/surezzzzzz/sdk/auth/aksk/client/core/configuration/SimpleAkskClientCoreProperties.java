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
     * Token 管理配置
     */
    private TokenConfig token = new TokenConfig();

    @Data
    public static class TokenConfig {
        /**
         * Token 过期前多少秒刷新（默认 300 秒）
         */
        private int refreshBeforeExpire = SimpleAkskClientCoreConstant.DEFAULT_REFRESH_BEFORE_EXPIRE;
    }
}
