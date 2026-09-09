package io.github.surezzzzzz.sdk.auth.iam.server.constant;

/**
 * Error Message Constants
 *
 * @author surezzzzzz
 */
public final class ServerErrorMessage {

    public static final String JWT_CONFIG_ERROR = "JWT配置错误：%s";

    // ==================== 配置错误 ====================
    public static final String JWT_PUBLIC_KEY_NOT_CONFIGURED = "JWT公钥未配置";
    public static final String JWT_PRIVATE_KEY_NOT_CONFIGURED = "JWT私钥未配置";
    public static final String JWT_KEY_CONFIG_EMPTY = "密钥配置不能为空";
    public static final String JWT_KEY_FILE_NOT_FOUND = "密钥文件不存在: %s";
    public static final String JWT_KEY_FILE_LOAD_FAILED = "加载密钥文件失败: %s";
    public static final String JWE_GENERATE_FAILED = "JWE Token 生成失败：%s";

    // ==================== JWE Token 错误 ====================
    public static final String AES_256_KEY_NOT_CONFIGURED = "AES-256 encryption key 未配置（token.format=jwe 时必须配置 token.encryption-key）";
    public static final String AES_256_KEY_FORMAT_ERROR = "AES-256 密钥格式错误，应为 Base64 编码";
    public static final String AES_256_KEY_LENGTH_ERROR = "AES-256 密钥长度错误，期望 %d 字节，实际 %d 字节";
    public static final String TOKEN_FORMAT_UNSUPPORTED = "不支持的 token 格式：%s（仅支持 jwt / jwe）";
    public static final String REDIS_REQUIRED_BUT_DISABLED = "security.require-redis=true 但 Redis Route 依赖未就绪";
    public static final String RESOURCE_SERVER_DISABLED_WITH_PATHS =
            "公共资源层已显式关闭（enabled=false）但 protected-paths 非空：开放 API /iam/api/** 将无链保护，二者只能取一";
    public static final String PROTECTED_PATHS_MISSING_OPEN_API =
            "公共资源层 protected-paths 已配置但未包含 /iam/api/**：%s；开放 API 启用时必须覆盖该路径";
    public static final String REDIS_ROUTE_DEFAULT_SOURCE_INVALID = "redis.route.default-source 必须为 default";
    public static final String REDIS_ROUTE_SINGLE_SOURCE_REQUIRED = "redis.route.sources 必须且只能配置 default";
    public static final String CACHE_REQUIRED_BUT_DISABLED = "security.require-cache=true 但 smart-cache 未启用";
    public static final String LIMITER_REQUIRED_BUT_DISABLED = "security.require-limiter=true 但 smart-redis-limiter 未启用";
    public static final String LOCK_REQUIRED_BUT_DISABLED = "security.require-lock=true 但 simple-redis-lock 未启用";
    public static final String ISSUER_REQUIRED = "issuer不能为空";
    public static final String ISSUER_MUST_BE_ABSOLUTE_URI = "issuer必须是绝对URI";
    public static final String APPLICATION_NAME_REQUIRED = "me不能为空";
    public static final String ACCESS_TOKEN_EXPIRES_IN_INVALID = "token.access-expires-in必须大于0";
    public static final String REFRESH_TOKEN_EXPIRES_IN_INVALID = "token.refresh-expires-in必须大于0";
    public static final String SESSION_EXPIRES_IN_INVALID = "session.expires-in必须大于0";
    public static final String LOGIN_MAX_ATTEMPTS_INVALID = "login.max-attempts必须大于0";
    public static final String LOGIN_LOCK_MINUTES_INVALID = "login.lock-minutes必须大于0";
    public static final String PASSWORD_LENGTH_RANGE_INVALID = "password.min-length必须大于0且不大于password.max-length";
    public static final String PASSWORD_REQUIRED_CHARACTER_TYPES_EXCEED_MAX_LENGTH = "password.max-length不能小于启用的必需字符类型数量";
    public static final String BOOTSTRAP_USERNAME_REQUIRED = "bootstrap.username不能为空";
    public static final String BOOTSTRAP_LOCK_UNAVAILABLE = "管理员引导锁未获取，当前实例不会执行初始化";
    public static final String PASSWORD_TOO_SHORT = "密码长度不足，最少 %d 位";
    public static final String PASSWORD_TOO_LONG = "密码长度超限，最多 %d 位";
    public static final String PASSWORD_UPPERCASE_REQUIRED = "密码必须包含大写字母";
    public static final String PASSWORD_LOWERCASE_REQUIRED = "密码必须包含小写字母";
    public static final String PASSWORD_DIGIT_REQUIRED = "密码必须包含数字";
    public static final String PASSWORD_SPECIAL_REQUIRED = "密码必须包含特殊字符";
    public static final String DATABASE_ERROR = "数据库操作失败：%s";

