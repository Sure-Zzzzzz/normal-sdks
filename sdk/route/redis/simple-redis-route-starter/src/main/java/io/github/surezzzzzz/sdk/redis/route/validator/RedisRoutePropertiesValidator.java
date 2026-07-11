package io.github.surezzzzzz.sdk.redis.route.validator;

import io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteProperties;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.redis.route.constant.RedisSourceMode;
import io.github.surezzzzzz.sdk.redis.route.constant.RouteMatchType;
import io.github.surezzzzzz.sdk.redis.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.redis.route.matcher.RedisRoutePatternMatcher;
import io.github.surezzzzzz.sdk.redis.route.support.RedisRouteStringHelper;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

/**
 * Redis route 配置校验器
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class RedisRoutePropertiesValidator {

    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

    private final RedisRoutePatternMatcher patternMatcher;

    public void validate(SimpleRedisRouteProperties properties) {
        validateSources(properties);
        validateRules(properties);
    }

    private void validateSources(SimpleRedisRouteProperties properties) {
        if (properties.getSources() == null || properties.getSources().isEmpty()) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_001, ErrorMessage.CONFIG_SOURCES_EMPTY);
        }
        if (!properties.getSources().containsKey(properties.getDefaultSource())) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_002,
                    String.format(ErrorMessage.CONFIG_DEFAULT_SOURCE_NOT_FOUND,
                            properties.getDefaultSource(), properties.getSources().keySet()));
        }
        for (Map.Entry<String, SimpleRedisRouteProperties.DataSourceConfig> entry : properties.getSources().entrySet()) {
            validateSource(entry.getKey(), entry.getValue());
        }
    }

    private void validateSource(String datasourceKey, SimpleRedisRouteProperties.DataSourceConfig config) {
        if (!RedisRouteStringHelper.hasText(datasourceKey)) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005, ErrorMessage.CONFIG_DATASOURCE_KEY_EMPTY);
        }
        RedisSourceMode mode = RedisSourceMode.fromCode(config.getMode());
        if (mode == null) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_SOURCE_MODE_INVALID,
                            datasourceKey, config.getMode(), join(RedisSourceMode.getAllCodes())));
        }
        validateCommonSource(datasourceKey, config);
        if (mode == RedisSourceMode.STANDALONE) {
            validateStandalone(datasourceKey, config);
        } else if (mode == RedisSourceMode.CLUSTER) {
            validateCluster(datasourceKey, config);
        }
    }

    private void validateCommonSource(String datasourceKey, SimpleRedisRouteProperties.DataSourceConfig config) {
        if (config.getTimeoutMs() <= 0 || config.getConnectTimeoutMs() <= 0) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_TIMEOUT_INVALID, datasourceKey));
        }
        if (config.getLettuce() == null || config.getLettuce().getShutdownTimeoutMs() <= 0) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_TIMEOUT_INVALID, datasourceKey));
        }
        if (config.getLettuce().getRequestQueueSize() <= 0) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_LETTUCE_REQUEST_QUEUE_SIZE_INVALID,
                            datasourceKey, config.getLettuce().getRequestQueueSize()));
        }
        if (config.getLettuce().getClusterRefreshPeriodMs() <= 0) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_CLUSTER_REFRESH_PERIOD_INVALID,
                            datasourceKey, config.getLettuce().getClusterRefreshPeriodMs()));
        }
    }

    private void validateStandalone(String datasourceKey, SimpleRedisRouteProperties.DataSourceConfig config) {
        if (!RedisRouteStringHelper.hasText(config.getHost())) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_HOST_EMPTY, datasourceKey));
        }
        validatePort(datasourceKey, config.getPort());
        if (config.getDatabase() < 0) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_DATABASE_INVALID, datasourceKey, config.getDatabase()));
        }
    }

    private void validateCluster(String datasourceKey, SimpleRedisRouteProperties.DataSourceConfig config) {
        if (config.getNodes() == null || config.getNodes().isEmpty()) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_CLUSTER_NODES_EMPTY, datasourceKey));
        }
        validateNodes(datasourceKey, config.getNodes());
        if (config.getDatabase() != 0) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_CLUSTER_DATABASE_INVALID, datasourceKey, config.getDatabase()));
        }
        if (config.getMaxRedirects() != null && config.getMaxRedirects() < 0) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_CLUSTER_MAX_REDIRECTS_INVALID, datasourceKey, config.getMaxRedirects()));
        }
    }

    private void validateNodes(String datasourceKey, List<String> nodes) {
        for (String node : nodes) {
            if (!RedisRouteStringHelper.hasText(node)) {
                throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                        String.format(ErrorMessage.CONFIG_NODE_INVALID, datasourceKey, node));
            }
            String[] parts = node.trim().split(":");
            if (parts.length != 2 || !RedisRouteStringHelper.hasText(parts[0])) {
                throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                        String.format(ErrorMessage.CONFIG_NODE_INVALID, datasourceKey, node));
            }
            try {
                validatePort(datasourceKey, Integer.parseInt(parts[1]));
            } catch (NumberFormatException e) {
                throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                        String.format(ErrorMessage.CONFIG_NODE_INVALID, datasourceKey, node), e);
            }
        }
    }

    private void validatePort(String datasourceKey, int port) {
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_PORT_INVALID, datasourceKey, port));
        }
    }

    private void validateRules(SimpleRedisRouteProperties properties) {
        List<SimpleRedisRouteProperties.RouteRule> rules = properties.getRules();
        if (rules == null) {
            return;
        }
        for (int i = 0; i < rules.size(); i++) {
            validateRule(i, rules.get(i), properties.getSources());
        }
    }

    private void validateRule(int index, SimpleRedisRouteProperties.RouteRule rule,
                              Map<String, SimpleRedisRouteProperties.DataSourceConfig> sources) {
        if (rule == null || !rule.isEnable()) {
            return;
        }
        if (!RedisRouteStringHelper.hasText(rule.getPattern())) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_004,
                    String.format(ErrorMessage.CONFIG_ROUTE_PATTERN_EMPTY, index));
        }
        RouteMatchType type = RouteMatchType.fromCode(rule.getType());
        if (type == null) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_007,
                    String.format(ErrorMessage.CONFIG_ROUTE_TYPE_INVALID,
                            index, rule.getType(), rule.getPattern(), rule.getDatasource(), join(RouteMatchType.getAllCodes())));
        }
        if (!sources.containsKey(rule.getDatasource())) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_004,
                    String.format(ErrorMessage.CONFIG_ROUTE_DATASOURCE_NOT_FOUND,
                            index, rule.getDatasource(), rule.getPattern(), rule.getType(), sources.keySet()));
        }
        if (type == RouteMatchType.REGEX || type == RouteMatchType.WILDCARD) {
            try {
                patternMatcher.compile(type, rule.getPattern());
            } catch (PatternSyntaxException e) {
                throw new ConfigurationException(ErrorCode.REDIS_ROUTE_004,
                        String.format(ErrorMessage.CONFIG_ROUTE_REGEX_INVALID,
                                index, rule.getPattern(), rule.getType(), rule.getDatasource()), e);
            }
        }
    }

    private String join(String[] values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values[i]);
        }
        return builder.toString();
    }
}
