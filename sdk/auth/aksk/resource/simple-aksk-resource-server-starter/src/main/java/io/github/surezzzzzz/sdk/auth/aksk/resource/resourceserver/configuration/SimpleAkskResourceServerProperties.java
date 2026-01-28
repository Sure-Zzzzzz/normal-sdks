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
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = SimpleAkskResourceServerConstant.CONFIG_PREFIX)
public class SimpleAkskResourceServerProperties {

    /**
     * 是否启用（默认：true）
     */
    private boolean enabled = true;

    /**
     * JWT 配置
     */
    private Jwt jwt = new Jwt();

    /**
     * 安全配置
     */
    private Security security = new Security();

    /**
     * JWT 配置
     */
    @Data
    public static class Jwt {

        /**
         * OAuth2 授权服务器的 Issuer URI（推荐方式）
         * <p>
         * 配置后，Resource Server 会自动从 {issuer-uri}/.well-known/oauth-authorization-server
         * 获取授权服务器元数据，并从 JWKS 端点获取公钥
         * <p>
         * 示例：http://localhost:8080
         */
        private String issuerUri;

        /**
         * JWT 公钥（PEM 格式字符串）
         * <p>
         * 仅在未配置 issuer-uri 时使用
         * <p>
         * 示例：
         * <pre>
         * -----BEGIN PUBLIC KEY-----
         * MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
         * -----END PUBLIC KEY-----
         * </pre>
         */
        private String publicKey;

        /**
         * JWT 公钥文件路径（支持 classpath: 和 file: 前缀）
         * <p>
         * 仅在未配置 issuer-uri 时使用
         * <p>
         * 示例：
         * <ul>
         *   <li>classpath:jwt-public-key.pem</li>
         *   <li>file:/etc/aksk/jwt-public-key.pem</li>
         * </ul>
         */
        private String publicKeyLocation;
    }

    /**
     * 安全配置
     */
    @Data
    public static class Security {

        /**
         * 需要保护的路径（需要 JWT 认证）
         * <p>
         * 默认：/api/**
         */
        private List<String> protectedPaths = new ArrayList<String>() {{
            add("/api/**");
        }};

        /**
         * 白名单路径（不需要认证）
         * <p>
         * 示例：
         * <ul>
         *   <li>/api/health</li>
         *   <li>/api/public/**</li>
         * </ul>
         */
        private List<String> permitAllPaths = new ArrayList<>();
    }
}
