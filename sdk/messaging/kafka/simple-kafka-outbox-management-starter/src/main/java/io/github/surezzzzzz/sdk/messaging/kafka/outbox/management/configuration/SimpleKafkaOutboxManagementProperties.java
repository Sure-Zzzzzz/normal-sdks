package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.configuration;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.constant.SimpleKafkaOutboxManagementConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.exception.KafkaOutboxManagementConfigurationException;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;

/**
 * Simple Kafka Outbox Management 配置。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimpleKafkaOutboxManagementConstant.CONFIG_PREFIX)
public class SimpleKafkaOutboxManagementProperties {
    /**
     * 是否启用。
     */
    private Boolean enable = SimpleKafkaOutboxManagementConstant.DEFAULT_ENABLE;
    /**
     * Outbox 表名。
     */
    private String tableName = SimpleKafkaOutboxManagementConstant.DEFAULT_TABLE_NAME;
    /**
     * DataSource Bean 名。
     */
    private String dataSourceBeanName;
    /**
     * 事务管理器 Bean 名。
     */
    private String transactionManagerBeanName;
    /**
     * 页面配置。
     */
    private UiConfig ui = new UiConfig();
    /**
     * 管理员配置。
     */
    private AdminConfig admin = new AdminConfig();
    /**
     * 分页配置。
     */
    private PageConfig page = new PageConfig();

    /**
     * 初始化并校验配置。
     */
    @PostConstruct
    public void init() {
        if (!Boolean.TRUE.equals(enable) || !Boolean.TRUE.equals(ui.getEnable())) {
            return;
        }
        if (!hasText(tableName) || tableName.length() > SimpleKafkaOutboxManagementConstant.MAX_TABLE_NAME_LENGTH
                || !tableName.matches(SimpleKafkaOutboxManagementConstant.SQL_TABLE_NAME_PATTERN)) {
            throw invalid("table-name 非法");
        }
        if (!hasText(ui.getBasePath()) || !ui.getBasePath().startsWith("/") || ui.getBasePath().endsWith("/")
                || containsWhitespace(ui.getBasePath()) || ui.getBasePath().contains("*") || ui.getBasePath().contains("?")) {
            throw invalid("ui.base-path 非法");
        }
        if (!hasText(admin.getUsername()) || !hasText(admin.getPassword())) {
            throw invalid("管理员凭据不能为空");
        }
        if (page.getMaxSize() == null || page.getMaxSize() < 1
                || page.getMaxSize() > SimpleKafkaOutboxManagementConstant.DEFAULT_MAX_PAGE_SIZE
                || page.getDefaultSize() == null || page.getDefaultSize() < 1
                || page.getDefaultSize() > page.getMaxSize()) {
            throw invalid("分页配置非法");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean containsWhitespace(String value) {
        return value.matches(".*\\s+.*");
    }

    private KafkaOutboxManagementConfigurationException invalid(String reason) {
        return new KafkaOutboxManagementConfigurationException(ErrorCode.CONFIGURATION_INVALID,
                String.format(ErrorMessage.CONFIGURATION_INVALID, reason));
    }

    /**
     * 页面配置。
     */
    @Data
    public static class UiConfig {
        /**
         * 是否启用页面。
         */
        private Boolean enable = SimpleKafkaOutboxManagementConstant.DEFAULT_UI_ENABLE;
        /**
         * 页面根路径。
         */
        private String basePath = SimpleKafkaOutboxManagementConstant.DEFAULT_UI_BASE_PATH;
        /**
         * 是否将根路径重定向到页面根路径。
         */
        private Boolean redirectRoot = SimpleKafkaOutboxManagementConstant.DEFAULT_UI_REDIRECT_ROOT;
    }

    /**
     * 管理员配置。
     */
    @Data
    public static class AdminConfig {
        /**
         * 管理员用户名。
         */
        private String username;
        /**
         * 管理员密码。
         */
        @ToString.Exclude
        private String password;
    }

    /**
     * 分页配置。
     */
    @Data
    public static class PageConfig {
        /**
         * 默认条数。
         */
        private Integer defaultSize = SimpleKafkaOutboxManagementConstant.DEFAULT_PAGE_SIZE;
        /**
         * 最大条数。
         */
        private Integer maxSize = SimpleKafkaOutboxManagementConstant.DEFAULT_MAX_PAGE_SIZE;
    }
}
