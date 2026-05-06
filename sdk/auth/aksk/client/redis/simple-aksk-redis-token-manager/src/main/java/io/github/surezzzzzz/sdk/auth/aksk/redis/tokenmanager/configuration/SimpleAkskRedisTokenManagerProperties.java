package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple AKSK Redis Token Manager Properties
 *
 * <p>Redis TokenManager 的专属配置。
 *
 * <p>注意：应用标识（me）由 {@code io.github.surezzzzzz.sdk.cache.me} 统一管理。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = "io.github.surezzzzzz.sdk.auth.aksk.client")
public class SimpleAkskRedisTokenManagerProperties {

    /**
     * Redis Token 缓存配置
     */
    private RedisConfig redis = new RedisConfig();

    @Data
    public static class RedisConfig {

        /**
         * Token 缓存配置
         */
        private TokenCacheConfig token = new TokenCacheConfig();

        @Data
        public static class TokenCacheConfig {

            /**
             * SmartCache cacheName，用于隔离 token 缓存
             * <p>
             * 最终 Redis Key 格式：{keyPrefix}:{cacheName}:{me}::{cacheKey}
             * <p>
             * 默认值：aksk-client-token
             */
            private String cacheName = "aksk-client-token";
        }
    }
}
