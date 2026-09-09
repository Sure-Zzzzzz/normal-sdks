package io.github.surezzzzzz.sdk.auth.iam.server.constant;

/**
 * Simple IAM Server Constants
 *
 * @author surezzzzzz
 */
public final class SimpleIamServerConstant {

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.auth.iam.server";

    // ==================== 配置相关常量 ====================
    /**
     * 模块默认启用
     */
    public static final boolean DEFAULT_ENABLE = true;
    /**
     * 默认应用名称（Redis/Cache/Lock key 中的 me 段）
     */
    public static final String DEFAULT_APPLICATION_NAME = "default";
    /**
     * 默认要求 Redis、缓存、限流和分布式锁就绪
     */
    public static final boolean DEFAULT_SECURITY_DEPENDENCY_REQUIRED = true;
    /**
     * 默认 Access Token 有效期（秒）：30 分钟
     */
    public static final int DEFAULT_ACCESS_TOKEN_EXPIRES_IN = 1800;
    /**
     * 默认 Refresh Token 有效期（秒）：10 小时
     */
    public static final int DEFAULT_REFRESH_TOKEN_EXPIRES_IN = 36000;
    /**
     * 默认 Session 有效期（秒）：30 分钟（滑动空闲超时）
     */
    public static final int DEFAULT_SESSION_EXPIRES_IN = 1800;
    /**
     * 默认 Session 绝对上限（秒）：10 小时（自签发起强制重认证，锚 issuedAt 不可续）
     */
    public static final int DEFAULT_SESSION_ABSOLUTE_EXPIRES_IN = 36000;
    /**
     * 默认 Session 续期节流窗（秒）：距上次续期超过该窗口的活跃请求才落库
     */
    public static final int DEFAULT_SESSION_RENEW_THRESHOLD = 300;
    /**
     * 默认过期 Token 定时清理开关：开启
     */
    public static final boolean DEFAULT_CLEANUP_ENABLE = true;
    /**
     * 默认过期 Token 清理调度 cron：每天凌晨 2 点
     */
    public static final String DEFAULT_CLEANUP_CRON = "0 0 2 * * ?";
    /**
     * 默认过期 Token 清理分批单批行数上限
     */
    public static final int DEFAULT_CLEANUP_BATCH_SIZE = 2000;
    /**
     * 默认过期 Token 清理任务分布式锁租约时长（秒）
     */
    public static final int DEFAULT_CLEANUP_LOCK_LEASE_SECONDS = 600;
    /**
     * 过期 Token 清理任务分布式锁 key（多实例互斥）
     */
    public static final String CLEANUP_LOCK_KEY = "iam:server:cleanup:expired-token-lock";
    /**
     * 默认登录失败上限
     */
    public static final int DEFAULT_LOGIN_MAX_ATTEMPTS = 5;
    /**
     * 默认触发人机验证的登录失败次数（达到即要求验证码；上限仍由登录失败上限锁定兜底）
     */
    public static final int DEFAULT_CAPTCHA_THRESHOLD = 2;
    /**
     * 默认登录锁定时长（分钟）
     */
    public static final int DEFAULT_LOGIN_LOCK_MINUTES = 15;
    /**
     * 默认密码最小长度
     */
    public static final int DEFAULT_PASSWORD_MIN_LENGTH = 8;
    /**
     * 默认密码最大长度
     */
    public static final int DEFAULT_PASSWORD_MAX_LENGTH = 64;
    /**
     * 默认要求密码包含大写字母、小写字母、数字和特殊字符
     */
    public static final boolean DEFAULT_PASSWORD_CHARACTER_TYPE_REQUIRED = true;
    /**
     * 默认开启登录渐进验证码（失败达阈值后人机校验；未装配验证码组件时降级放行）
     */
    public static final boolean DEFAULT_CAPTCHA_ENABLED = true;
    /**
     * 默认关闭 MFA（1.0.0 仅 SPI 预留）
     */
    public static final boolean DEFAULT_RISK_CHECK_ENABLED = false;
    /**
     * 默认开启启动引导
     */
    public static final boolean DEFAULT_BOOTSTRAP_ENABLED = true;
    /**
     * 默认管理端页码
     */
    public static final int DEFAULT_ADMIN_PAGE = 1;
    /**
     * 默认管理端页大小
     */
    public static final int DEFAULT_ADMIN_PAGE_SIZE = 20;
    /**
     * 管理端默认页码请求参数值
     */
    public static final String DEFAULT_ADMIN_PAGE_VALUE = "1";
    /**
     * 管理端默认页大小请求参数值
     */
    public static final String DEFAULT_ADMIN_PAGE_SIZE_VALUE = "20";
    /**
     * 默认授权交易有效期（秒）
     */
    public static final int DEFAULT_AUTHORIZE_CONTEXT_EXPIRES_IN = 300;
    /**
     * 每分钟秒数
     */
    public static final int SECONDS_PER_MINUTE = 60;
    /**
     * 启动引导锁租约时长（秒）
     */
    public static final int BOOTSTRAP_LOCK_LEASE_SECONDS = 60;
    /**
     * 启动引导锁业务标识
     */
    public static final String LOCK_KEY_BOOTSTRAP = "bootstrap";
    /**
     * 默认 JWT Key ID
     */
    public static final String DEFAULT_JWT_KEY_ID = "sure-auth-iam-2026";
    /**
     * Servlet 会话中保存的 IAM 会话 ID 属性名
     */
    public static final String SESSION_ATTRIBUTE_IAM_SESSION_ID = "IAM_SESSION_ID";
    /**
     * Servlet 会话中保存的权限版本快照属性名：登录时写入，会话校验时与
     * iam_user.permission_version 对比，不一致则热刷新登录态权限
     */
    public static final String SESSION_ATTRIBUTE_PERMISSION_VERSION = "IAM_PERMISSION_VERSION";
    /**
     * Servlet 会话中保存的"须改密"标记快照属性名：登录时写入 iam_user.must_change_password
     * 真值，会话校验时同步；为 true 时除白名单端点外一律 403 强制先改密
     */
    public static final String SESSION_ATTRIBUTE_MUST_CHANGE_PASSWORD = "IAM_MUST_CHANGE_PASSWORD";

