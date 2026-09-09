package io.github.surezzzzzz.sdk.auth.iam.server.configuration;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple IAM Server Properties
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = SimpleIamServerConstant.CONFIG_PREFIX)
public class SimpleIamServerProperties {

    /**
     * 总开关
     */
    private Boolean enable = SimpleIamServerConstant.DEFAULT_ENABLE;

    /**
     * Token 签发者（iss），绝对 URI，必配——无默认值，缺失启动即报错
     */
    private String issuer;

    /**
     * 应用标识，注入到所有 Redis/Cache/Lock/Limiter key 的 me 段
     */
    private String me = SimpleIamServerConstant.DEFAULT_APPLICATION_NAME;

    /**
     * 安全依赖校验：为 true 时对应 SDK 未就绪则启动失败
     */
    private SecurityConfig security = new SecurityConfig();

    /**
     * Token 配置
     */
    private TokenConfig token = new TokenConfig();

    /**
     * Session 配置
     */
    private SessionConfig session = new SessionConfig();

    /**
     * 登录配置
     */
    private LoginConfig login = new LoginConfig();

    /**
     * 验证码（captcha）配置：风险校验插件，非 MFA
     */
    private CaptchaConfig captcha = new CaptchaConfig();

    /**
     * MFA 配置：1.0.0 仅 SPI 预留，默认关闭
     */
    private MfaConfig mfa = new MfaConfig();

    /**
     * 密码策略配置
     */
    private PasswordConfig password = new PasswordConfig();

    /**
     * 注册配置：默认管理员建账
     */
    private RegistrationConfig registration = new RegistrationConfig();

    /**
     * 密码重置配置：默认管理员重置
     */
    private PasswordResetConfig passwordReset = new PasswordResetConfig();

    /**
     * 启动引导配置：首个管理员账号
     */
    private BootstrapConfig bootstrap = new BootstrapConfig();

    /**
     * 管理台配置
     */
    private AdminConfig admin = new AdminConfig();

    /**
     * 外部身份源登录配置
     */
    private ExternalIdentityConfig externalIdentity = new ExternalIdentityConfig();

    /**
     * 过期 Token 定时清理配置
     */
    private CleanupConfig cleanup = new CleanupConfig();

    @Data
    public static class ExternalIdentityConfig {

        /**
         * 外部身份开号模式：jit（首次登录自动创建本地账号，默认）/ pre-bound-only（仅管理员预绑定身份可登录）
         */
        private String provisioningMode = SimpleIamServerConstant.PROVISIONING_MODE_JIT;

        /**
         * 跳转型登录回调地址基础 URL（含协议主机端口，不含路径）；为空时按请求推断。
         * 反向代理导致请求 Host 不可信时必须显式配置。
         */
        private String callbackBaseUrl;

        /**
         * 各登录方式的展示文案（key 为登录方式编码）；未配置时 name 使用编码本身
         */
        private java.util.Map<String, ProviderDisplayConfig> providers = new java.util.LinkedHashMap<>();
    }

    @Data
    public static class CleanupConfig {

        /**
         * 过期 Token 定时清理开关，false 时清理任务不装配
         */
        private Boolean enable = SimpleIamServerConstant.DEFAULT_CLEANUP_ENABLE;

        /**
         * 清理调度 cron 表达式（默认每天凌晨 2 点）
         */
        private String cron = SimpleIamServerConstant.DEFAULT_CLEANUP_CRON;

        /**
         * 分批删除单批行数上限
         */
        private Integer batchSize = SimpleIamServerConstant.DEFAULT_CLEANUP_BATCH_SIZE;

        /**
         * 清理任务分布式锁租约时长（秒）
         */
        private Integer lockLeaseSeconds = SimpleIamServerConstant.DEFAULT_CLEANUP_LOCK_LEASE_SECONDS;
    }

    @Data
    public static class ProviderDisplayConfig {

        /**
         * 登录方式展示名（登录页按钮文案）
         */
        private String name;

        /**
         * 登录方式描述
         */
        private String description;
    }

    @Data
    public static class SecurityConfig {

        private Boolean requireRedis = SimpleIamServerConstant.DEFAULT_SECURITY_DEPENDENCY_REQUIRED;

        private Boolean requireCache = SimpleIamServerConstant.DEFAULT_SECURITY_DEPENDENCY_REQUIRED;

        private Boolean requireLimiter = SimpleIamServerConstant.DEFAULT_SECURITY_DEPENDENCY_REQUIRED;

