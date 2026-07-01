package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.constant.SimpleAkskResourceServerConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple AKSK Resource Server Configuration Properties
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = SimpleAkskResourceServerConstant.CONFIG_PREFIX)
public class SimpleAkskResourceServerProperties {

    /**
     * 是否启用（默认：true）
     */
    private boolean enabled = true;

    /**
     * Introspect 配置（调 /oauth2/introspect 验证 token）
     */
    private Introspect introspect = new Introspect();

    /**
     * 安全配置
     */
    private Security security = new Security();

    @Data
    public static class Introspect {

        /**
         * introspect 端点地址
         * 示例：http://localhost:8080/oauth2/introspect
         */
        private String endpoint;

        /**
         * 调 introspect 用的 clientId
         * 留空则不带认证（仅适用于 server 端 require-authentication=false 的场景）
         */
        private String clientId;

        /**
         * 调 introspect 用的 clientSecret
         */
        private String clientSecret;

        /**
         * 本地缓存配置
         */
        private LocalCacheConfig localCache = new LocalCacheConfig();

        @Data
        public static class LocalCacheConfig {

            /**
             * 是否启用本地缓存（默认开启，撤销感知延迟 = TTL）
             */
            private boolean enabled = SimpleAkskResourceServerConstant.DEFAULT_LOCAL_CACHE_ENABLED;

            /**
             * 缓存 TTL（秒），默认 3s
             */
            private int expireSeconds = SimpleAkskResourceServerConstant.DEFAULT_LOCAL_CACHE_EXPIRE_SECONDS;

            /**
             * 最大缓存条目数，默认 10000
             */
            private int maxSize = SimpleAkskResourceServerConstant.DEFAULT_LOCAL_CACHE_MAX_SIZE;

            /**
             * 统计日志打印间隔（秒），每次 cache miss 时判断是否到达间隔，默认 60s
             */
            private int statsLogIntervalSeconds = SimpleAkskResourceServerConstant.DEFAULT_STATS_LOG_INTERVAL_SECONDS;

            /**
             * 兜底缓存配置（端点不可用时的降级策略）
             */
            private FallbackConfig fallback = new FallbackConfig();

            @Data
            public static class FallbackConfig {

                /**
                 * 是否启用兜底降级，默认 false，需显式开启
                 * 开启后端点不可用时使用兜底缓存放行，接受安全与可用性的权衡
                 */
                private boolean enabled = SimpleAkskResourceServerConstant.DEFAULT_FALLBACK_ENABLED;

                /**
                 * 兜底缓存 TTL 倍数：兜底 TTL = expire-seconds × 此值，默认 10
                 * 建议范围 [2, 100]，超出范围打 WARN 提示风险
                 */
                private int staleTtlMultiplier = SimpleAkskResourceServerConstant.DEFAULT_STALE_TTL_MULTIPLIER;

                /**
                 * 兜底缓存最大条目数，默认 10000
                 */
                private int staleMaxSize = SimpleAkskResourceServerConstant.DEFAULT_STALE_MAX_SIZE;
            }
        }
    }

    @Data
    public static class Security {

        /**
         * 需要保护的路径（需要认证），默认：/api/**
         */
        private List<String> protectedPaths = new ArrayList<String>() {{
            add("/api/**");
        }};

        /**
         * 白名单路径（不需要认证）
         */
        private List<String> permitAllPaths = new ArrayList<>();

        /**
         * 是否启用 context-path-aware 路径归一化，默认开启
         */
        private boolean contextPathAware = SimpleAkskResourceServerConstant.DEFAULT_CONTEXT_PATH_AWARE;
    }
}