    /**
     * 须改密强制拦截默认开启（测试环境可在配置中关闭；生产不得关闭）
     */
    public static final boolean DEFAULT_MUST_CHANGE_PASSWORD_ENFORCEMENT = true;
    /**
     * Servlet 会话中保存的跳转型登录防重放 state 属性名前缀（后接登录方式编码）
     */
    public static final String SESSION_ATTRIBUTE_EXTERNAL_LOGIN_STATE_PREFIX = "IAM_EXTERNAL_LOGIN_STATE_";
    /**
     * Servlet 会话中保存的跳转型登录回跳目标属性名前缀（后接登录方式编码）；
     * authorize 时随 state 一并暂存，回调成功后取出回跳，保证 SSO 与本地登录的
     * redirect 目标行为一致（如 SAS 授权流携带的 /oauth2/authorize 地址）
     */
    public static final String SESSION_ATTRIBUTE_EXTERNAL_LOGIN_REDIRECT_PREFIX =
            "IAM_EXTERNAL_LOGIN_REDIRECT_";
    /**
     * 本地账号密码登录方式编码
     */
    public static final String LOGIN_PROVIDER_LOCAL_PASSWORD = "local-password";
    /**
     * 外部身份开号模式：首次登录自动创建本地账号
     */
    public static final String PROVISIONING_MODE_JIT = "jit";
    /**
     * 外部身份开号模式：仅允许管理员预绑定的身份登录
     */
    public static final String PROVISIONING_MODE_PRE_BOUND_ONLY = "pre-bound-only";
    /**
     * SAS 授权记录中保存的 IAM 会话 ID 属性名
     */
    public static final String AUTHORIZATION_ATTRIBUTE_IAM_SESSION_ID = "iam_session_id";
    /**
     * Redis / Cache key 总前缀
     */
    public static final String KEY_PREFIX = "sure-auth-iam";

