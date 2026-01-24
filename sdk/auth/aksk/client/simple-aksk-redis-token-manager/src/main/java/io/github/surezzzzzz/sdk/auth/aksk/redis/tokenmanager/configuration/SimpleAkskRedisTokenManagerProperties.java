package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple AKSK Redis Token Manager Properties
 * <p>
 * Redis TokenManager 的专属配置
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimpleAkskClientCoreConstant.CONFIG_PREFIX)
public class SimpleAkskRedisTokenManagerProperties {

    /**
     * Redis 配置
     */
    private RedisConfig redis = new RedisConfig();

    /**
     * Redis Configuration
     */
    @Data
    public static class RedisConfig {

        /**
         * Token 缓存配置
         */
        private TokenCacheConfig token = new TokenCacheConfig();

        /**
         * Token Cache Configuration
         */
        @Data
        public static class TokenCacheConfig {
            /**
             * 应用标识，用于区分多个 Client 实例共用 Redis 的场景
             * <p>
             * 最终 Redis Key 格式: sure-auth-aksk-client:{me}:token::{hash}
             * <p>
             * 默认值: default
             */
            private String me = "default";
        }
    }
}
