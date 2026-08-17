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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
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
    private static final Pattern HOSTNAME_LABEL =
            Pattern.compile("^[a-z0-9](?:[-a-z0-9]*[a-z0-9])?$");

    private final RedisRoutePatternMatcher patternMatcher;

    /**
     * 校验 Route 数据源、默认数据源和路由规则配置。
     *
     * @param properties 待校验的 Route 配置
     * @throws ConfigurationException 配置不满足 Route 约束时抛出
     */
    public void validate(SimpleRedisRouteProperties properties) {
        validateSources(properties);
        validateRules(properties);
    }

    private void validateSources(SimpleRedisRouteProperties properties) {
        if (properties.getSources() == null || properties.getSources().isEmpty()) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_001, ErrorMessage.CONFIG_SOURCES_EMPTY);
        }
        if (!RedisRouteStringHelper.hasText(properties.getDefaultSource())) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_002, ErrorMessage.CONFIG_DEFAULT_SOURCE_EMPTY);
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
        validateClusterTopologyAddressFollowNodes(datasourceKey, config, mode);
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
        validateLettucePool(datasourceKey, config.getLettuce().getPool());
    }

    private void validateLettucePool(String datasourceKey, SimpleRedisRouteProperties.PoolConfig pool) {
        if (pool == null || !pool.isEnabled()) {
            return;
        }
        if (pool.getMaxActive() <= 0) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_LETTUCE_POOL_MAX_ACTIVE_INVALID,
                            datasourceKey, pool.getMaxActive()));
        }
        if (pool.getMaxIdle() < 0) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_LETTUCE_POOL_MAX_IDLE_INVALID,
                            datasourceKey, pool.getMaxIdle()));
        }
        if (pool.getMinIdle() < 0) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_LETTUCE_POOL_MIN_IDLE_INVALID,
                            datasourceKey, pool.getMinIdle()));
        }
        if (pool.getMinIdle() > pool.getMaxIdle()) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_LETTUCE_POOL_IDLE_RANGE_INVALID,
                            datasourceKey, pool.getMinIdle(), pool.getMaxIdle()));
        }
        if (pool.getMaxWaitMs() < -1L) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_LETTUCE_POOL_MAX_WAIT_INVALID,
                            datasourceKey, pool.getMaxWaitMs()));
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

    private void validateClusterTopologyAddressFollowNodes(String datasourceKey,
                                                           SimpleRedisRouteProperties.DataSourceConfig config,
                                                           RedisSourceMode mode) {
        if (!config.isClusterTopologyAddressFollowNodes()) {
            return;
        }
        if (mode != RedisSourceMode.CLUSTER) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_CLUSTER_TOPOLOGY_ADDRESS_FOLLOW_NODES_STANDALONE, datasourceKey));
        }
        if (!hasUniqueHostnameAddressMapping(config.getNodes())) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_CLUSTER_TOPOLOGY_ADDRESS_FOLLOW_NODES_MAPPING_INVALID,
                            datasourceKey));
        }
    }

    private boolean hasUniqueHostnameAddressMapping(List<String> nodes) {
        Map<String, String> serviceTails = new HashMap<>();
        boolean mapped = false;
        for (String node : nodes) {
            String normalizedNode = node.trim();
            String host = normalizedNode.substring(0, normalizedNode.lastIndexOf(':'));
            String[] labels = host.split("\\.", -1);
            if (!isSupportedNodesHostname(labels)) {
                continue;
            }
            String service = labels[1];
            String tail = normalizeTail(labels);
            String previous = serviceTails.putIfAbsent(service, tail);
            if (previous != null && !previous.equals(tail)) {
                return false;
            }
            mapped = true;
        }
        return mapped;
    }

    private boolean isSupportedNodesHostname(String[] labels) {
        if (labels.length != 3 && labels.length != 6) {
            return false;
        }
        for (String label : labels) {
            if (!HOSTNAME_LABEL.matcher(label).matches()) {
                return false;
            }
        }
        return labels.length == 3 || ("svc".equals(labels[3])
                && "cluster".equals(labels[4]) && "local".equals(labels[5]));
    }

    private String normalizeTail(String[] labels) {
        StringBuilder builder = new StringBuilder();
        for (int i = 2; i < labels.length; i++) {
            builder.append('.').append(labels[i]);
        }
        return builder.toString();
    }

    private void validateNodes(String datasourceKey, List<String> nodes) {
        for (String node : nodes) {
            if (!RedisRouteStringHelper.hasText(node)) {
                throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                        String.format(ErrorMessage.CONFIG_NODE_INVALID, datasourceKey));
            }
            String[] parts = node.trim().split(":");
            if (parts.length != 2 || !RedisRouteStringHelper.hasText(parts[0])) {
                throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                        String.format(ErrorMessage.CONFIG_NODE_INVALID, datasourceKey));
            }
            try {
                validatePort(datasourceKey, Integer.parseInt(parts[1]));
            } catch (NumberFormatException e) {
                throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                        String.format(ErrorMessage.CONFIG_NODE_INVALID, datasourceKey), e);
            }
        }
    }

    private void validatePort(String datasourceKey, int port) {
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_005,
                    String.format(ErrorMessage.CONFIG_PORT_INVALID, datasourceKey));
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
        if (!RedisRouteStringHelper.hasText(rule.getDatasource())) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_004,
                    String.format(ErrorMessage.CONFIG_ROUTE_DATASOURCE_EMPTY,
                            index, rule.getPattern(), rule.getType()));
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