    // ==================== Redis / Cache / Lock Key ====================
    // 规范：所有 key 形如  sure-auth-iam:{businessType}:{me}::{dataId}
    // 其中 {me} 段必须用 Redis Hash Tag 包裹（{<me>}），保证 Cluster 模式下同一 me 的 key 落在同一 slot。
    // 具体 key 拼接由 RedisKeyHelper 统一执行，禁止在业务代码里手拼 key。
    /**
     * 业务类型：会话
     */
    public static final String BUSINESS_SESSION = "session";
    /**
     * 业务类型：授权交易上下文
     */
    public static final String BUSINESS_AUTHORIZE_CONTEXT = "authorize-context";
    /**
     * 业务类型：登录流程
     */
    public static final String BUSINESS_LOGIN_FLOW = "login-flow";
    /**
     * 业务类型：MFA step-up
     */
    public static final String BUSINESS_MFA_STEPUP = "mfa-stepup";
    /**
     * 业务类型：Refresh Token 族
     */
    public static final String BUSINESS_REFRESH_FAMILY = "refresh-family";
    /**
     * 业务类型：密码重置凭证
     */
    public static final String BUSINESS_PASSWORD_RESET = "password-reset";
    /**
     * 业务类型：限流
     */
    public static final String BUSINESS_RATE_LIMIT = "rate-limit";
    /**
     * 业务类型：分布式锁
     */
    public static final String BUSINESS_LOCK = "lock";
    /**
     * 业务类型：spring-session HttpSession
     */
    public static final String BUSINESS_SPRING_SESSION = "spring-session";
    /**
     * 业务类型：站内信 SSE 跨实例广播
     */
    public static final String BUSINESS_MESSAGE_SSE = "message-sse";

    // ==================== spring-session ====================
    /**
     * spring-session namespace 默认值：多实例须共享同一 me 才能互认会话；
     * me 段按 key 规范用 Hash Tag 包裹。
     */
    public static final String SESSION_REDIS_NAMESPACE_DEFAULT =
            KEY_PREFIX + ":" + BUSINESS_SPRING_SESSION + ":{${" + CONFIG_PREFIX + ".me:default}}";
    /**
     * 会话 cookie 名：保持容器时代契约，spring-session 默认 SESSION 不采用
     */
    public static final String SESSION_COOKIE_NAME = "JSESSIONID";
    /**
     * OAuth2 授权缓存名
     */
    public static final String CACHE_OAUTH2_AUTHORIZATION = "oauth2-authorization";

    // ==================== Cache ====================
    /**
     * OAuth2 授权 ID 缓存 key 模板
     */
    public static final String CACHE_KEY_OAUTH2_AUTHORIZATION_ID = "id:%s";
    /**
     * OAuth2 授权 token 缓存 key 模板
     */
    public static final String CACHE_KEY_OAUTH2_AUTHORIZATION_TOKEN = "token:%s:%s";
    /**
     * 未知 token 类型
     */
    public static final String CACHE_TOKEN_TYPE_UNKNOWN = "unknown";
    public static final String TABLE_USER = "iam_user";

    // ==================== 表名 ====================
    public static final String TABLE_ROLE = "iam_role";
    public static final String TABLE_PERMISSION = "iam_permission";
    public static final String TABLE_USER_ROLE = "iam_user_role";
    public static final String TABLE_ROLE_PERMISSION = "iam_role_permission";
    public static final String TABLE_DEPARTMENT = "iam_department";
    public static final String TABLE_USER_GROUP = "iam_user_group";
    public static final String TABLE_USER_GROUP_MEMBER = "iam_user_group_member";
    public static final String TABLE_SESSION = "iam_session";
    public static final String TABLE_AUTHORIZE_CONTEXT = "iam_authorize_context";
    public static final String TABLE_CONSENT = "iam_consent";
    public static final String TABLE_REFRESH_TOKEN_FAMILY = "iam_refresh_token_family";
    public static final String TABLE_PASSWORD_RESET = "iam_password_reset";
    public static final String TABLE_MESSAGE = "iam_message";
    public static final String TABLE_TRUSTED_APPLICATION = "iam_trusted_application";
    public static final String TABLE_TRUSTED_APPLICATION_PORTAL = "iam_trusted_application_portal";
    public static final String TABLE_TRUSTED_APPLICATION_MENU = "iam_trusted_application_menu";
    /**
     * 启用 / 活跃 / 有效
     */
    public static final int STATUS_ACTIVE = 1;