        private Boolean requireLock = SimpleIamServerConstant.DEFAULT_SECURITY_DEPENDENCY_REQUIRED;
    }

    @Data
    public static class TokenConfig {

        /**
         * Token 格式：jwt（默认，JWS）或 jwe（JWS 外层 AES-256 加密）
         */
        private String format = SimpleIamServerConstant.TOKEN_FORMAT_JWT;

        private String keyId = SimpleIamServerConstant.DEFAULT_JWT_KEY_ID;

        private Integer accessExpiresIn = SimpleIamServerConstant.DEFAULT_ACCESS_TOKEN_EXPIRES_IN;

        private Integer refreshExpiresIn = SimpleIamServerConstant.DEFAULT_REFRESH_TOKEN_EXPIRES_IN;

        private String publicKey;

        private String privateKey;

        /**
         * AES-256 密钥（Base64 编码的 32 字节），仅 format=jwe 时必填
         */
        private String encryptionKey;
    }

    @Data
    public static class SessionConfig {

        /**
         * 滑动空闲超时（秒）：活跃即续，空闲该时长会话判死
         */
        private Integer expiresIn = SimpleIamServerConstant.DEFAULT_SESSION_EXPIRES_IN;

        /**
         * 绝对上限（秒）：自 issuedAt 起的最长会话时长，到期强制重认证；须大于 expiresIn
         */
        private Integer absoluteExpiresIn = SimpleIamServerConstant.DEFAULT_SESSION_ABSOLUTE_EXPIRES_IN;

        /**
         * 续期节流窗（秒）：距上次续期超过该窗口的活跃请求才落库，空闲用户零写
         */
        private Integer renewThreshold = SimpleIamServerConstant.DEFAULT_SESSION_RENEW_THRESHOLD;

        /**
         * spring-session namespace；缺省为 sure-auth-iam:spring-session:{me}
         */
        private String redisNamespace;

        /**
         * 托管 Redis 禁止 CONFIG 命令时设为 noop，跳过 keyspace notifications 探测
         */
        private String redisConfigureAction;
    }

    @Data
    public static class LoginConfig {

        /**
         * 连续失败上限，超过则锁定
         */
        private Integer maxAttempts = SimpleIamServerConstant.DEFAULT_LOGIN_MAX_ATTEMPTS;

        /**
         * 锁定时长（分钟）
         */
        private Integer lockMinutes = SimpleIamServerConstant.DEFAULT_LOGIN_LOCK_MINUTES;
    }

    @Data
    public static class CaptchaConfig {

        /**
         * 验证码开关：失败达阈值后要求人机校验，默认启用；未装配验证码组件时降级放行（不阻断登录）
         */
        private Boolean enabled = SimpleIamServerConstant.DEFAULT_CAPTCHA_ENABLED;

        /**
         * 触发人机验证的登录失败次数（达到即要求验证码；锁定兜底仍由登录失败上限控制）
         */
        private Integer threshold = SimpleIamServerConstant.DEFAULT_CAPTCHA_THRESHOLD;
    }

    @Data
    public static class MfaConfig {

        /**
         * MFA 开关：1.0.0 仅 SPI 预留，默认关闭
         */
        private Boolean enabled = SimpleIamServerConstant.DEFAULT_RISK_CHECK_ENABLED;
    }

    @Data
    public static class PasswordConfig {

        private Integer minLength = SimpleIamServerConstant.DEFAULT_PASSWORD_MIN_LENGTH;

        private Integer maxLength = SimpleIamServerConstant.DEFAULT_PASSWORD_MAX_LENGTH;

        private Boolean requireUppercase = SimpleIamServerConstant.DEFAULT_PASSWORD_CHARACTER_TYPE_REQUIRED;

        private Boolean requireLowercase = SimpleIamServerConstant.DEFAULT_PASSWORD_CHARACTER_TYPE_REQUIRED;

        private Boolean requireDigit = SimpleIamServerConstant.DEFAULT_PASSWORD_CHARACTER_TYPE_REQUIRED;

        private Boolean requireSpecial = SimpleIamServerConstant.DEFAULT_PASSWORD_CHARACTER_TYPE_REQUIRED;

        /**
         * 须改密强制拦截开关（侵入型防线组件可关）：默认开启，测试环境批量建号
         * 场景可关；生产关闭等于放弃首登强制改密防线，不得关闭
         */
        private Boolean mustChangeEnforcement = SimpleIamServerConstant.DEFAULT_MUST_CHANGE_PASSWORD_ENFORCEMENT;
    }

    @Data
    public static class RegistrationConfig {

