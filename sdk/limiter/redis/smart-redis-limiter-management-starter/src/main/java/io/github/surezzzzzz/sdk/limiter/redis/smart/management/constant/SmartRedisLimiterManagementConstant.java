package io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant;

/**
 * SmartRedisLimiter Management 常量
 *
 * @author surezzzzzz
 */
public final class SmartRedisLimiterManagementConstant {

    // ==================== 部署形态与三权 ====================
    /**
     * 保留内嵌页面和旧对外认证的形态。
     */
    public static final String MODE_CONSOLE = "console";
    /**
     * 独立门户与公共资源认证形态。
     */
    public static final String MODE_PORTAL = "portal";
    /**
     * 部署形态配置字段。
     */
    public static final String CONFIG_FIELD_MODE = "mode";
    /**
     * 策略管理页面权限。
     */
    public static final String PAGE_POLICY = "smartLimiterPolicy:page";
    /**
     * 分页、详情和能力查询 API 权限。
     */
    public static final String API_POLICY_READ = "smartLimiterPolicy:read";
    /**
     * 策略变更 API 权限。
     */
    public static final String API_POLICY_WRITE = "smartLimiterPolicy:write";
    /**
     * 运行端快照 API 权限。
     */
    public static final String API_SNAPSHOT_READ = "smartLimiterPolicySnapshot:read";
    /**
     * 策略 DATA 资源码。
     */
    public static final String DATA_POLICY_RESOURCE = "limiter-policy";
    /**
     * DATA 读取动作。
     */
    public static final String DATA_READ = "read";
    /**
     * DATA 写入动作。
     */
    public static final String DATA_WRITE = "write";
    /**
     * DATA 服务范围维度。
     */
    public static final String DATA_SERVICE_DIMENSION = "serviceCode";
    /**
     * 公共资源安全链的既有 Bean 名。
     */
    public static final String RESOURCE_SECURITY_CHAIN_BEAN = "resourceServerSecurityFilterChain";
    /**
     * 公共资源 API 权限拦截器配置 Bean 名。
     */
    public static final String RESOURCE_MVC_BEAN = "resourceServerWebMvcConfigurer";
    /**
     * 可选公共资源配置类，用于缺依赖的明确失败关闭。
     */
    public static final String RESOURCE_PROPERTIES_CLASS = "io.github.surezzzzzz.sdk.auth.resource.server.configuration.ResourceServerProperties";
    /**
     * 可选公共资源引擎类。
     */
    public static final String RESOURCE_ENGINE_CLASS = "io.github.surezzzzzz.sdk.auth.resource.server.support.ResourceServerEngine";
    /**
     * Portal 首版双来源最少适配器数。
     */
    public static final int PORTAL_MIN_SOURCE_COUNT = 2;
    /**
     * 人员能力结果禁止缓存。
     */
    public static final String CACHE_CONTROL_NO_STORE = "no-store";
    /**
     * 主键或查询 SQL 的服务范围谓词。
     */
    public static final String SQL_CONDITION_DATA_SCOPE = " AND service_code IN (:allowedServiceCodes)";
    /**
     * 服务范围命名参数。
     */
    public static final String PARAM_ALLOWED_SERVICE_CODES = "allowedServiceCodes";
    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX =
            "io.github.surezzzzzz.sdk.limiter.redis.smart.management";

    // ==================== 配置 ====================
    /**
     * 总开关字段名
     */
    public static final String CONFIG_FIELD_ENABLE = "enable";
    /**
     * 默认关闭
     */
    public static final boolean DEFAULT_ENABLE = false;
    /**
     * 默认 API 开关
     */
    public static final boolean DEFAULT_API_ENABLE = false;
    /**
     * 默认页面开关
     */
    public static final boolean DEFAULT_UI_ENABLE = false;
    /**
     * 默认 API 根路径
     */
    public static final String DEFAULT_API_BASE_PATH = "/api";
    /**
     * simple-aksk-resource-server 配置前缀，用于判断 resource-server 是否接管对外 REST 链
     */
    public static final String RESOURCE_SERVER_CONFIG_PREFIX =
            "io.github.surezzzzzz.sdk.auth.aksk.resource.server";
    /**
     * Spring 属性占位符：API 根路径
     */
    public static final String PLACEHOLDER_API_BASE_PATH =
            "${" + CONFIG_PREFIX + ".api.base-path:" + DEFAULT_API_BASE_PATH + "}";
    /**
     * 默认页面根路径
     */
    public static final String DEFAULT_UI_BASE_PATH = "/admin";
    /**
     * Spring 属性占位符：页面根路径
     */
    public static final String PLACEHOLDER_UI_BASE_PATH =
            "${" + CONFIG_PREFIX + ".ui.base-path:" + DEFAULT_UI_BASE_PATH + "}";
    /**
     * 默认安全链顺序
     */
    public static final int DEFAULT_SECURITY_ORDER = 30;
    /**
     * 默认分页大小
     */
    public static final int DEFAULT_PAGE_SIZE = 20;
    /**
     * 最大分页大小
     */
    public static final int DEFAULT_MAX_PAGE_SIZE = 100;
    /**
     * 策略快照 API 后缀
     */
    public static final String PATH_POLICY_SNAPSHOT = "/v1/policy/snapshot";

