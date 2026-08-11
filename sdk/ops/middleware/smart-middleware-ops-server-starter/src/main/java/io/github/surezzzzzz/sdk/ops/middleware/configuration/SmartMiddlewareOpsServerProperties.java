package io.github.surezzzzzz.sdk.ops.middleware.configuration;

import io.github.surezzzzzz.sdk.ops.middleware.constant.SmartMiddlewareOpsServerConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Middleware Ops Server 配置。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SmartMiddlewareOpsServerConstant.CONFIG_PREFIX)
public class SmartMiddlewareOpsServerProperties {

    /**
     * 是否启用默认 Server 链路。
     */
    private Boolean enable = SmartMiddlewareOpsServerConstant.DEFAULT_ENABLED;
    /**
     * API 根路径。
     */
    private String apiBasePath = SmartMiddlewareOpsServerConstant.DEFAULT_API_BASE_PATH;
    /**
     * Thymeleaf 页面根路径。
     */
    private String uiBasePath = SmartMiddlewareOpsServerConstant.DEFAULT_UI_BASE_PATH;
    /**
     * Windows AD LDAP 认证配置。
     */
    private Ldap ldap = new Ldap();
    /**
     * 审计读侧配置。
     */
    private Audit audit = new Audit();
    /**
     * 启动期数据源的自由展示标签。
     */
    private DatasourceTags datasourceTags = new DatasourceTags();
    /**
     * 查询限制配置。
     */
    private Query query = new Query();
    /**
     * 并发限制配置。
     */
    private Concurrency concurrency = new Concurrency();

    @PostConstruct
    public void validateRoutePaths() {
        validateRoutePath("api-base-path", apiBasePath);
        validateRoutePath("ui-base-path", uiBasePath);
        if (containsRoutePath(apiBasePath, uiBasePath) || containsRoutePath(uiBasePath, apiBasePath)) {
            throw new IllegalStateException("api-base-path 与 ui-base-path 必须使用不重叠的路径");
        }
        validateAudit();
        validateQuery();
        validateConcurrency();
    }

    private void validateAudit() {
        if (audit == null || audit.getMaxRangeDays() == null || audit.getMaxRangeDays() < 30) {
            throw new IllegalStateException("audit.max-range-days 不能小于 Search Starter 默认的 30 天");
        }
        if (audit.getMaxOffset() == null || audit.getMaxOffset() <= 0) {
            throw new IllegalStateException("audit.max-offset 必须大于 0");
        }
    }

    private void validateQuery() {
        if (query == null || query.getDefaultSize() == null || query.getDefaultSize() <= 0
                || query.getMaxSize() == null || query.getMaxSize() < query.getDefaultSize()
                || query.getMaxDslLength() == null || query.getMaxDslLength() <= 0
                || query.getMaxSqlLength() == null || query.getMaxSqlLength() <= 0
                || query.getMaxColumns() == null || query.getMaxColumns() <= 0
                || query.getMaxCellLength() == null || query.getMaxCellLength() <= 0
                || query.getMaxResourceNameLength() == null || query.getMaxResourceNameLength() <= 0
                || query.getMaxResponseLength() == null || query.getMaxResponseLength() <= 0
                || query.getDeadlineMillis() == null || query.getDeadlineMillis() <= 0) {
            throw new IllegalStateException("query 限制必须为正数，且 max-size 不得小于 default-size");
        }
    }

    private void validateConcurrency() {
        if (concurrency == null || concurrency.getGlobal() == null || concurrency.getGlobal() <= 0
                || concurrency.getDatasource() == null || concurrency.getDatasource() <= 0) {
            throw new IllegalStateException("concurrency 限制必须大于 0");
        }
    }

    private void validateRoutePath(String name, String path) {
        if (path == null || path.trim().isEmpty() || !path.startsWith("/") || path.length() > 1 && path.endsWith("/")) {
            throw new IllegalStateException(name + " 必须是无末尾斜杠的绝对路径");
        }
    }

    private boolean containsRoutePath(String parent, String child) {
        return "/".equals(parent) || parent.equals(child) || child.startsWith(parent + "/");
    }

