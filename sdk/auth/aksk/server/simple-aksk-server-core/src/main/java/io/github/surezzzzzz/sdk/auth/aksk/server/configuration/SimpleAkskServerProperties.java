package io.github.surezzzzzz.sdk.auth.aksk.server.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.server.constant.AkskOwnerAuthorizationSynchronizationMode;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
     * 限流配置
     */
    private LimiterConfig limiter = new LimiterConfig();

    /**
     * 过期Token定时清理配置
     */
    private CleanupConfig cleanup = new CleanupConfig();

    /**
     * 外部所属人授权投影配置。
     *
     * <p>这里只保存 AKSK 通用的投影与租约策略，不包含任何身份源 endpoint、client 或 secret。</p>
     */
    private OwnerAuthorizationProjectionConfig ownerAuthorization =
            new OwnerAuthorizationProjectionConfig();

    @Data
    public static class JwtConfig {

        private String keyId = SimpleAkskServerConstant.DEFAULT_JWT_KEY_ID;

        private Integer expiresIn = SimpleAkskServerConstant.DEFAULT_TOKEN_EXPIRES_IN;

        private String publicKey;

        private String privateKey;

        private Integer securityContextMaxSize = SimpleAkskServerConstant.DEFAULT_SECURITY_CONTEXT_MAX_SIZE;

        /**
         * AES-256 密钥，用于 JWE 加密（通过流水线注入环境变量 AKS_AES_256_KEY）
         * 支持格式：Base64 编码的 32 字节密钥
         */
        private String encryptionKey;
    }

    @Data
    public static class RedisConfig {

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

    @Data
    public static class LimiterConfig {

        private OAuth2Config oauth2 = new OAuth2Config();

        @Data
        public static class OAuth2Config {

            private Boolean enable = SimpleAkskServerConstant.DEFAULT_LIMITER_OAUTH2_ENABLE;

            private EndpointLimitConfig token = EndpointLimitConfig.token();

            private EndpointLimitConfig introspect = EndpointLimitConfig.introspect();

            private EndpointLimitConfig revoke = EndpointLimitConfig.revoke();
        }

        @Data
        public static class EndpointLimitConfig {

            private String algorithm;

            private String fallback;

            private String keyStrategy = SimpleAkskServerConstant.DEFAULT_LIMITER_KEY_STRATEGY;

            private List<LimitRuleConfig> limits = new ArrayList<>();

            private static EndpointLimitConfig token() {
                EndpointLimitConfig config = new EndpointLimitConfig();
                config.setAlgorithm(SimpleAkskServerConstant.DEFAULT_LIMITER_ALGORITHM);
                config.setFallback(SimpleAkskServerConstant.DEFAULT_LIMITER_TOKEN_FALLBACK);
                config.getLimits().add(LimitRuleConfig.of(
                        SimpleAkskServerConstant.DEFAULT_LIMITER_TOKEN_COUNT,
                        SimpleAkskServerConstant.DEFAULT_LIMITER_WINDOW,
                        SimpleAkskServerConstant.DEFAULT_LIMITER_WINDOW_UNIT));
                return config;
            }

            private static EndpointLimitConfig introspect() {
                EndpointLimitConfig config = new EndpointLimitConfig();
                config.setAlgorithm(SimpleAkskServerConstant.DEFAULT_LIMITER_ALGORITHM);
                config.setFallback(SimpleAkskServerConstant.DEFAULT_LIMITER_INTROSPECT_FALLBACK);
                config.getLimits().add(LimitRuleConfig.of(
                        SimpleAkskServerConstant.DEFAULT_LIMITER_INTROSPECT_COUNT,
                        SimpleAkskServerConstant.DEFAULT_LIMITER_WINDOW,
                        SimpleAkskServerConstant.DEFAULT_LIMITER_WINDOW_UNIT));
                return config;
            }

            private static EndpointLimitConfig revoke() {
                EndpointLimitConfig config = new EndpointLimitConfig();
                config.setAlgorithm(SimpleAkskServerConstant.DEFAULT_LIMITER_ALGORITHM);
                config.setFallback(SimpleAkskServerConstant.DEFAULT_LIMITER_REVOKE_FALLBACK);
                config.getLimits().add(LimitRuleConfig.of(
                        SimpleAkskServerConstant.DEFAULT_LIMITER_REVOKE_COUNT,
                        SimpleAkskServerConstant.DEFAULT_LIMITER_WINDOW,
                        SimpleAkskServerConstant.DEFAULT_LIMITER_WINDOW_UNIT));
                return config;
            }
        }

        @Data
        public static class LimitRuleConfig {

            private Integer count;

            private Integer window;

            private TimeUnit unit = SimpleAkskServerConstant.DEFAULT_LIMITER_WINDOW_UNIT;

            private static LimitRuleConfig of(Integer count, Integer window, TimeUnit unit) {
                LimitRuleConfig config = new LimitRuleConfig();
                config.setCount(count);
                config.setWindow(window);
                config.setUnit(unit);
                return config;
            }
        }
    }

    /**
     * 过期Token定时清理配置
     */
    @Data
    public static class CleanupConfig {

        /**
         * 定时清理开关，false 时清理任务不装配
         */
        private Boolean enable = SimpleAkskServerConstant.DEFAULT_CLEANUP_ENABLE;

        /**
         * 清理调度 cron 表达式（默认每天凌晨2点）
         */
        private String cron = SimpleAkskServerConstant.DEFAULT_CLEANUP_CRON;

        /**
         * 分批删除单批行数上限
         */
        private Integer batchSize = SimpleAkskServerConstant.DEFAULT_CLEANUP_BATCH_SIZE;

        /**
         * 清理任务分布式锁租约时长（秒）
         */
        private Integer lockLeaseSeconds = SimpleAkskServerConstant.DEFAULT_CLEANUP_LOCK_LEASE_SECONDS;
    }

    @Data
    public static class OwnerAuthorizationProjectionConfig {

        /**
         * 默认关闭；只有宿主显式提供所属人授权 provider 后才允许开启。
         */
        private Boolean enabled = Boolean.FALSE;

        /**
         * 所属人授权身份源标识。
         */
        private String ownerSourceId;

        /**
         * 公共资源认证层中 HUMAN 的认证来源标识。
         */
        private String humanResourceSourceId = "human";

        /**
         * 默认用本地投影；STRICT_ONLINE 仅用于受控诊断或显式收紧场景。
         */
        private AkskOwnerAuthorizationSynchronizationMode synchronizationMode =
                AkskOwnerAuthorizationSynchronizationMode.EVENTUAL_WITH_LEASE;

        /**
         * 正常拉取轮询间隔毫秒。
         */
        private Integer pullIntervalMillis = 1000;

        /**
         * 外部授权源暂不可用时，本地 inherited 授权最多继续服务的秒数。
         */
        private Integer leaseSeconds = 30;

        /**
         * 单次拉取的最大连续事件数。
         */
        private Integer pullPageSize = 100;
    }
}
