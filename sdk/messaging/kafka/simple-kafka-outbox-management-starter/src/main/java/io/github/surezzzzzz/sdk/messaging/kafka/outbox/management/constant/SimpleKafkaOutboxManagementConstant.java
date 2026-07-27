package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.constant;

/**
 * Simple Kafka Outbox Management 常量。
 *
 * @author surezzzzzz
 */
public final class SimpleKafkaOutboxManagementConstant {

    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.messaging.kafka.outbox.management";
    public static final String CONFIG_PROPERTY_ENABLE = "enable";
    public static final String CONFIG_PROPERTY_UI_ENABLE = "ui.enable";
    public static final String CONFIG_PROPERTY_UI_REDIRECT_ROOT = "ui.redirect-root";
    public static final String DEFAULT_TABLE_NAME = "simple_kafka_outbox";
    public static final boolean DEFAULT_ENABLE = false;
    public static final boolean DEFAULT_UI_ENABLE = true;
    public static final boolean DEFAULT_UI_REDIRECT_ROOT = true;
    public static final String DEFAULT_UI_BASE_PATH = "/outbox-management";
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int DEFAULT_MAX_PAGE_SIZE = 100;
    public static final int MAX_TABLE_NAME_LENGTH = 64;
    public static final int DEFAULT_SECURITY_ORDER = 30;
    public static final String BEAN_NAMED_JDBC_TEMPLATE = "simpleKafkaOutboxManagementNamedParameterJdbcTemplate";
    public static final String BEAN_TRANSACTION_TEMPLATE = "simpleKafkaOutboxManagementTransactionTemplate";
    public static final String BEAN_USER_DETAILS_SERVICE = "simpleKafkaOutboxManagementUserDetailsService";
    public static final String PATH_WILDCARD_SUFFIX = "/**";
    public static final String PATH_ASSETS = "/assets";
    public static final String PATH_ASSETS_WILDCARD = PATH_ASSETS + PATH_WILDCARD_SUFFIX;
    public static final String PATH_BOOTSTRAP_CSS = PATH_ASSETS + "/css/bootstrap.min.css";
    public static final String PATH_MANAGEMENT_UI_CSS = PATH_ASSETS + "/css/management-ui.css";
    public static final String PATH_LOGIN = "/login";
    public static final String PATH_LOGOUT = "/logout";
    public static final String PATH_RECORDS = "/records";
    public static final String PATH_LOCATE = "/locate";
    public static final String VIEW_LOGIN = "outbox-management/login";
    public static final String VIEW_DASHBOARD = "outbox-management/dashboard";
    public static final String VIEW_RECORDS = "outbox-management/records";
    public static final String VIEW_DETAIL = "outbox-management/detail";
    public static final String VIEW_ERROR = "outbox-management/error";
    public static final String ADMIN_ROLE = "OUTBOX_MANAGEMENT_ADMIN";
    public static final String SQL_TABLE_NAME_PATTERN = "[A-Za-z0-9_]+";

    private SimpleKafkaOutboxManagementConstant() {
        throw new UnsupportedOperationException("常量类不能实例化");
    }
}