    /**
     * Windows AD LDAP 认证配置。
     */
    @Data
    public static class Ldap {
        /**
         * 是否启用生产 LDAP 认证。
         */
        private Boolean enabled = SmartMiddlewareOpsServerConstant.DEFAULT_LDAP_ENABLED;
        /**
         * LDAP 服务地址，包含根 DN。
         */
        private String url;
        /**
         * 用于搜索用户的服务账号 DN。
         */
        private String managerDn;
        /**
         * 用于搜索用户的服务账号密码。
         */
        private String managerPassword;
        /**
         * 用户搜索基准 DN。
         */
        private String userSearchBase = SmartMiddlewareOpsServerConstant.DEFAULT_LDAP_USER_SEARCH_BASE;
        /**
         * 用户搜索过滤器。
         */
        private String userSearchFilter = SmartMiddlewareOpsServerConstant.DEFAULT_LDAP_USER_SEARCH_FILTER;
    }

    /**
     * 审计读侧配置。
     */
    @Data
    public static class Audit {
        /**
         * 是否启用审计读取。
         */
        private Boolean enabled = SmartMiddlewareOpsServerConstant.DEFAULT_AUDIT_READ_ENABLED;
        /**
         * 是否启用审计异步写入。
         */
        private Boolean writeEnabled = SmartMiddlewareOpsServerConstant.DEFAULT_AUDIT_WRITE_ENABLED;
        /**
         * 审计自定义时间范围最大天数。
         */
        private Integer maxRangeDays = SmartMiddlewareOpsServerConstant.DEFAULT_AUDIT_MAX_RANGE_DAYS;
        /**
         * 审计 offset 分页最大可读取记录数。
         */
        private Integer maxOffset = SmartMiddlewareOpsServerConstant.DEFAULT_AUDIT_MAX_OFFSET;
    }

    /**
     * 启动期数据源的自由展示标签配置。
     */
    @Data
    public static class DatasourceTags {
        /**
         * Elasticsearch 数据源标签。
         */
        private Map<String, String> elasticsearch = new HashMap<>();
        /**
         * Redis 数据源标签。
         */
        private Map<String, String> redis = new HashMap<>();
        /**
         * Kafka 数据源标签。
         */
        private Map<String, String> kafka = new HashMap<>();
        /**
         * MySQL 数据源标签。
         */
        private Map<String, String> mysql = new HashMap<>();
    }

    /**
     * 查询限制配置。
     */
    @Data
    public static class Query {
        /**
         * 默认结果数量。
         */
        private Integer defaultSize = SmartMiddlewareOpsServerConstant.DEFAULT_RESULT_SIZE;
        /**
         * 单次最大结果数量。
         */
        private Integer maxSize = SmartMiddlewareOpsServerConstant.MAX_RESULT_SIZE;
        /**
         * JSON DSL 最大字符数。
         */
        private Integer maxDslLength = SmartMiddlewareOpsServerConstant.DEFAULT_MAX_DSL_LENGTH;
        /**
         * MySQL 受控 SELECT 最大字符数。
         */
        private Integer maxSqlLength = SmartMiddlewareOpsServerConstant.DEFAULT_MAX_SQL_LENGTH;
        /**
         * MySQL 受控 SELECT 最大返回列数。
         */
        private Integer maxColumns = SmartMiddlewareOpsServerConstant.DEFAULT_MAX_COLUMNS;
        /**
         * 单个 MySQL 查询值最大字符数。
         */
        private Integer maxCellLength = SmartMiddlewareOpsServerConstant.DEFAULT_MAX_CELL_LENGTH;
        /**
         * 索引、topic、消费组等手工资源标识最大字符数。
         */
        private Integer maxResourceNameLength = SmartMiddlewareOpsServerConstant.DEFAULT_MAX_KEY_LENGTH;
        /**
         * 单次响应中业务数据最大字符数。
         */
        private Integer maxResponseLength = SmartMiddlewareOpsServerConstant.DEFAULT_MAX_VALUE_LENGTH;
        /**
         * 单个 capability 截止时间毫秒数。
         */
        private Long deadlineMillis = SmartMiddlewareOpsServerConstant.DEFAULT_DEADLINE_MILLIS;
    }

    /**
     * 并发限制配置。
     */
    @Data
    public static class Concurrency {
        /**
         * 全局瞬时并发上限。
         */
        private Integer global = SmartMiddlewareOpsServerConstant.DEFAULT_GLOBAL_CONCURRENCY;
        /**
         * 单中间件单数据源瞬时并发上限。
         */
        private Integer datasource = SmartMiddlewareOpsServerConstant.DEFAULT_DATASOURCE_CONCURRENCY;
    }
}