    // ==================== HTTP ====================
    /**
     * 策略快照读取权限 scope
     */
    public static final String POLICY_READ_SCOPE = "smart-redis-limiter:policy:read";
    /**
     * 策略管理权限 scope
     */
    public static final String POLICY_WRITE_SCOPE = "smart-redis-limiter:policy:write";
    /**
     * 策略快照读取鉴权表达式
     */
    public static final String POLICY_READ_EXPRESSION = "#context['scope'] != null && #context['scope'] matches '(^|.*\\s)"
            + POLICY_READ_SCOPE + "(\\s.*|$)'";
    /**
     * 策略管理鉴权表达式
     */
    public static final String POLICY_WRITE_EXPRESSION = "#context['scope'] != null && #context['scope'] matches '(^|.*\\s)"
            + POLICY_WRITE_SCOPE + "(\\s.*|$)'";
    /**
     * 管理 API 后缀
     */
    public static final String PATH_POLICY_ADMIN = "/admin/v1/policy";
    /**
     * 登录页后缀
     */
    public static final String PATH_LOGIN = "/login";
    /**
     * admin 路径前缀
     */
    public static final String PATH_ADMIN_PREFIX = "/admin";
    /**
     * 路径通配符后缀
     */
    public static final String PATH_WILDCARD_SUFFIX = "/**";
    /**
     * 登出成功标识查询参数
     */
    public static final String QUERY_PARAM_LOGOUT_SUCCESS = "?logout";
    /**
     * 策略页面后缀
     */
    public static final String PATH_POLICY_PAGE = "/policies";
    /**
     * serviceCode 查询参数
     */
    public static final String QUERY_PARAM_SERVICE_CODE = "serviceCode";
    /**
     * If-None-Match 请求头
     */
    public static final String HEADER_IF_NONE_MATCH = "If-None-Match";
    /**
     * ETag 响应头
     */
    public static final String HEADER_ETAG = "ETag";
    /**
     * 策略 REST 固定 token 请求头
     */
    public static final String HEADER_POLICY_TOKEN = "X-Smart-Redis-Limiter-Policy-Token";
    /**
     * 固定 token 客户端身份
     */
    public static final String POLICY_TOKEN_PRINCIPAL = "smart-redis-limiter-policy-client";
    /**
     * 固定 token 客户端权限
     */
    public static final String POLICY_TOKEN_AUTHORITY = "ROLE_SMART_REDIS_LIMITER_POLICY_CLIENT";
    /**
     * Cache-Control 响应头
     */
    public static final String HEADER_CACHE_CONTROL = "Cache-Control";
    /**
     * no-cache 值
     */
    public static final String CACHE_CONTROL_NO_CACHE = "no-cache";
    /**
     * 管理员角色
     */
    public static final String ADMIN_ROLE = "ADMIN";
    /**
     * 登出路径后缀
     */
    public static final String PATH_LOGOUT = "/logout";
    /**
     * 页面资源路径后缀
     */
    public static final String PATH_ASSETS = "/assets";
    /**
     * 页面资源通配符后缀
     */
    public static final String PATH_ASSETS_WILDCARD = PATH_ASSETS + PATH_WILDCARD_SUFFIX;
    /**
     * Bootstrap 样式资源路径
     */
    public static final String PATH_BOOTSTRAP_CSS = PATH_ASSETS + "/css/bootstrap.min.css";
    /**
     * Management 页面样式资源路径
     */
    public static final String PATH_MANAGEMENT_UI_CSS = PATH_ASSETS + "/css/management-ui.css";
    /**
     * Bootstrap 脚本资源路径
     */
    public static final String PATH_BOOTSTRAP_JS = PATH_ASSETS + "/js/bootstrap.bundle.min.js";
    /**
     * 页面模型：策略列表
     */
    public static final String MODEL_ATTRIBUTE_ITEMS = "items";
    /**
     * 页面模型：当前页码
     */
    public static final String MODEL_ATTRIBUTE_PAGE = "page";
    /**
     * 页面模型：当前页大小
     */
    public static final String MODEL_ATTRIBUTE_SIZE = "size";
    /**
     * 页面模型：总记录数
     */
    public static final String MODEL_ATTRIBUTE_TOTAL_ELEMENTS = "totalElements";
    /**
     * 页面模型：总页数
     */
    public static final String MODEL_ATTRIBUTE_TOTAL_PAGES = "totalPages";
    /**
     * 页面模型：可显示页码
     */
    public static final String MODEL_ATTRIBUTE_PAGE_NUMBERS = "pageNumbers";
    /**
     * 页面模型：serviceCode 过滤条件
     */
    public static final String MODEL_ATTRIBUTE_SERVICE_CODE = "serviceCode";
    /**
     * 页面模型：resourceCode 过滤条件
     */
    public static final String MODEL_ATTRIBUTE_RESOURCE_CODE = "resourceCode";
    /**
     * 页面模型：subject 过滤条件
     */
    public static final String MODEL_ATTRIBUTE_SUBJECT = "subject";
    /**
     * 页面模型：enabled 过滤条件
     */
    public static final String MODEL_ATTRIBUTE_ENABLED = "enabled";
    /**
     * 页面模型：默认页大小
     */
    public static final String MODEL_ATTRIBUTE_DEFAULT_PAGE_SIZE = "defaultPageSize";
    /**
     * 页面模型：最大页大小
     */
    public static final String MODEL_ATTRIBUTE_MAX_PAGE_SIZE = "maxPageSize";
    /**
     * 页面模型：登录地址
     */
    public static final String MODEL_ATTRIBUTE_LOGIN_URL = "loginUrl";
    /**
     * 页面模型：登出地址
     */
    public static final String MODEL_ATTRIBUTE_LOGOUT_URL = "logoutUrl";
    /**
     * 页面模型：策略列表地址
     */
    public static final String MODEL_ATTRIBUTE_POLICY_PAGE_URL = "policyPageUrl";
    /**
     * 页面模型：管理 API 地址
     */
    public static final String MODEL_ATTRIBUTE_ADMIN_API_URL = "adminApiUrl";
    /**
     * 页面模型：Bootstrap 样式地址
     */
    public static final String MODEL_ATTRIBUTE_BOOTSTRAP_CSS_URL = "bootstrapCssUrl";
    /**
     * 页面模型：Management 页面样式地址
     */
    public static final String MODEL_ATTRIBUTE_MANAGEMENT_UI_CSS_URL = "managementUiCssUrl";
    /**
     * 页面模型：Bootstrap 脚本地址
     */
    public static final String MODEL_ATTRIBUTE_BOOTSTRAP_JS_URL = "bootstrapJsUrl";
    /**
     * 页面模型：提示消息
     */
    public static final String MODEL_ATTRIBUTE_MESSAGE = "message";
    /**
     * 登录页模板
     */
    public static final String VIEW_LOGIN = "smart-redis-limiter/management/login";
    /**
     * 策略列表模板
     */
    public static final String VIEW_POLICY_LIST = "smart-redis-limiter/management/policy-list";
    /**
     * 错误页模板
     */
    public static final String VIEW_ERROR = "smart-redis-limiter/management/error";
    /**
     * SHA-256 算法
     */
    public static final String DIGEST_ALGORITHM_SHA_256 = "SHA-256";

