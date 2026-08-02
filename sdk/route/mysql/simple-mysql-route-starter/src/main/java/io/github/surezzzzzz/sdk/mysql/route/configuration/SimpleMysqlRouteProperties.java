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

    private boolean enable;
    private Map<String, ClusterConfig> clusters = new LinkedHashMap<>();
    private List<RouteRule> rules = new ArrayList<>();

    @Data
    @ToString(exclude = {"host", "connectionProperties", "datasources"})
    public static class ClusterConfig {
        private String host;
        private int port = SimpleMysqlRouteConstant.DEFAULT_CLUSTER_PORT;
        private String driverClassName = SimpleMysqlRouteConstant.DEFAULT_DRIVER_CLASS_NAME;
        private Map<String, String> connectionProperties = new LinkedHashMap<>();
        private Map<String, DatasourceConfig> datasources = new LinkedHashMap<>();
    }

    @Data
    @ToString(exclude = {"username", "password"})
    public static class DatasourceConfig {
        private String database;
        private String username;
        private String password;
    }

    @Data
    public static class RouteRule {
        private String pattern;
        private String matchType = SimpleMysqlRouteConstant.DEFAULT_ROUTE_MATCH_TYPE;
        private String datasourceKey;
        private int priority = SimpleMysqlRouteConstant.DEFAULT_RULE_PRIORITY;
        private boolean enable = SimpleMysqlRouteConstant.DEFAULT_ROUTE_RULE_ENABLE;
    }
}
