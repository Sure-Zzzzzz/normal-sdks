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
