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
        validateClusters(properties.getClusters());
        validateDatasources(properties.getClusters(), properties.getDatasources());
        validateRules(properties.getDatasources(), properties.getRules());
    }

    private void validateClusters(Map<String, SimpleMysqlRouteProperties.ClusterConfig> clusters) {
        if (clusters == null || clusters.isEmpty()) {
            fail(ErrorMessage.CLUSTERS_REQUIRED);
        }
        Set<String> keys = new HashSet<>();
        for (Map.Entry<String, SimpleMysqlRouteProperties.ClusterConfig> entry : clusters.entrySet()) {
            String clusterKey = entry.getKey();
            SimpleMysqlRouteProperties.ClusterConfig config = entry.getValue();
            if (!MySqlRouteStringHelper.hasText(clusterKey) || !keys.add(clusterKey)) {
                fail(ErrorMessage.CLUSTER_KEY_INVALID);
            }
            if (config == null || !MySqlRouteStringHelper.hasText(config.getHost())) {
                fail(String.format(ErrorMessage.CLUSTER_HOST_REQUIRED, clusterKey));
            }
            if (config.getPort() < SimpleMysqlRouteConstant.MIN_CLUSTER_PORT
                    || config.getPort() > SimpleMysqlRouteConstant.MAX_CLUSTER_PORT) {
                fail(String.format(ErrorMessage.CLUSTER_PORT_INVALID, clusterKey));
            }
            if (!MySqlRouteStringHelper.hasText(config.getCredentialRef())) {
                fail(String.format(ErrorMessage.CLUSTER_CREDENTIAL_REF_REQUIRED, clusterKey));
            }
            if (!MySqlRouteStringHelper.hasText(config.getDriverClassName())) {
                fail(String.format(ErrorMessage.CLUSTER_DRIVER_REQUIRED, clusterKey));
            }
        }
    }

    private void validateDatasources(Map<String, SimpleMysqlRouteProperties.ClusterConfig> clusters,
                                     Map<String, SimpleMysqlRouteProperties.DatasourceConfig> datasources) {
        if (datasources == null || datasources.isEmpty()) {
            fail(ErrorMessage.DATASOURCES_REQUIRED);
        }
        Set<String> combinations = new HashSet<>();
        for (Map.Entry<String, SimpleMysqlRouteProperties.DatasourceConfig> entry : datasources.entrySet()) {
            String datasourceKey = entry.getKey();
            SimpleMysqlRouteProperties.DatasourceConfig config = entry.getValue();
            if (!MySqlRouteStringHelper.hasText(datasourceKey)) {
                fail(ErrorMessage.DATASOURCE_KEY_REQUIRED);
            }
            if (config == null || !MySqlRouteStringHelper.hasText(config.getClusterKey())
                    || !clusters.containsKey(config.getClusterKey())) {
                fail(String.format(ErrorMessage.DATASOURCE_CLUSTER_NOT_FOUND, datasourceKey));
            }
            if (!MySqlRouteStringHelper.hasText(config.getDatabase())) {
                fail(String.format(ErrorMessage.DATASOURCE_DATABASE_REQUIRED, datasourceKey));
            }
            String combination = datasourceCombination(config.getClusterKey(), config.getDatabase());
            if (!combinations.add(combination)) {
                fail(ErrorMessage.DATASOURCE_COMBINATION_DUPLICATE);
            }
        }
    }

    private String datasourceCombination(String clusterKey, String database) {
        return clusterKey.length() + ":" + clusterKey + ":" + database.length() + ":" + database;
    }

    private void validateRules(Map<String, SimpleMysqlRouteProperties.DatasourceConfig> datasources,
                               List<SimpleMysqlRouteProperties.RouteRule> rules) {
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
                    || !datasources.containsKey(rule.getDatasourceKey())) {
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

    private void fail(String detail) {
        throw new ConfigurationException(ErrorCode.CONFIG_INVALID,
                String.format(ErrorMessage.CONFIG_INVALID, detail));
    }
}