        /**
         * 开放注册开关：默认关闭，走管理员建账
         */
        private Boolean open = SimpleIamServerConstant.DEFAULT_REGISTRATION_OPEN;
    }

    @Data
    public static class PasswordResetConfig {

        /**
         * 自助重置开关：默认关闭，走管理员重置
         */
        private Boolean selfService = SimpleIamServerConstant.DEFAULT_PASSWORD_RESET_SELF_SERVICE;
    }

    @Data
    public static class BootstrapConfig {

        private Boolean enabled = SimpleIamServerConstant.DEFAULT_BOOTSTRAP_ENABLED;

        private String username = SimpleIamServerConstant.DEFAULT_ADMIN_USERNAME;

        /**
         * 部署注入的一次性管理员恢复码，不得写入版本库或日志
         */
        private String recoveryCode;

        /**
         * 部署注入的管理员恢复后新密码，不得写入版本库或日志
         */
        private String recoveryPassword;

        /**
         * 内置可信应用列表（平台自身子应用，随引导注册进门户；已存在的应用编码跳过不覆盖）。
         * 列表整体可被配置覆盖/追加（如 AKSK 管理台）；单项 entry 置空则跳过该项。
         */
        private List<BuiltInApplicationConfig> builtInApplications = new ArrayList<>(
                List.of(defaultIamAdminApplication()));

        /**
         * IAM 管理台默认引导项（本机联调形态，部署方按环境覆盖 entry/apiBase）
         */
        private static BuiltInApplicationConfig defaultIamAdminApplication() {
            BuiltInApplicationConfig application = new BuiltInApplicationConfig();
            application.setApplicationCode(SimpleIamServerConstant.BUILT_IN_APPLICATION_IAM);
            application.setApplicationName("IAM 管理台");
            application.setDescription("IAM 平台内置管理台，负责用户、组织、角色权限与可信应用的统一管理");
            application.setRoutePrefix(SimpleIamServerConstant.BUILT_IN_APPLICATION_IAM_PORTAL_ROUTE_PREFIX);
            application.setEntry(SimpleIamServerConstant.DEFAULT_BOOTSTRAP_BUILT_IN_APPLICATION_ENTRY);
            application.setApiBase(null);
            application.setMenus(new ArrayList<>(List.of(
                    menu("dashboard", "仪表盘", "/"),
                    menu("users", "用户管理", "/users"),
                    menu("organizations", "组织与成员", "/organizations"),
                    menu("user-groups", "协作组管理", "/user-groups"),
                    menu("roles", "角色管理", "/roles"),
                    menu("trusted-applications", "可信应用", "/trusted-applications"),
                    menu("messages", "站内信", "/messages"))));
            return application;
        }

        private static BuiltInApplicationMenuConfig menu(String code, String name, String route) {
            BuiltInApplicationMenuConfig menu = new BuiltInApplicationMenuConfig();
            menu.setCode(code);
            menu.setName(name);
            menu.setRoute(route);
            return menu;
        }

        @Data
        public static class BuiltInApplicationConfig {

            /**
             * 应用编码（唯一键，已存在则跳过该项引导）
             */
            private String applicationCode;

            /**
             * 应用展示名
             */
            private String applicationName;

            /**
             * 应用描述
             */
            private String description;

            /**
             * 门户路由前缀（缺省按 /micro/{applicationCode} 拼接）
             */
            private String routePrefix;

            /**
             * 微前端 entry 地址（置空则跳过该项）
             */
            private String entry;

            /**
             * 后端 API 基地址
             */
            private String apiBase;

            /**
             * 门户菜单（按列表顺序排序）
             */
            private List<BuiltInApplicationMenuConfig> menus = new ArrayList<>();
        }

        @Data
        public static class BuiltInApplicationMenuConfig {

            /**
             * 菜单项编码
             */
            private String code;

            /**
             * 菜单项名称
             */
            private String name;

            /**
             * 子应用内相对路由
             */
            private String route;
        }
    }

    @Data
    public static class AdminConfig {

        /**
         * Vue Web 品牌配置
         */
        private ThemeConfig theme = new ThemeConfig();
    }

    @Data
    public static class ThemeConfig {

        /**
         * 品牌名称
         */
        private String brandName = SimpleIamServerConstant.DEFAULT_WEB_BRAND_NAME;

        /**
         * 自定义 Logo 地址
         */
        private String logoUrl;

        /**
         * 自定义 CSS 地址
         */
        private String customCssUrl;

        /**
         * 自定义 JS 地址
         */
        private String customJsUrl;
    }
}