    // ==================== ETag ====================
    /**
     * ETag 前缀
     */
    public static final String ETAG_PREFIX = "srlm-";
    /**
     * ETag 引号
     */
    public static final String ETAG_QUOTE = "\"";
    /**
     * 弱 ETag 前缀
     */
    public static final String ETAG_WEAK_PREFIX = "W/";
    /**
     * 多 ETag 分隔符
     */
    public static final String ETAG_SEPARATOR = ",";
    /**
     * ETag canonical 模板
     */
    public static final String TEMPLATE_ETAG_CANONICAL = "%d:%s:%d:%s";
    /**
     * 十六进制格式
     */
    public static final String FORMAT_HEX_BYTE = "%02x";
    /**
     * revision 初始值
     */
    public static final long INITIAL_REVISION = 0L;

    // ==================== 数据库 ====================
    /**
     * rowVersion 初始值
     */
    public static final long INITIAL_ROW_VERSION = 0L;
    /**
     * 每次递增值
     */
    public static final long REVISION_INCREMENT = 1L;
    /**
     * 布尔真数据库值
     */
    public static final int DATABASE_BOOLEAN_TRUE = 1;
    /**
     * 布尔假数据库值
     */
    public static final int DATABASE_BOOLEAN_FALSE = 0;
    /**
     * 幂等初始化 revision SQL
     */
    public static final String SQL_INITIALIZE_REVISION =
            "INSERT INTO smart_redis_limiter_policy_revision "
                    + "(service_code, revision, published_at) "
                    + "VALUES (:serviceCode, 0, :publishedAt) "
                    + "ON DUPLICATE KEY UPDATE service_code = VALUES(service_code)";
    /**
     * 锁定 revision SQL
     */
    public static final String SQL_SELECT_REVISION_FOR_UPDATE =
            "SELECT service_code, revision, published_at "
                    + "FROM smart_redis_limiter_policy_revision "
                    + "WHERE service_code = :serviceCode FOR UPDATE";
    /**
     * 查询 revision SQL
     */
    public static final String SQL_SELECT_REVISION =
            "SELECT service_code, revision, published_at "
                    + "FROM smart_redis_limiter_policy_revision WHERE service_code = :serviceCode";
    /**
     * 更新 revision SQL
     */
    public static final String SQL_UPDATE_REVISION =
            "UPDATE smart_redis_limiter_policy_revision "
                    + "SET revision = :revision, published_at = :publishedAt "
                    + "WHERE service_code = :serviceCode";
    /**
     * 策略精确查询 SQL
     */
    public static final String SQL_SELECT_POLICY_BY_ID =
            "SELECT id, service_code, resource_code, subject, enabled, row_version, created_at, updated_at "
                    + "FROM smart_redis_limiter_policy WHERE id = :id";
    /**
     * 策略身份查询 SQL
     */
    public static final String SQL_SELECT_POLICY_BY_KEY =
            "SELECT id, service_code, resource_code, subject, enabled, row_version, created_at, updated_at "
                    + "FROM smart_redis_limiter_policy "
                    + "WHERE service_code = :serviceCode AND resource_code = :resourceCode AND subject = :subject";
    /**
     * 新增策略 SQL
     */
    public static final String SQL_INSERT_POLICY =
            "INSERT INTO smart_redis_limiter_policy "
                    + "(service_code, resource_code, subject, enabled, row_version, created_at, updated_at) "
                    + "VALUES (:serviceCode, :resourceCode, :subject, :enabled, :rowVersion, :createdAt, :updatedAt)";
    /**
     * 更新策略 limits 对应版本 SQL
     */
    public static final String SQL_UPDATE_POLICY_VERSION =
            "UPDATE smart_redis_limiter_policy SET row_version = row_version + 1, updated_at = :updatedAt "
                    + "WHERE id = :id AND row_version = :expectedRowVersion";
    /**
     * 更新策略状态 SQL
     */
    public static final String SQL_UPDATE_POLICY_STATE =
            "UPDATE smart_redis_limiter_policy "
                    + "SET enabled = :enabled, row_version = row_version + 1, updated_at = :updatedAt "
                    + "WHERE id = :id AND row_version = :expectedRowVersion";
    /**
     * 删除策略 SQL
     */
    public static final String SQL_DELETE_POLICY =
            "DELETE FROM smart_redis_limiter_policy WHERE id = :id AND row_version = :expectedRowVersion";
    /**
     * 删除 limits SQL
     */
    public static final String SQL_DELETE_LIMITS =
            "DELETE FROM smart_redis_limiter_policy_limit WHERE policy_id = :policyId";
    /**
     * 新增 limit SQL
     */
    public static final String SQL_INSERT_LIMIT =
            "INSERT INTO smart_redis_limiter_policy_limit "
                    + "(policy_id, sort_order, limit_count, limit_window, limit_unit, window_seconds, created_at, updated_at) "
                    + "VALUES (:policyId, :sortOrder, :count, :window, :unit, :windowSeconds, :createdAt, :updatedAt)";
    /**
     * 查询单策略 limits SQL
     */
    public static final String SQL_SELECT_LIMITS_BY_POLICY_ID =
            "SELECT id, policy_id, sort_order, limit_count, limit_window, limit_unit, window_seconds, created_at, updated_at "
                    + "FROM smart_redis_limiter_policy_limit WHERE policy_id = :policyId ORDER BY sort_order";
    /**
     * 查询快照策略 SQL
     */
    public static final String SQL_SELECT_ENABLED_POLICIES =
            "SELECT id, service_code, resource_code, subject, enabled, row_version, created_at, updated_at "
                    + "FROM smart_redis_limiter_policy "
                    + "WHERE service_code = :serviceCode AND enabled = 1 ORDER BY resource_code, subject, id";
    /**
     * 批量查询 limits SQL 前缀
     */
    public static final String SQL_SELECT_LIMITS_BY_POLICY_IDS =
            "SELECT id, policy_id, sort_order, limit_count, limit_window, limit_unit, window_seconds, created_at, updated_at "
                    + "FROM smart_redis_limiter_policy_limit WHERE policy_id IN (:policyIds) "
                    + "ORDER BY policy_id, sort_order";
    /**
     * 分页查询基础 SQL
     */
    public static final String SQL_QUERY_POLICY_BASE =
            "SELECT id, service_code, resource_code, subject, enabled, row_version, created_at, updated_at "
                    + "FROM smart_redis_limiter_policy WHERE 1 = 1";
    /**
     * 统计基础 SQL
     */
    public static final String SQL_COUNT_POLICY_BASE =
            "SELECT COUNT(*) FROM smart_redis_limiter_policy WHERE 1 = 1";
    /**
     * serviceCode 条件
     */
    public static final String SQL_CONDITION_SERVICE_CODE = " AND service_code = :serviceCode";
    /**
     * resourceCode 条件
     */
    public static final String SQL_CONDITION_RESOURCE_CODE = " AND resource_code = :resourceCode";
    /**
     * subject 条件
     */
    public static final String SQL_CONDITION_SUBJECT = " AND subject = :subject";
    /**
     * enabled 条件
     */
    public static final String SQL_CONDITION_ENABLED = " AND enabled = :enabled";
    /**
     * 分页排序
     */
    public static final String SQL_POLICY_PAGE_ORDER =
            " ORDER BY service_code, resource_code, subject, id LIMIT :limit OFFSET :offset";
    public static final String COLUMN_ID = "id";

