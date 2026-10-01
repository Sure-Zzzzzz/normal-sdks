package io.github.surezzzzzz.sdk.mysql.route.configuration;

import io.github.surezzzzzz.sdk.mysql.route.constant.SimpleMysqlRouteConstant;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MySQL Route 配置。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimpleMysqlRouteConstant.CONFIG_PREFIX)
public class SimpleMysqlRouteProperties {

    /**
     * 是否启用 Route。
     */
    private boolean enable;

    /**
     * 无显式作用域时使用的主数据源名称。
     */
    private String primaryDatasource;

    /**
     * 按调用方定义键登记的数据源。
     */
    private Map<String, DatasourceConfig> datasources = new LinkedHashMap<>();

    /**
     * 业务路由规则。
     */
    private List<RouteRule> rules = new ArrayList<>();

    /**
     * 数据源配置。
     */
    @Data
    @ToString(exclude = {"url", "username", "password", "hikari"})
    public static class DatasourceConfig {

        /**
         * Hikari 数据源使用的 JDBC 地址。
         */
        private String url;

        /**
         * Route 自建数据源使用的连接账号。
         */
        private String username;

        /**
         * Route 自建数据源使用的连接密码。
         */
        private String password;

        /**
         * Route 自建数据源使用的驱动类名。
         */
        private String driverClassName = SimpleMysqlRouteConstant.DEFAULT_DRIVER_CLASS_NAME;

        /**
         * Route 自建 Hikari 连接池参数。
         */
        private Map<String, String> hikari = new LinkedHashMap<>();
    }

    /**
     * 业务路由规则配置。
     */
    @Data
    public static class RouteRule {

        /**
         * 待匹配的业务路由键模式。
         */
        private String pattern;

        /**
         * 路由键匹配方式。
         */
        private String matchType = SimpleMysqlRouteConstant.DEFAULT_ROUTE_MATCH_TYPE;

        /**
         * 命中规则后使用的数据源名称。
         */
        private String datasource;

        /**
         * 规则优先级。
         */
        private int priority = SimpleMysqlRouteConstant.DEFAULT_RULE_PRIORITY;

        /**
         * 是否启用规则。
         */
        private boolean enable = SimpleMysqlRouteConstant.DEFAULT_ROUTE_RULE_ENABLE;
    }
}
