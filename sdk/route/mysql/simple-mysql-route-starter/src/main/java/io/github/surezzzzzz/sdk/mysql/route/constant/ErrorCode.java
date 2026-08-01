package io.github.surezzzzzz.sdk.mysql.route.constant;

/**
 * MySQL Route 错误码。
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    public static final String CONFIG_INVALID = "MYSQL_ROUTE_001";
    public static final String DATASOURCE_NOT_FOUND = "MYSQL_ROUTE_002";
    public static final String ROUTE_NOT_FOUND = "MYSQL_ROUTE_003";
    public static final String CONTEXT_INVALID = "MYSQL_ROUTE_004";
    public static final String CALLBACK_INVALID = "MYSQL_ROUTE_005";
    public static final String DATASOURCE_CREATE_FAILED = "MYSQL_ROUTE_006";
    public static final String TRANSACTION_CROSS_DATASOURCE = "MYSQL_ROUTE_007";
    public static final String REGISTRY_DESTROYED = "MYSQL_ROUTE_008";
    public static final String ROUTE_KEYS_INVALID = "MYSQL_ROUTE_009";
    public static final String OPERATION_CROSS_DATASOURCE = "MYSQL_ROUTE_010";
    public static final String DIRECT_TARGET_IN_TRANSACTION = "MYSQL_ROUTE_011";
    public static final String ROUTE_KEY_INVALID = "MYSQL_ROUTE_012";
    public static final String USER_CREDENTIAL_CONNECTION_UNSUPPORTED = "MYSQL_ROUTE_013";

    private ErrorCode() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }
}