    // ==================== 数据库列 ====================
    public static final String COLUMN_POLICY_ID = "policy_id";
    public static final String COLUMN_SERVICE_CODE = "service_code";
    public static final String COLUMN_RESOURCE_CODE = "resource_code";
    public static final String COLUMN_SUBJECT = "subject";
    public static final String COLUMN_ENABLED = "enabled";
    public static final String COLUMN_ROW_VERSION = "row_version";
    public static final String COLUMN_CREATED_AT = "created_at";
    public static final String COLUMN_UPDATED_AT = "updated_at";
    public static final String COLUMN_REVISION = "revision";
    public static final String COLUMN_PUBLISHED_AT = "published_at";
    public static final String COLUMN_SORT_ORDER = "sort_order";
    public static final String COLUMN_LIMIT_COUNT = "limit_count";
    public static final String COLUMN_LIMIT_WINDOW = "limit_window";
    public static final String COLUMN_LIMIT_UNIT = "limit_unit";
    public static final String COLUMN_WINDOW_SECONDS = "window_seconds";
    public static final String PARAM_ID = "id";

    // ==================== Map 参数 ====================
    public static final String PARAM_POLICY_ID = "policyId";
    public static final String PARAM_POLICY_IDS = "policyIds";
    public static final String PARAM_SERVICE_CODE = "serviceCode";
    public static final String PARAM_RESOURCE_CODE = "resourceCode";
    public static final String PARAM_SUBJECT = "subject";
    public static final String PARAM_ENABLED = "enabled";
    public static final String PARAM_ROW_VERSION = "rowVersion";
    public static final String PARAM_EXPECTED_ROW_VERSION = "expectedRowVersion";
    public static final String PARAM_CREATED_AT = "createdAt";
    public static final String PARAM_UPDATED_AT = "updatedAt";
    public static final String PARAM_PUBLISHED_AT = "publishedAt";
    public static final String PARAM_REVISION = "revision";
    public static final String PARAM_SORT_ORDER = "sortOrder";
    public static final String PARAM_COUNT = "count";
    public static final String PARAM_WINDOW = "window";
    public static final String PARAM_UNIT = "unit";
    public static final String PARAM_WINDOW_SECONDS = "windowSeconds";
    public static final String PARAM_LIMIT = "limit";
    public static final String PARAM_OFFSET = "offset";