    // ==================== 通用状态 ====================
    /**
     * 禁用 / 撤销 / 已使用
     */
    public static final int STATUS_INACTIVE = 0;
    /**
     * 默认失败登录次数
     */
    public static final int DEFAULT_FAILED_LOGIN_COUNT = 0;
    /**
     * 默认 MFA 等级
     */
    public static final int DEFAULT_MFA_LEVEL = 0;
    /**
     * 默认排序值
     */
    public static final int DEFAULT_SORT_ORDER = 0;
    /**
     * 默认非内置角色或权限
     */
    public static final int DEFAULT_BUILT_IN = 0;
    /**
     * 默认关闭 Portal 集成
     */
    public static final int DEFAULT_PORTAL_INTEGRATION_ENABLED = 0;
    /**
     * 管理端分页最大条数
     */
    public static final int MAX_ADMIN_PAGE_SIZE = 100;
    /**
     * 站内信标题最大长度
     */
    public static final int MESSAGE_TITLE_MAX_LENGTH = 128;
    /**
     * 站内信内容最大长度
     */
    public static final int MESSAGE_CONTENT_MAX_LENGTH = 2000;
    /**
     * 默认密码重置凭证有效期（分钟）
     */
    public static final int DEFAULT_PASSWORD_RESET_TOKEN_EXPIRES_MINUTES = 30;
    /**
     * 部署恢复凭据的固定标识
     */
    public static final String DEPLOYMENT_RECOVERY_CREDENTIAL_ID = "deployment-recovery";
    /**
     * 部署恢复凭据的审计发起人标识
     */
    public static final String DEPLOYMENT_RECOVERY_REQUESTED_BY = "deployment-recovery";
    /**
     * 管理域角色
     */
    public static final String ROLE_IAM_ADMIN = "ROLE_iam_admin";

    // ==================== 权限 ====================
    /**
     * 默认权限类型：页面权限（创建权限未指定类型时使用）
     */
    public static final String DEFAULT_PERMISSION_TYPE = "page";

    // ==================== 权限类型 ====================
    /**
     * 权限类型分隔：用于权限编码命名空间，如 iam:user:read
     */
    public static final String PERMISSION_CODE_SEPARATOR = ":";
    /**
     * 内置权限编码：IAM 用户管理页面
     */
    public static final String BUILT_IN_PERMISSION_USER_PAGE = "iam:user:page";

    // ==================== 内置权限编码 ====================
    /**
     * 内置权限编码：IAM 角色管理页面
     */
    public static final String BUILT_IN_PERMISSION_ROLE_PAGE = "iam:role:page";
    /**
     * 内置权限编码：IAM 站内信管理页面
     */
    public static final String BUILT_IN_PERMISSION_MESSAGE_PAGE = "iam:message:page";
    /**
     * 内置权限编码：IAM 可信应用管理页面
     */
    public static final String BUILT_IN_PERMISSION_TRUSTED_APPLICATION_PAGE = "iam:trusted-application:page";
    /**
     * 内置权限编码：IAM 部门管理页面
     */
    public static final String BUILT_IN_PERMISSION_DEPARTMENT_PAGE = "iam:department:page";
    /**
     * 内置权限编码：IAM 协作组管理页面
     */
    public static final String BUILT_IN_PERMISSION_USER_GROUP_PAGE = "iam:user-group:page";
    /**
     * 内置权限编码：IAM 用户管理接口
     */
    public static final String BUILT_IN_PERMISSION_USER_API = "iam:user:api";
    /**
     * 内置权限编码：IAM 角色管理接口
     */
    public static final String BUILT_IN_PERMISSION_ROLE_API = "iam:role:api";
    /**
     * 内置权限编码：IAM 权限管理接口
     */
    public static final String BUILT_IN_PERMISSION_PERMISSION_API = "iam:permission:api";
    /**
     * 内置权限编码：IAM 站内信接口
     */
    public static final String BUILT_IN_PERMISSION_MESSAGE_API = "iam:message:api";
    /**
     * 内置权限编码：IAM 可信应用管理接口
     */
    public static final String BUILT_IN_PERMISSION_TRUSTED_APPLICATION_API = "iam:trusted-application:api";
    /**
     * 内置权限编码：IAM 部门管理接口
     */
    public static final String BUILT_IN_PERMISSION_DEPARTMENT_API = "iam:department:api";
    /**
     * 内置权限编码：IAM 协作组管理接口
     */
    public static final String BUILT_IN_PERMISSION_USER_GROUP_API = "iam:user-group:api";
    /**
     * 内置权限编码：IAM 仪表盘统计接口
     */
    public static final String BUILT_IN_PERMISSION_DASHBOARD_API = "iam:dashboard:api";
    /**
     * 内置权限编码：IAM 会话管理接口
     */
    public static final String BUILT_IN_PERMISSION_SESSION_API = "iam:session:api";
    /**
     * 内置权限编码：IAM 全量数据范围
     */
    public static final String BUILT_IN_PERMISSION_DATA_ALL = "iam:data:all";
    /**
     * 内置 DATA 资源：IAM 用户（数据范围按部门维度约束）
     */
    public static final String DATA_RESOURCE_IAM_USER = "iam:user";
    /**
     * DATA 资源动作：读
     */
    public static final String DATA_RESOURCE_ACTION_READ = "read";
    /**
     * DATA 资源动作：写
     */
    public static final String DATA_RESOURCE_ACTION_WRITE = "write";
    /**
     * DATA 资源维度：部门
     */
    public static final String DATA_RESOURCE_DIMENSION_DEPARTMENT_ID = "departmentId";
    /**
     * 管理台入口门禁权限集合：iam_admin 角色 + 全部页面权限码。
     * 持任一者可进入 /iam/admin/** 管理台，方法级 @PreAuthorize 仍逐端点强制；
     * 纯 api 权限码（无 page 码）不进门（进台后无任何可渲染页面）。
     */
    public static final String[] ADMIN_CONSOLE_ENTRANCE_AUTHORITIES = {
            ROLE_IAM_ADMIN,
            BUILT_IN_PERMISSION_USER_PAGE,
            BUILT_IN_PERMISSION_ROLE_PAGE,
            BUILT_IN_PERMISSION_MESSAGE_PAGE,
            BUILT_IN_PERMISSION_TRUSTED_APPLICATION_PAGE,
            BUILT_IN_PERMISSION_DEPARTMENT_PAGE,
            BUILT_IN_PERMISSION_USER_GROUP_PAGE};
    /**
     * 站内信发送目标摘要模板
     */
    public static final String TEMPLATE_MESSAGE_TARGET_SUMMARY = "用户:%d,部门:%d,协作组:%d,包含子部门:%s";