    // ==================== 数据库 / 缓存错误 ====================
    public static final String CACHE_OPERATION_FAILED = "缓存操作失败：%s";
    public static final String TOKEN_OPERATION_FAILED = "Token操作失败：%s";
    public static final String USER_CREATE_SUCCESS = "用户创建成功";

    // ==================== 用户管理消息 ====================
    public static final String USER_NOT_FOUND = "用户不存在：%s";
    public static final String USER_ALREADY_EXISTS = "用户已存在：%s";
    public static final String USER_DISABLED = "用户已被禁用：%s";
    public static final String LAST_ADMIN_PROTECTED = "最后一个可用 IAM 管理员不能被撤销角色、删除或禁用：%s";
    public static final String DEPARTMENT_NOT_FOUND = "部门不存在：%s";

    // ==================== 部门管理消息 ====================
    public static final String DEPARTMENT_ALREADY_EXISTS = "部门编码已存在：%s";
    public static final String DEPARTMENT_CODE_EMPTY = "部门编码不能为空";
    public static final String DEPARTMENT_NAME_EMPTY = "部门名称不能为空";
    public static final String DEPARTMENT_PARENT_INVALID = "部门父级非法：%s";
    public static final String DEPARTMENT_HAS_CHILD = "部门存在子部门，不能删除：%s";
    public static final String DEPARTMENT_HAS_USER = "部门已被用户引用，不能删除：%s";
    public static final String USER_GROUP_NOT_FOUND = "协作组不存在：%s";

    // ==================== 协作组管理消息 ====================
    public static final String USER_GROUP_ALREADY_EXISTS = "协作组编码已存在：%s";
    public static final String USER_GROUP_CODE_EMPTY = "协作组编码不能为空";
    public static final String USER_GROUP_NAME_EMPTY = "协作组名称不能为空";
    public static final String MESSAGE_NOT_FOUND = "站内信不存在：%s";

    // ==================== 站内信消息 ====================
    public static final String MESSAGE_FORBIDDEN = "无权操作该站内信";
    public static final String MESSAGE_RECIPIENT_EMPTY = "站内信收件人不能为空";
    public static final String MESSAGE_TITLE_INVALID = "站内信标题不能为空且不能超过%d个字符";
    public static final String MESSAGE_CONTENT_INVALID = "站内信内容不能为空且不能超过%d个字符";
    public static final String BAD_CREDENTIALS = "用户名或密码错误";

    // ==================== 认证错误 ====================
    public static final String ACCOUNT_LOCKED = "账号已锁定，请稍后重试或联系管理员";
    public static final String BAD_CREDENTIALS_REMAINING = "用户名或密码错误，剩余 %d 次尝试后将锁定账号";
    public static final String CAPTCHA_REQUIRED = "请完成人机验证后重试";
    public static final String CAPTCHA_INVALID = "验证码错误或已失效，请重新输入";
    public static final String CAPTCHA_PROVIDER_MISSING = "人机验证组件未装配，请联系管理员";
    public static final String MUST_CHANGE_PASSWORD = "首次登录或密码已被重置，请先修改密码";
    public static final String PASSWORD_CHANGE_NOT_ALLOWED = "外部身份源账号的密码由外部系统管理，不支持在本地修改";

    // ==================== 外部身份源登录 ====================
    public static final String EXTERNAL_PROVIDER_NOT_FOUND = "登录方式不存在或未启用：%s";
    public static final String EXTERNAL_PROVIDER_UNAVAILABLE = "外部身份源暂不可用，请稍后重试";
    public static final String EXTERNAL_LOGIN_STATE_INVALID = "登录会话已失效，请重新发起登录";
    public static final String EXTERNAL_CALLBACK_INVALID = "外部登录回调无效，请重新发起登录";
    public static final String EXTERNAL_IDENTITY_NOT_BOUND = "该账号需要管理员绑定后才能登录";
    public static final String EXTERNAL_IDENTITY_USERNAME_INVALID = "外部身份未提供可用的用户名";
    public static final String EXTERNAL_IDENTITY_ALREADY_BOUND = "该外部身份已绑定其他用户：%s";
    public static final String EXTERNAL_IDENTITY_PROVIDER_UNKNOWN = "登录方式未装配，无法绑定：%s";
    public static final String EXTERNAL_IDENTITY_INVALID = "绑定信息无效：登录方式编码与外部 ID 不能为空";
    public static final String REFRESH_TOKEN_REUSE = "Refresh Token 被复用，整个 Token 族已被撤销";
    public static final String AUTHORIZE_CONTEXT_INVALID = "授权交易无效";

