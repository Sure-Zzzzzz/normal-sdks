package io.github.surezzzzzz.sdk.mysql.route.constant;

/**
 * MySQL Route 常量。
 *
 * @author surezzzzzz
 */
public final class SimpleMysqlRouteConstant {

    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.mysql.route";
    public static final String PRIMARY_DATASOURCE_PROPERTY = ".primary-datasource";
    public static final String DATASOURCE_KEY_PATTERN = "[A-Za-z0-9][A-Za-z0-9_-]*";
    public static final String HIKARI_CONNECTION_TIMEOUT_PROPERTY = "connectionTimeout";
    public static final String HIKARI_VALIDATION_TIMEOUT_PROPERTY = "validationTimeout";
    public static final String HIKARI_CONNECTION_TEST_QUERY_PROPERTY = "connectionTestQuery";
    public static final String HIKARI_CONNECTION_INIT_SQL_PROPERTY = "connectionInitSql";
    public static final String HIKARI_MAXIMUM_POOL_SIZE_PROPERTY = "maximumPoolSize";
    public static final String HIKARI_MINIMUM_IDLE_PROPERTY = "minimumIdle";
    public static final String HIKARI_IDLE_TIMEOUT_PROPERTY = "idleTimeout";
    public static final String HIKARI_MAX_LIFETIME_PROPERTY = "maxLifetime";
    public static final String HIKARI_INITIALIZATION_FAIL_TIMEOUT_PROPERTY = "initializationFailTimeout";
    public static final String HIKARI_AUTO_COMMIT_PROPERTY = "autoCommit";
    public static final String HIKARI_READ_ONLY_PROPERTY = "readOnly";
    public static final String HIKARI_TRANSACTION_ISOLATION_PROPERTY = "transactionIsolation";
    public static final String HIKARI_CATALOG_PROPERTY = "catalog";
    public static final String HIKARI_SCHEMA_PROPERTY = "schema";
    public static final String HIKARI_ISOLATE_INTERNAL_QUERIES_PROPERTY = "isolateInternalQueries";
    public static final String HIKARI_ALLOW_POOL_SUSPENSION_PROPERTY = "allowPoolSuspension";
    public static final String HIKARI_POOL_NAME_PROPERTY = "poolName";
    public static final String HIKARI_LEAK_DETECTION_THRESHOLD_PROPERTY = "leakDetectionThreshold";
    public static final String HIKARI_REGISTER_MBEANS_PROPERTY = "registerMbeans";
    public static final String HIKARI_EXCEPTION_OVERRIDE_CLASS_NAME_PROPERTY = "exceptionOverrideClassName";
    public static final String HIKARI_DATA_SOURCE_PROPERTIES_PREFIX = "dataSourceProperties.";
    public static final String ROUTING_DATASOURCE_BEAN_NAME = "mysqlRouteRoutingDataSource";
    public static final String BOOT_JDBC_TEMPLATE_BEAN_NAME = "jdbcTemplate";
    public static final String BOOT_NAMED_PARAMETER_JDBC_TEMPLATE_BEAN_NAME = "namedParameterJdbcTemplate";
    public static final String JDBC_TEMPLATE_BEAN_NAME = "mysqlRouteJdbcTemplate";
    public static final String NAMED_PARAMETER_JDBC_TEMPLATE_BEAN_NAME = "mysqlRouteNamedParameterJdbcTemplate";
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
    public static final String SHA_256 = "SHA-256";
    public static final int SHA_256_DIGEST_LENGTH = 64;
    public static final char DIGIT_MIN = '0';
    public static final char DIGIT_MAX = '9';
    public static final char LOWERCASE_HEX_MIN = 'a';
    public static final char LOWERCASE_HEX_MAX = 'f';

    private SimpleMysqlRouteConstant() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }
}
