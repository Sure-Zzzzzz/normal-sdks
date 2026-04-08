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

    @Data
    public static class JwtConfig {

        private String keyId = SimpleAkskServerConstant.DEFAULT_JWT_KEY_ID;

        private Integer expiresIn = SimpleAkskServerConstant.DEFAULT_TOKEN_EXPIRES_IN;

        private String publicKey;

        private String privateKey;

        private Integer securityContextMaxSize = 4096;
    }

    @Data
    public static class RedisConfig {

        private Boolean enabled = false;

        private TokenConfig token = new TokenConfig();

        @Data
        public static class TokenConfig {
            private String me = SimpleAkskServerConstant.DEFAULT_APPLICATION_NAME;
        }
    }

    @Data
    public static class ClientConfig {
    }

    @Data
    public static class AdminConfig {

        private Boolean enabled = true;

        private String username = SimpleAkskServerConstant.DEFAULT_ADMIN_USERNAME;

        private String password;

        private Integer sessionTimeoutMinutes = 30;
    }
}