    // ==================== v2 类型化规则（smart_redis_limiter_typed_rule） ====================
    /**
     * 类型化规则身份字段列表（七字段）
     */
    public static final String TYPED_RULE_COLUMNS =
            "id, service_code, resource_code, dimension, selector, namespace, custom_type, object_id, "
                    + "enabled, row_version, created_at, updated_at";
    /**
     * 按主键查询类型化规则 SQL
     */
    public static final String SQL_SELECT_TYPED_RULE_BY_ID =
            "SELECT " + TYPED_RULE_COLUMNS + " FROM smart_redis_limiter_typed_rule WHERE id = :id";
    /**
     * 按七字段身份查询类型化规则 SQL
     */
    public static final String SQL_SELECT_TYPED_RULE_BY_KEY =
            "SELECT " + TYPED_RULE_COLUMNS + " FROM smart_redis_limiter_typed_rule "
                    + "WHERE service_code = :serviceCode AND resource_code = :resourceCode "
                    + "AND dimension = :dimension AND selector = :selector AND namespace = :namespace "
                    + "AND custom_type = :customType AND object_id = :objectId";
    /**
     * 新增类型化规则 SQL（身份不适用的字段以空串入库，保证唯一索引判重）
     */
    public static final String SQL_INSERT_TYPED_RULE =
            "INSERT INTO smart_redis_limiter_typed_rule "
                    + "(service_code, resource_code, dimension, selector, namespace, custom_type, object_id, "
                    + "enabled, row_version, created_at, updated_at) "
                    + "VALUES (:serviceCode, :resourceCode, :dimension, :selector, :namespace, :customType, :objectId, "
                    + ":enabled, :rowVersion, :createdAt, :updatedAt)";
    /**
     * 整体替换类型化规则窗口对应版本 SQL
     */
    public static final String SQL_UPDATE_TYPED_RULE_VERSION =
            "UPDATE smart_redis_limiter_typed_rule SET row_version = row_version + 1, updated_at = :updatedAt "
                    + "WHERE id = :id AND row_version = :expectedRowVersion";
    /**
     * 更新类型化规则启停状态 SQL
     */
    public static final String SQL_UPDATE_TYPED_RULE_STATE =
            "UPDATE smart_redis_limiter_typed_rule "
                    + "SET enabled = :enabled, row_version = row_version + 1, updated_at = :updatedAt "
                    + "WHERE id = :id AND row_version = :expectedRowVersion";
    /**
     * 删除类型化规则 SQL（limits 由外键级联删除）
     */
    public static final String SQL_DELETE_TYPED_RULE =
            "DELETE FROM smart_redis_limiter_typed_rule WHERE id = :id AND row_version = :expectedRowVersion";
    /**
     * 删除类型化规则窗口 SQL
     */
    public static final String SQL_DELETE_TYPED_LIMITS =
            "DELETE FROM smart_redis_limiter_typed_rule_limit WHERE rule_id = :ruleId";
    /**
     * 新增类型化规则窗口 SQL
     */
    public static final String SQL_INSERT_TYPED_LIMIT =
            "INSERT INTO smart_redis_limiter_typed_rule_limit "
                    + "(rule_id, sort_order, limit_count, limit_window, limit_unit, window_seconds, created_at, updated_at) "
                    + "VALUES (:ruleId, :sortOrder, :count, :window, :unit, :windowSeconds, :createdAt, :updatedAt)";
    /**
     * 查询单规则窗口 SQL
     */
    public static final String SQL_SELECT_TYPED_LIMITS_BY_RULE_ID =
            "SELECT id, rule_id, sort_order, limit_count, limit_window, limit_unit, window_seconds, created_at, updated_at "
                    + "FROM smart_redis_limiter_typed_rule_limit WHERE rule_id = :ruleId ORDER BY sort_order";
    /**
     * 快照启用规则查询 SQL
     */
    public static final String SQL_SELECT_ENABLED_TYPED_RULES =
            "SELECT " + TYPED_RULE_COLUMNS + " FROM smart_redis_limiter_typed_rule "
                    + "WHERE service_code = :serviceCode AND enabled = 1 "
                    + "ORDER BY resource_code, dimension, selector, namespace, custom_type, object_id, id";
    /**
     * 批量查询类型化规则窗口 SQL
     */
    public static final String SQL_SELECT_TYPED_LIMITS_BY_RULE_IDS =
            "SELECT id, rule_id, sort_order, limit_count, limit_window, limit_unit, window_seconds, created_at, updated_at "
                    + "FROM smart_redis_limiter_typed_rule_limit WHERE rule_id IN (:ruleIds) "
                    + "ORDER BY rule_id, sort_order";
    /**
     * 类型化规则分页查询基础 SQL
     */
    public static final String SQL_QUERY_TYPED_RULE_BASE =
            "SELECT " + TYPED_RULE_COLUMNS + " FROM smart_redis_limiter_typed_rule WHERE 1 = 1";
    /**
     * 类型化规则统计基础 SQL
     */
    public static final String SQL_COUNT_TYPED_RULE_BASE =
            "SELECT COUNT(*) FROM smart_redis_limiter_typed_rule WHERE 1 = 1";
    /**
     * 类型化规则 dimension 条件
     */
    public static final String SQL_CONDITION_DIMENSION = " AND dimension IN (:dimensions)";
    /**
     * 类型化规则 selector 条件
     */
    public static final String SQL_CONDITION_SELECTOR = " AND selector = :selector";
    /**
     * 类型化规则 namespace 条件
     */
    public static final String SQL_CONDITION_NAMESPACE = " AND namespace = :namespace";
    /**
     * 类型化规则 custom_type 条件
     */
    public static final String SQL_CONDITION_CUSTOM_TYPE = " AND custom_type = :customType";
    /**
     * 类型化规则 object_id 前缀匹配条件（列表筛选按对象检索）
     */
    public static final String SQL_CONDITION_TYPED_OBJECT =
            " AND object_id LIKE :objectId ESCAPE '|'";
    /**
     * 类型化规则分页排序
     */
    public static final String SQL_TYPED_RULE_PAGE_ORDER =
            " ORDER BY service_code, resource_code, dimension, selector, namespace, custom_type, object_id, id "
                    + "LIMIT :limit OFFSET :offset";

