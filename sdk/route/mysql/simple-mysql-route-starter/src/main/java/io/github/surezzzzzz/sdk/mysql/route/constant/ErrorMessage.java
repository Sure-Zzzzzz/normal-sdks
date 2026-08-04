package io.github.surezzzzzz.sdk.mysql.route.constant;

/**
 * MySQL Route 错误消息。
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    public static final String CONFIG_INVALID = "MySQL Route 配置无效: %s";
    public static final String DATASOURCE_NOT_FOUND = "MySQL Route datasource [%s] 未注册";
    public static final String ROUTE_NOT_FOUND = "MySQL Route routeKey [%s] 未命中规则";
    public static final String CONTEXT_INVALID = "MySQL Route 当前线程没有有效路由上下文";
    public static final String CALLBACK_INVALID = "MySQL Route callback 不能为空";
    public static final String ROUTING_RESOURCE_INVALID = "MySQL Route 路由 DataSource 与 JDBC Template 必须保持一致";
    public static final String PRIMARY_DATASOURCE_INVALID = "MySQL Route 主数据源无效";
    public static final String DATASOURCE_CREATE_FAILED = "创建 MySQL Route datasource [%s] 失败";
    public static final String TRANSACTION_CROSS_DATASOURCE = "当前事务已绑定其他 datasource，不能切换到 [%s]";
    public static final String REGISTRY_DESTROYED = "MySQL Route Registry 已销毁";
    public static final String ROUTE_KEYS_INVALID = "MySQL Route routeKeys 不能为空";
    public static final String OPERATION_CROSS_DATASOURCE = "多个 routeKey 解析到了不同 datasource";
    public static final String DIRECT_TARGET_IN_TRANSACTION = "事务内不能绕过 RoutingDataSource 直接访问目标";
    public static final String ROUTE_KEY_INVALID = "MySQL Route routeKey 不能为空";
    public static final String TARGET_INITIALIZE_FAILED = "MySQL Route 目标初始化失败";
    public static final String DATASOURCE_UNAVAILABLE = "MySQL Route DataSource 连接不可用";
    public static final String DATASOURCE_VERIFY_FAILED = "MySQL Route DataSource 连接验证失败";
    public static final String DATASOURCE_CLOSE_FAILED = "MySQL Route DataSource 关闭失败";
    public static final String USER_CREDENTIAL_CONNECTION_UNSUPPORTED =
            "MySQL Route 不支持调用方指定连接凭据";
    public static final String PROPERTIES_REQUIRED = "properties 不能为空";
    public static final String DATASOURCES_REQUIRED = "datasources 不能为空";
    public static final String PRIMARY_DATASOURCE_REQUIRED = "primary-datasource 不能为空";
    public static final String PRIMARY_DATASOURCE_NOT_FOUND = "primary-datasource 未指向已配置 datasource";
    public static final String DATASOURCE_KEY_INVALID = "datasource 名称不能为空";
    public static final String DATASOURCE_URL_REQUIRED = "datasource [%s] url 不能为空";
    public static final String DATASOURCE_DRIVER_REQUIRED = "datasource [%s] driver-class-name 不能为空";
    public static final String DATASOURCE_USERNAME_REQUIRED = "datasource [%s] username 不能为空";
    public static final String DATASOURCE_PASSWORD_REQUIRED = "datasource [%s] password 不能为空";
    public static final String RULE_PATTERN_REQUIRED = "第 %s 条规则 pattern 不能为空";
    public static final String RULE_MATCH_TYPE_INVALID = "第 %s 条规则 match-type 无效";
    public static final String RULE_DATASOURCE_NOT_FOUND = "第 %s 条规则引用的 datasource 不存在";
    public static final String RULE_PATTERN_COMPILE_FAILED = "第 %s 条规则 pattern 无法编译";
    public static final String SHA_256_UNAVAILABLE = "SHA-256 算法不可用";
    public static final String ROUTING_BEAN_NAME_CONFLICT = "MySQL Route 路由 Bean 名称冲突";
    public static final String HIKARI_CONFIGURATION_INVALID = "datasource [%s] Hikari 配置无效";
    public static final String BOOT_JDBC_TEMPLATE_REQUIRED = "MySQL Route 需要 Spring Boot 标准 JDBC Template";

    private ErrorMessage() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }
}