    // ==================== 站内信 ====================
    /**
     * Web 统一 API 路径
     */
    public static final String PATH_RESOURCE_API = "/iam/resource/**";
    /**
     * Web 统一 API 路径
     */
    public static final String PATH_WEB_API = "/iam/web/**";

    // ==================== API 路径（SecurityFilterChain antMatcher） ====================
    /**
     * Web CSRF Token API 路径
     */
    public static final String PATH_WEB_AUTH_CSRF = "/iam/web/auth/csrf";
    /**
     * Web 登录 API 路径
     */
    public static final String PATH_WEB_AUTH_LOGIN = "/iam/web/auth/login";
    /**
     * Web 登录方式 API 路径
     */
    public static final String PATH_WEB_AUTH_PROVIDERS = "/iam/web/auth/providers";
    /**
     * Web 人机验证挑战 API 路径（公开，登录前可取题）
     */
    public static final String PATH_WEB_AUTH_CAPTCHA = "/iam/web/auth/captcha";
    /**
     * Web 外部身份源授权跳转 API 路径（公开，登录发起入口）
     */
    public static final String PATH_WEB_AUTH_AUTHORIZE = "/iam/web/auth/authorize/**";
    /**
     * Web 外部身份源回调 API 路径（公开，外部 IdP 302 落点，state 校验防重放）
     */
    public static final String PATH_WEB_AUTH_CALLBACK = "/iam/web/auth/callback/**";
    /**
     * Web 品牌信息 API 路径（公开，登录前可读）
     */
    public static final String PATH_WEB_BRANDING = "/iam/web/branding";
    /**
     * OAuth2 协议登录页（Vue SPA 路由，登录后携带 redirect 参数跳回 /oauth2/authorize）
     */
    public static final String PATH_OAUTH2_LOGIN = "/login";
    /**
     * OAuth2 协议 Consent 页（Vue SPA 路由）
     */
    public static final String PATH_OAUTH2_CONSENT = "/consent";
    /**
     * OAuth2 Consent 信息接口路径
     */
    public static final String PATH_WEB_OAUTH2_CONSENT_INFO = "/iam/web/oauth2/consent-info";
    /**
     * Portal 侧边栏可访问应用列表接口路径（已认证用户，按授权过滤）
     */
    public static final String PATH_WEB_PORTAL_ACCESSIBLE = "/iam/web/portal/accessible-applications";
    /**
     * Portal 路由前缀模板：/app/{applicationCode}，服务端按应用编码生成
     */
    public static final String PORTAL_ROUTE_PREFIX_TEMPLATE = "/app/%s";
    /**
     * 管理域 API 路径
     */
    public static final String PATH_ADMIN_API = "/iam/admin/**";
    /**
     * 开放 API 路径（AKSK 凭证主体，公共资源层鉴权链接管）
     */
    public static final String PATH_OPEN_API = "/iam/api/**";
    /**
     * IAM 应用 API 路径
     */
    public static final String PATH_IAM = "/iam/**";
    /**
     * 容器错误分派路径（Spring Boot BasicErrorController；SecurityFilterChain 放行，
     * 否则 sendError 的 ERROR dispatch 会落兜底 denyAll 链被覆盖成 403）
     */
    public static final String PATH_ERROR = "/error";
    /**
     * 兜底路径
     */
    public static final String PATH_FALLBACK = "/**";
    public static final String KEY_ALGORITHM_RSA = "RSA";