    // ==================== 授权交易消息 ====================
    public static final String AUTHORIZE_CONTEXT_EXPIRED = "授权交易已过期";
    public static final String AUTHORIZE_CONTEXT_FORBIDDEN = "当前身份不能操作该授权交易";
    public static final String PERMISSION_DENIED = "权限不足";

    // ==================== 权限消息 ====================
    public static final String PERMISSION_NOT_FOUND = "权限不存在：%s";
    public static final String TRUSTED_APPLICATION_NOT_FOUND = "可信应用不存在：%s";

    // ==================== 可信应用消息 ====================
    public static final String TRUSTED_APPLICATION_REDIRECT_URI_INVALID = "重定向 URI 非法：必须是绝对 URI 且不允许通配符：%s";
    public static final String TRUSTED_APPLICATION_ID_EXISTS = "可信应用 ID 已存在：%s";
    public static final String TRUSTED_APPLICATION_REDIRECT_URI_EMPTY = "授权码模式可信应用至少配置一个 redirect_uri";
    public static final String TRUSTED_APPLICATION_GRANT_TYPE_NOT_ALLOWED = "可信应用仅支持 authorization_code / refresh_token 授权类型，机器凭证请通过 aksk-server 签发 AK/SK";
    public static final String TRUSTED_APPLICATION_CLIENT_TYPE_INVALID = "可信应用客户端类型非法";
    public static final String TRUSTED_APPLICATION_PUBLIC_CLIENT_SECRET_FORBIDDEN = "公共客户端不能配置客户端密钥";
    public static final String TRUSTED_APPLICATION_PUBLIC_CLIENT_AUTH_METHOD_INVALID = "公共客户端只能使用 none 客户端认证方式";
    public static final String TRUSTED_APPLICATION_CONFIDENTIAL_CLIENT_AUTH_METHOD_INVALID = "机密客户端必须使用受支持的密钥认证方式";
    public static final String TRUSTED_APPLICATION_CLIENT_TYPE_CHANGE_FORBIDDEN = "更新可信应用时不能变更客户端类型";
    public static final String TRUSTED_APPLICATION_CODE_EXISTS = "可信应用编码已存在：%s";
    public static final String TRUSTED_APPLICATION_CLIENT_REQUIRED = "创建可信应用必须至少包含一个 initialClient";
    public static final String TRUSTED_APPLICATION_PORTAL_CONFIG_INVALID = "Portal 配置无效：enabled=true 时 entry 不能为空";
    public static final String TRUSTED_APPLICATION_CLIENT_NOT_BELONG = "客户端 %s 不属于应用 %s";
    public static final String TRUSTED_APPLICATION_NOT_FOUND_BY_ID = "可信应用不存在：id=%s";
    public static final String TRUSTED_APPLICATION_CODE_EMPTY = "应用编码不能为空";
    public static final String TRUSTED_APPLICATION_NAME_EMPTY = "应用名称不能为空";
    public static final String TRUSTED_APPLICATION_ICON_INVALID = "应用图标必须使用内置图标编码：%s";
    public static final String TRUSTED_APPLICATION_DELETE_BLOCKED = "内置可信应用不可删除（平台引导注册，如需下线请关闭门户集成）：%s";

    // ==================== 资源验证客户端消息 ====================
    public static final String RESOURCE_VERIFICATION_CLIENT_NOT_FOUND = "资源验证客户端不存在：%s";
    public static final String RESOURCE_VERIFICATION_CLIENT_NOT_BELONG = "资源验证客户端 %s 不属于应用 %s";
    public static final String RESOURCE_VERIFICATION_CLIENT_ID_EXISTS = "资源验证客户端 ID 已存在：%s";
    public static final String RESOURCE_VERIFICATION_CLIENT_REVOKED = "资源验证客户端已撤销，不能轮换密钥：%s";
    public static final String RESOURCE_VERIFICATION_CLIENT_ID_INVALID = "资源验证客户端 ID 非法：不能为空、长度不能超过 100 且不能包含冒号";

    // ==================== 用户应用授权消息 ====================
    public static final String APPLICATION_AUTHORIZATION_NOT_FOUND = "用户 %s 在应用 %s 下没有应用授权";
    public static final String APPLICATION_MANIFEST_NOT_FOUND = "可信应用未登记权限清单：id=%s";
    public static final String APPLICATION_MANIFEST_CONFLICT = "权限清单并发冲突，请重试：applicationId=%s";
    public static final String APPLICATION_AUTHORIZATION_CONTENT_INVALID = "应用授权内容非法：%s";
    public static final String APPLICATION_AUTHORIZATION_CONFLICT = "应用授权并发冲突，请重试：userId=%s, applicationId=%s";

    private ServerErrorMessage() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
