package io.github.surezzzzzz.sdk.iam.client.constant;

/**
 * Simple IAM Client 常量（契约值逐字对位 server rest 契约）。
 *
 * @author surezzzzzz
 */
public final class SimpleIamClientConstant {

    /**
     * 配置前缀（仅 enable 与 base-url 两键；连接池超时由 aksk 底座管理）
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.iam.client";

    // ==================== 配置相关常量 ====================
    /**
     * openapi 基路径（三族 controller 共用根）
     */
    public static final String API_BASE_PATH = "/iam/api";

    // ==================== API 路径常量 ====================
    /**
     * 用户族资源段
     */
    public static final String RESOURCE_USERS = "users";

    /**
     * 资源路径段：可信应用目录（/iam/api/applications，1.3.7）
     */
    public static final String RESOURCE_APPLICATIONS = "applications";

    /**
     * Portal 可信应用路由投影资源段（与 applications 组合为 /iam/api/portal/applications）。
     */
    public static final String RESOURCE_PORTAL = "portal";

    /**
     * 资源路径段：站内信（/iam/api/messages，1.3.7）
     */
    public static final String RESOURCE_MESSAGES = "messages";

    /**
     * 资源路径段：会话（/iam/api/sessions，1.3.7）
     */
    public static final String RESOURCE_SESSIONS = "sessions";

    /**
     * 资源路径段：权限清单（/iam/api/permissions，1.3.7）
     */
    public static final String RESOURCE_PERMISSIONS = "permissions";

    /**
     * 资源路径段：角色目录（/iam/api/role-catalog，1.3.7）
     */
    public static final String RESOURCE_ROLE_CATALOG = "role-catalog";

    /**
     * 资源路径段：统计聚合（/iam/api/statistics，1.3.7）
     */
    public static final String RESOURCE_STATISTICS = "statistics";

    /**
     * 资源路径段：协作组（/iam/api/user-groups，1.3.7）
     */
    public static final String RESOURCE_USER_GROUPS = "user-groups";

    /**
     * 查询参数名：公开主体（1.3.7）
     */
    public static final String QUERY_SUBJECT_ID = "subjectId";
    /**
     * 部门族资源段
     */
    public static final String RESOURCE_DEPARTMENTS = "departments";
    /**
     * 受委托角色族资源段
     */
    public static final String RESOURCE_ROLES = "roles";
    /**
     * 受委托角色规则资源段
     */
    public static final String RESOURCE_AUTHORIZATION_RULES = "authorization-rules";
    /**
     * 组织目录资源段
     */
    public static final String RESOURCE_ORGANIZATION_DIRECTORIES = "organization-directories";
    /**
     * 组织目录成员资源段
     */
    public static final String RESOURCE_MEMBERS = "members";
    /**
     * 目标应用资源段
     */
    public static final String RESOURCE_TARGET_APPLICATIONS = "target-applications";
    /**
     * 权限清单资源段
     */
    public static final String RESOURCE_PERMISSION_MANIFEST = "permission-manifest";
    /**
     * 启用动作段
     */
    public static final String RESOURCE_ENABLE = "enable";
    /**
     * 停用动作段
     */
    public static final String RESOURCE_DISABLE = "disable";
    /**
     * 重置密码动作段
     */
    public static final String RESOURCE_RESET_PASSWORD = "reset-password";
    /**
     * 乐观并发条件头（受委托角色规则写/部门挂载用；值形如 open-role:&lt;id&gt;:&lt;revision&gt;，
     * revision 来源于上次响应模型或 ETag 头）
     */
    public static final String HEADER_IF_MATCH = "If-Match";

    // ==================== 协议头常量 ====================
    /**
     * 乐观并发版本响应头
     */
    public static final String HEADER_ETAG = "ETag";
    /**
     * 用户分页（Spring Page wire 形态）字段
     */
    public static final String FIELD_CONTENT = "content";

    // ==================== wire 字段常量 ====================
    /**
     * Spring Page 总元素数字段
     */
    public static final String FIELD_TOTAL_ELEMENTS = "totalElements";
    /**
     * Spring Page 总页数字段
     */
    public static final String FIELD_TOTAL_PAGES = "totalPages";
    /**
     * Spring Page 当前页码字段（0 起）
     */
    public static final String FIELD_NUMBER = "number";
    /**
     * Spring Page 页大小字段
     */
    public static final String FIELD_SIZE = "size";
    /**
     * 受委托角色分页（server 自持形态）条目字段
     */
    public static final String FIELD_ITEMS = "items";
    /**
     * 受委托角色分页总数字段
     */
    public static final String FIELD_TOTAL = "total";
    /**
     * 受委托角色分页页码字段（1 起）
     */
    public static final String FIELD_PAGE = "page";

    /**
     * Portal 目录全局版本字段。
     */
    public static final String FIELD_CATALOG_REVISION = "catalogRevision";
    /**
     * Portal 路由投影字段。
     */
    public static final String FIELD_PORTAL_ENABLED = "portalEnabled";
    public static final String FIELD_ROUTE_PREFIX = "routePrefix";
    public static final String FIELD_ENTRY = "entry";
    public static final String FIELD_API_BASE = "apiBase";
    public static final String FIELD_TARGET_ORIGIN = "targetOrigin";
    public static final String FIELD_CONFIG_VERSION = "configVersion";
    public static final String FIELD_FORWARD_AUTHORIZATION = "forwardAuthorization";
    public static final String FIELD_FORWARD_COOKIE_NAMES = "forwardCookieNames";
    /**
     * 1.3.8 Portal 路由契约：透传原请求的 Authorization 请求头。
     */
    public static final String FORWARD_AUTHORIZATION_REQUEST = "REQUEST";
    /**
     * 查询参数：用户状态过滤
     */
    public static final String QUERY_STATUS = "status";
    /**
     * 查询参数：部门过滤
     */
    public static final String QUERY_DEPARTMENT_ID = "departmentId";
    /**
     * 查询参数：关键字过滤
     */
    public static final String QUERY_KEYWORD = "keyword";
    /**
     * 查询参数：页码
     */
    public static final String QUERY_PAGE = "page";
    /**
     * 查询参数：页大小
     */
    public static final String QUERY_SIZE = "size";

    /**
     * Portal 目录全局版本查询参数。
     */
    public static final String QUERY_CATALOG_REVISION = "catalogRevision";
    /**
     * 响应不符合协议
     */
    public static final String MESSAGE_PROTOCOL_ERROR = "IAM 服务响应不符合协议";

    // ==================== 消息常量 ====================
    /**
     * 配置不合法
     */
    public static final String MESSAGE_INVALID_CONFIGURATION = "IAM Client 配置不合法";

    private SimpleIamClientConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}
