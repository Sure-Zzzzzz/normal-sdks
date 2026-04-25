package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.constant.SimpleAkskResourceServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.constant.VerificationMode;
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
     * Token 验证模式
     * JWT（默认）：本地验签，性能最好，不支持即时撤销感知
     * INTROSPECT：调 introspect 端点验证，支持即时撤销感知，每次请求多一次 HTTP 调用
     */
    private VerificationMode verificationMode = VerificationMode.JWT;

    /**
     * JWT 配置（verificationMode=JWT 时使用）
     */
    private Jwt jwt = new Jwt();

    /**
     * Introspect 配置（verificationMode=INTROSPECT 时使用）
     */
    private Introspect introspect = new Introspect();

    /**
     * 安全配置
     */
    private Security security = new Security();

    @Data
    public static class Jwt {

        /**
         * OAuth2 授权服务器的 Issuer URI（推荐方式）
         */
        private String issuerUri;

        /**
         * JWT 公钥（PEM 格式字符串），仅在未配置 issuer-uri 时使用
         */
        private String publicKey;

        /**
         * JWT 公钥文件路径（支持 classpath: 和 file: 前缀），仅在未配置 issuer-uri 时使用
         */
        private String publicKeyLocation;
    }

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
         * 本地缓存配置（可选，启用后减少 HTTP 往返）
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
    }
}
