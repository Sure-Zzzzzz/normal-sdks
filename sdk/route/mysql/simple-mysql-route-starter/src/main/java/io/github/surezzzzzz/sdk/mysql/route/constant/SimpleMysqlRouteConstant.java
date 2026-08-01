package io.github.surezzzzzz.sdk.mysql.route.constant;

/**
 * MySQL Route 常量。
 *
 * @author surezzzzzz
 */
public final class SimpleMysqlRouteConstant {

    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.mysql.route";
    public static final String ROUTING_DATASOURCE_BEAN_NAME = "mysqlRouteRoutingDataSource";
    public static final String JDBC_TEMPLATE_BEAN_NAME = "mysqlRouteJdbcTemplate";
    public static final String NAMED_PARAMETER_JDBC_TEMPLATE_BEAN_NAME = "mysqlRouteNamedParameterJdbcTemplate";
    public static final int MIN_CLUSTER_PORT = 1;
    public static final int DEFAULT_CLUSTER_PORT = 3306;
    public static final int MAX_CLUSTER_PORT = 65535;
    public static final int DEFAULT_RULE_PRIORITY = 1000;
    public static final String DEFAULT_ROUTE_MATCH_TYPE = "exact";
    public static final boolean DEFAULT_ROUTE_RULE_ENABLE = true;
    public static final String DEFAULT_DRIVER_CLASS_NAME = "com.mysql.cj.jdbc.Driver";
    public static final String MIDDLEWARE_TYPE_MYSQL = "mysql";
    public static final int AUDIT_STATUS_SUCCESS = 200;
    public static final int AUDIT_STATUS_BAD_REQUEST = 400;
    public static final int AUDIT_STATUS_NOT_FOUND = 404;
    public static final int AUDIT_STATUS_CONFLICT = 409;
    public static final int AUDIT_STATUS_INTERNAL_SERVER_ERROR = 500;
    public static final int CONNECTION_VALIDATION_TIMEOUT_SECONDS = 5;
    public static final String JDBC_URL_PREFIX = "jdbc:mysql://";
    public static final String JDBC_URL_QUERY_START = "?";
    public static final String JDBC_URL_QUERY_SEPARATOR = "&";
    public static final String JDBC_URL_KEY_VALUE_SEPARATOR = "=";
    public static final String UTF_8 = "UTF-8";
    public static final String SHA_256 = "SHA-256";

    private SimpleMysqlRouteConstant() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }
}
