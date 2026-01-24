package io.github.surezzzzzz.sdk.auth.aksk.server.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple AKSK Server Properties
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = SimpleAkskServerConstant.CONFIG_PREFIX)
public class SimpleAkskServerProperties {

    /**
     * JWT配置
     */
    private JwtConfig jwt = new JwtConfig();

    /**
     * Redis配置
     */
    private RedisConfig redis = new RedisConfig();

    /**
     * Client配置
     */
    private ClientConfig client = new ClientConfig();

    /**
     * Admin管理页面配置
     */
    private AdminConfig admin = new AdminConfig();

    /**
     * JWT Configuration
     */
    @Data
    public static class JwtConfig {

        /**
         * JWT Key ID
         */
        private String keyId = SimpleAkskServerConstant.DEFAULT_JWT_KEY_ID;

        /**
         * JWT过期时间（秒）
         */
        private Integer expiresIn = SimpleAkskServerConstant.DEFAULT_TOKEN_EXPIRES_IN;

        /**
         * RSA公钥
         * 支持三种格式：
         * 1. PEM文件路径：classpath:keys/public.pem 或 file:/etc/keys/public.pem
         * 2. PEM内容：-----BEGIN PUBLIC KEY-----\nMIIBIjANBg...\n-----END PUBLIC KEY-----
         * 3. Base64编码：MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
         */
        private String publicKey;

        /**
         * RSA私钥
         * 支持三种格式：
         * 1. PEM文件路径：classpath:keys/private.pem 或 file:/etc/keys/private.pem
         * 2. PEM内容：-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBg...\n-----END PRIVATE KEY-----
         * 3. Base64编码：MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC...
         */
        private String privateKey;

        /**
         * Security Context最大大小（字节）
         * 默认4KB（4096字节）
         */
        private Integer securityContextMaxSize = 4096;
    }

    /**
     * Redis Configuration
     */
    @Data
    public static class RedisConfig {

        /**
         * 是否启用Redis缓存OAuth2授权信息
         * true: 使用Redis缓存
         * false: 仅使用MySQL存储（默认）
         */
        private Boolean enabled = false;

        /**
         * Token缓存配置
         */
        private TokenConfig token = new TokenConfig();

        /**
         * Token Cache Configuration
         */
        @Data
        public static class TokenConfig {
            /**
             * 应用标识,用于区分多个OAuth2 Server实例共用Redis的场景
             * 最终Redis key格式: sure-auth-aksk:{me}:oauth2:authorization:{id}
             */
            private String me = SimpleAkskServerConstant.DEFAULT_APPLICATION_NAME;
        }
    }

    /**
     * Client Configuration
     */
    @Data
    public static class ClientConfig {
        // 当前为空，预留未来客户端相关配置
    }

    /**
     * Admin Configuration
     */
    @Data
    public static class AdminConfig {

        /**
         * 是否启用Admin管理页面
         */
        private Boolean enabled = true;

        /**
         * Admin用户名
         */
        private String username = SimpleAkskServerConstant.DEFAULT_ADMIN_USERNAME;

        /**
         * Admin密码
         */
        private String password;

        /**
         * Session超时时间（分钟）
         * 默认30分钟
         */
        private Integer sessionTimeoutMinutes = 30;
    }
}