    // ==================== JWT 密钥相关常量 ====================
    public static final String KEY_PATH_PREFIX_CLASSPATH = "classpath:";
    public static final String KEY_PATH_PREFIX_FILE = "file:";
    public static final String KEY_PATH_PREFIX_UNIX = "/";
    public static final String PEM_BEGIN_MARKER = "-----BEGIN";
    public static final String PEM_REGEX_BEGIN = "-----BEGIN [A-Z ]+-----";
    public static final String PEM_REGEX_END = "-----END [A-Z ]+-----";
    public static final String REGEX_WHITESPACE = "\\s+";
    public static final String EMPTY_STRING = "";
    public static final String LINE_SEPARATOR = "\n";
    /**
     * Token 格式：JWS（默认，标准 OAuth2/OIDC，resource server 只需公钥验签）
     */
    public static final String TOKEN_FORMAT_JWT = "jwt";

    // ==================== Token 格式 ====================
    /**
     * Token 格式：JWE（JWS 外层 AES-256 加密，payload 机密；需在 auth/resource 间共享 AES 密钥）
     */
    public static final String TOKEN_FORMAT_JWE = "jwe";
    /**
     * JWE 密钥加密算法：AES-256 GCM 密钥包装
     */
    public static final String JWE_KEY_ENCRYPTION_ALGORITHM = "A256GCMKW";

    // ==================== JWE 加密算法 ====================
    /**
     * JWE 内容加密算法：AES-256 GCM
     */
    public static final String JWE_CONTENT_ENCRYPTION_ALGORITHM = "A256GCM";
    /**
     * JWE content type：内嵌 JWT
     */
    public static final String JWE_CONTENT_TYPE_JWT = "JWT";
    /**
     * AES-256 密钥字节数
     */
    public static final int AES_256_KEY_LENGTH = 32;
    public static final String DEFAULT_ADMIN_USERNAME = "admin";

    // ==================== 默认管理员 ====================
    /**
     * 默认 Vue Web 品牌名
     */
    public static final String DEFAULT_WEB_BRAND_NAME = "统一认证中心";
    /**
     * 内置角色编码：IAM 管理员
     */
    public static final String BUILT_IN_ROLE_IAM_ADMIN = "iam_admin";
    /**
     * 内置角色编码：普通用户（无管理权限，供业务账号分配基础身份）
     */
    public static final String BUILT_IN_ROLE_IAM_USER = "iam_user";
    /**
     * 内置部门编码：默认根部门（组织树首个节点，供成员与下级部门挂载）
     */
    public static final String BUILT_IN_DEPARTMENT_ROOT = "root";
    /**
     * 内置可信应用编码：IAM 管理台（平台自身首个可信应用，随引导注册进门户）
     */
    public static final String BUILT_IN_APPLICATION_IAM = "iam";
    /**
     * 内置可信应用默认门户路由前缀（与 iam-admin-web 的 vite base 对齐，统一挂在门户 /app/ 前缀下）
     */
    public static final String BUILT_IN_APPLICATION_IAM_PORTAL_ROUTE_PREFIX = "/app/iam";
    /**
     * 内置可信应用引导默认 entry（同 origin 相对路径，随门户所在域名自适应；
     * 显式指向 index.html 静态文件——nginx 对子应用路由路径的 HTML 回退指向门户，
     * entry 若只写到目录会被回退拦到门户 HTML 导致 qiankun 加载失败；
     * dev 联调直挂子应用时按环境覆盖为绝对地址）
     */
    public static final String DEFAULT_BOOTSTRAP_BUILT_IN_APPLICATION_ENTRY =
            BUILT_IN_APPLICATION_IAM_PORTAL_ROUTE_PREFIX + "/index.html";
    /**
     * 注册默认走管理员建账（open registration 关闭）
     */
    public static final boolean DEFAULT_REGISTRATION_OPEN = false;

    // ==================== 注册 / 密码重置默认策略 ====================
    /**
     * 密码重置默认走管理员重置（self-service 关闭）
     */
    public static final boolean DEFAULT_PASSWORD_RESET_SELF_SERVICE = false;

    private SimpleIamServerConstant() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
