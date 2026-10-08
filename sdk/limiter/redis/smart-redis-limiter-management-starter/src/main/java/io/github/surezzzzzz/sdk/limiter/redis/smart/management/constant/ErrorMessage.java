package io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant;

/**
 * SmartRedisLimiter Management 错误消息
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {
    public static final String TYPED_PROTOCOL_MISMATCH = "服务不是类型化协议(TYPED_V2)，不能使用 v2 接口";
    public static final String TYPED_DIMENSION_NOT_DECLARED = "资源 %s 未声明维度 %s";
    public static final String TYPED_NAMESPACE_MISMATCH = "命名空间与宿主声明不一致，要求 %s";
    public static final String TYPED_CUSTOM_TYPE_NOT_DECLARED = "自定义类型 %s 未在目录声明";
    public static final String TYPED_SERVICE_UNAVAILABLE = "类型化策略服务未装配（缺少 JDBC 数据源）";

    public static final String CONFIG_VALIDATION_FAILED = "SmartRedisLimiter Management 配置验证失败：%s";
    public static final String CONFIG_MODE_INVALID = "mode 只支持 console 或 portal";
    public static final String CONFIG_PORTAL_ENTRY_INVALID = "portal 要求开启 API、关闭 UI 和固定 token";
    public static final String CONFIG_PORTAL_RESOURCE_REQUIRED = "portal 要求公共 Resource 安全链、MVC 权限校验、受保护路径和双来源适配器";
    public static final String ACCESS_DENIED = "无权访问该限流策略资源";
    public static final String REQUEST_INVALID = "请求参数或内容格式非法";
    public static final String METHOD_NOT_ALLOWED = "请求方法不支持";
    public static final String MEDIA_TYPE_NOT_SUPPORTED = "请求内容类型不支持";
    public static final String CONFIG_ENTRY_REQUIRED = "API 和 UI 至少启用一个入口";
    public static final String CONFIG_BASE_PATH_INVALID = "base-path 必须以 / 开头、不以 / 结尾且不包含通配符";
    public static final String CONFIG_BASE_PATH_OVERLAP = "API 和 UI 的 base-path 不能相同或重叠";
    public static final String CONFIG_UI_API_REQUIRED = "UI 开启时 API 必须同时开启";
    public static final String CONFIG_ADMIN_REQUIRED = "UI 开启时管理员用户名和密码不能为空";
    public static final String CONFIG_REST_TOKEN_REQUIRED = "resource-server 显式关闭时 REST 固定 token 不能为空";
    public static final String POLICY_VALIDATION_FAILED = "限流策略校验失败：%s";
    public static final String POLICY_NOT_FOUND = "限流策略不存在";
    public static final String POLICY_IDENTITY_CONFLICT = "相同 serviceCode、resourceCode 和 subject 的策略已存在";
    public static final String POLICY_VERSION_CONFLICT = "限流策略已被其他操作更新";
    public static final String REVISION_OVERFLOW = "服务策略 revision 已达到最大值";
    public static final String PERSISTENCE_FAILED = "限流策略持久化失败";
    public static final String PAGE_UNAVAILABLE = "管理页面暂时无法使用，请稍后重试。";
    public static final String PAGE_QUERY_FAILED = "管理页面策略查询结果无效";
    public static final String SNAPSHOT_FAILED = "限流策略快照构建失败";
    public static final String EVENT_REGISTRATION_FAILED = "管理事件只能在有效写事务内注册";
    public static final String OPERATOR_REQUIRED = "管理操作人不能为空";
    public static final String POLICY_STATE_REQUIRED = "策略启停状态不能为空";
    public static final String PAGE_INVALID = "分页参数非法";

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }
}