    // ==================== v2 数据库列与参数 ====================
    public static final String COLUMN_DIMENSION = "dimension";
    public static final String COLUMN_SELECTOR = "selector";
    public static final String COLUMN_NAMESPACE = "namespace";
    public static final String COLUMN_CUSTOM_TYPE = "custom_type";
    public static final String COLUMN_OBJECT_ID = "object_id";
    public static final String COLUMN_RULE_ID = "rule_id";
    public static final String PARAM_DIMENSION = "dimension";
    public static final String PARAM_DIMENSIONS = "dimensions";
    public static final String PARAM_SELECTOR = "selector";
    public static final String PARAM_NAMESPACE = "namespace";
    public static final String PARAM_CUSTOM_TYPE = "customType";
    public static final String PARAM_OBJECT_ID = "objectId";
    public static final String PARAM_RULE_ID = "ruleId";
    public static final String PARAM_RULE_IDS = "ruleIds";
    /**
     * v2 类型化快照默认策略代次（协议切换时由宿主显式调整）
     */
    public static final long TYPED_POLICY_EPOCH_DEFAULT = 1L;
    /**
     * 目录声明的类型化协议模式编码（与 core 枚举一致）
     */
    public static final String TYPED_CONTROL_MODE = "TYPED_V2";
    /**
     * 类型化管理事件操作人来源标识（83 字符摘要的 sourceId 输入）
     */
    public static final String OPERATOR_SOURCE_ID = "management";
    /**
     * 类型化管理事件操作人主体类型（Portal 操作人是人员）
     */
    public static final String OPERATOR_SUBJECT_TYPE = "HUMAN";
    /**
     * 对象目录检索默认返回上限
     */
    public static final int DEFAULT_OBJECT_QUERY_LIMIT = 20;
    /**
     * 对象目录检索最大返回上限
     */
    public static final int MAX_OBJECT_QUERY_LIMIT = 100;

    private SmartRedisLimiterManagementConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}
