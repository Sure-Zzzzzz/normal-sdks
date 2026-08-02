package io.github.surezzzzzz.sdk.mysql.route.validator;

import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteProperties;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.mysql.route.constant.RouteMatchType;
import io.github.surezzzzzz.sdk.mysql.route.constant.SimpleMysqlRouteConstant;
import io.github.surezzzzzz.sdk.mysql.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.mysql.route.matcher.MySqlRoutePatternMatcher;
import io.github.surezzzzzz.sdk.mysql.route.support.MySqlRouteStringHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

/**
 * MySQL Route 配置校验器。
 *
 * @author surezzzzzz
 */
public class MySqlRoutePropertiesValidator {

    private final MySqlRoutePatternMatcher patternMatcher;

    /**
     * 创建使用指定匹配器校验规则模式的校验器。
     *
     * @param patternMatcher 路由规则匹配器
     */
    public MySqlRoutePropertiesValidator(MySqlRoutePatternMatcher patternMatcher) {
        this.patternMatcher = patternMatcher;
    }

    /**
     * 校验全部 MySQL Route 配置及其目标引用关系。
     *
     * @param properties 待校验配置
     */
    public void validate(SimpleMysqlRouteProperties properties) {
        if (properties == null) {
            fail(ErrorMessage.PROPERTIES_REQUIRED);
        }
        Set<String> datasourceKeys = validateClusters(properties.getClusters());
        validateRules(datasourceKeys, properties.getRules());
    }

    private Set<String> validateClusters(Map<String, SimpleMysqlRouteProperties.ClusterConfig> clusters) {
        if (clusters == null || clusters.isEmpty()) {
            fail(ErrorMessage.CLUSTERS_REQUIRED);
        }
        Set<String> clusterKeys = new HashSet<>();
        Set<String> datasourceKeys = new HashSet<>();
        for (Map.Entry<String, SimpleMysqlRouteProperties.ClusterConfig> entry : clusters.entrySet()) {
            String clusterKey = entry.getKey();
            SimpleMysqlRouteProperties.ClusterConfig cluster = entry.getValue();
            validateCluster(clusterKey, cluster, clusterKeys);
            validateDatasources(clusterKey, cluster.getDatasources(), datasourceKeys);
        }
        return datasourceKeys;
    }

    private void validateCluster(String clusterKey, SimpleMysqlRouteProperties.ClusterConfig cluster,
                                 Set<String> clusterKeys) {
        if (!MySqlRouteStringHelper.hasText(clusterKey) || !clusterKeys.add(clusterKey)) {
            fail(ErrorMessage.CLUSTER_KEY_INVALID);
        }
        if (cluster == null || !MySqlRouteStringHelper.hasText(cluster.getHost())) {
            fail(String.format(ErrorMessage.CLUSTER_HOST_REQUIRED, clusterKey));
        }
        if (cluster.getPort() < SimpleMysqlRouteConstant.MIN_CLUSTER_PORT
                || cluster.getPort() > SimpleMysqlRouteConstant.MAX_CLUSTER_PORT) {
            fail(String.format(ErrorMessage.CLUSTER_PORT_INVALID, clusterKey));
        }
        if (!MySqlRouteStringHelper.hasText(cluster.getDriverClassName())) {
            fail(String.format(ErrorMessage.CLUSTER_DRIVER_REQUIRED, clusterKey));
        }
    }

    private void validateDatasources(String clusterKey,
                                     Map<String, SimpleMysqlRouteProperties.DatasourceConfig> datasources,
                                     Set<String> datasourceKeys) {
        if (datasources == null || datasources.isEmpty()) {
            fail(String.format(ErrorMessage.CLUSTER_DATASOURCES_REQUIRED, clusterKey));
        }
        Set<String> databases = new HashSet<>();
        for (Map.Entry<String, SimpleMysqlRouteProperties.DatasourceConfig> entry : datasources.entrySet()) {
            String datasourceName = entry.getKey();
            String datasourceKey = datasourceKey(clusterKey, datasourceName);
            SimpleMysqlRouteProperties.DatasourceConfig datasource = entry.getValue();
            if (!MySqlRouteStringHelper.hasText(datasourceName)) {
                fail(String.format(ErrorMessage.DATASOURCE_NAME_REQUIRED, clusterKey));
            }
            if (datasource == null) {
                fail(String.format(ErrorMessage.DATASOURCE_DATABASE_REQUIRED, datasourceKey));
            }
            if (!MySqlRouteStringHelper.hasText(datasource.getDatabase())) {
                fail(String.format(ErrorMessage.DATASOURCE_DATABASE_REQUIRED, datasourceKey));
            }
            if (!MySqlRouteStringHelper.hasText(datasource.getUsername())) {
                fail(String.format(ErrorMessage.DATASOURCE_USERNAME_REQUIRED, datasourceKey));
            }
            if (!MySqlRouteStringHelper.hasText(datasource.getPassword())) {
                fail(String.format(ErrorMessage.DATASOURCE_PASSWORD_REQUIRED, datasourceKey));
            }
            if (!databases.add(datasource.getDatabase())) {
                fail(ErrorMessage.DATASOURCE_COMBINATION_DUPLICATE);
            }
            if (!datasourceKeys.add(datasourceKey)) {
                fail(String.format(ErrorMessage.DATASOURCE_KEY_DUPLICATE, datasourceKey));
            }
        }
    }

    private void validateRules(Set<String> datasourceKeys, List<SimpleMysqlRouteProperties.RouteRule> rules) {
        if (rules == null) {
            return;
        }
        for (int index = 0; index < rules.size(); index++) {
            SimpleMysqlRouteProperties.RouteRule rule = rules.get(index);
            if (rule == null || !rule.isEnable()) {
                continue;
            }
            if (!MySqlRouteStringHelper.hasText(rule.getPattern())) {
                fail(String.format(ErrorMessage.RULE_PATTERN_REQUIRED, index));
            }
            RouteMatchType matchType = RouteMatchType.fromCode(rule.getMatchType());
            if (matchType == null) {
                fail(String.format(ErrorMessage.RULE_MATCH_TYPE_INVALID, index));
            }
            if (!MySqlRouteStringHelper.hasText(rule.getDatasourceKey())
                    || !datasourceKeys.contains(rule.getDatasourceKey())) {
                fail(String.format(ErrorMessage.RULE_DATASOURCE_NOT_FOUND, index));
            }
            if (matchType == RouteMatchType.REGEX || matchType == RouteMatchType.WILDCARD) {
                try {
                    patternMatcher.compile(matchType, rule.getPattern());
                } catch (PatternSyntaxException e) {
                    fail(String.format(ErrorMessage.RULE_PATTERN_COMPILE_FAILED, index));
                }
            }
        }
    }

    private String datasourceKey(String clusterKey, String datasourceName) {
        return clusterKey + "." + datasourceName;
    }

    private void fail(String detail) {
        throw new ConfigurationException(ErrorCode.CONFIG_INVALID,
                String.format(ErrorMessage.CONFIG_INVALID, detail));
    }
}
