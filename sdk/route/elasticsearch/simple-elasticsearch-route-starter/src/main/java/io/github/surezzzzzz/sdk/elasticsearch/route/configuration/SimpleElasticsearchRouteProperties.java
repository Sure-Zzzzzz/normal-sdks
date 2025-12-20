package io.github.surezzzzzz.sdk.elasticsearch.route.configuration;

import io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ConfigConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.RouteMatchType;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.ServerVersion;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple Elasticsearch Route Properties
 *
 * @author surezzzzzz
 */
@Getter
@Setter
@NoArgsConstructor
@Slf4j
@SimpleElasticsearchRouteComponent
@ConfigurationProperties("io.github.surezzzzzz.sdk.elasticsearch.route")
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.elasticsearch.route", name = "enable", havingValue = "true")
public class SimpleElasticsearchRouteProperties {

    /**
     * 是否启用路由
     */
    private boolean enable = false;

    /**
     * 默认数据源key
     */
    private String defaultSource = ConfigConstant.DEFAULT_DATASOURCE_KEY;

    /**
     * 数据源配置
     */
    private Map<String, DataSourceConfig> sources = new HashMap<>();

    /**
     * 路由规则
     */
    private List<RouteRule> rules = new ArrayList<>();

    /**
     * ES 服务端版本探测配置
     */
    private VersionDetectConfig versionDetect = new VersionDetectConfig();

    @PostConstruct
    public void init() {
        log.info("Simple Elasticsearch Route enabled: {}", enable);
        log.info("Default datasource: {}", defaultSource);
        log.info("Configured datasources: {}", sources.keySet());
        log.info("Configured route rules: {}", rules.size());
        log.info("Version detect enabled: {}, fail-fast-on-detect-error: {}",
                versionDetect.isEnabled(), versionDetect.isFailFastOnDetectError());

        // 校验配置
        validate();
    }

    /**
     * 校验配置
     */
    private void validate() {
        if (!enable) {
            return;
        }

        try {
            // 1. 校验数据源不能为空
            if (CollectionUtils.isEmpty(sources)) {
                throw new ConfigurationException(ErrorCode.CONFIG_SOURCES_EMPTY,
                        ErrorMessage.CONFIG_SOURCES_EMPTY);
            }

            // 2. 校验默认数据源必须存在
            if (!sources.containsKey(defaultSource)) {
                throw new ConfigurationException(ErrorCode.CONFIG_DEFAULT_SOURCE_NOT_FOUND,
                        String.format(ErrorMessage.CONFIG_DEFAULT_SOURCE_NOT_FOUND,
                                defaultSource, sources.keySet()));
            }

            // 3. 校验每个数据源配置
            sources.forEach((key, config) -> validateDataSourceConfig(key, config));

            validateVersionDetectConfig(versionDetect);

            // 4. 校验路由规则
            if (!CollectionUtils.isEmpty(rules)) {
                validateRouteRules(rules);
            }

            log.info("Configuration validation passed");

        } catch (ConfigurationException e) {
            throw new ConfigurationException(ErrorCode.CONFIG_VALIDATION_FAILED,
                    ErrorMessage.CONFIG_VALIDATION_FAILED, e);
        }
    }

    /**
     * 校验数据源配置
     */
    private void validateDataSourceConfig(String key, DataSourceConfig config) {
        String prefix = "数据源 [" + key + "] ";

        // 1. hosts/urls 至少有一个
        if (isEmpty(config.getHosts()) && isEmpty(config.getUrls())) {
            throw new ConfigurationException(ErrorCode.CONFIG_HOSTS_AND_URLS_EMPTY,
                    prefix + ErrorMessage.CONFIG_HOSTS_AND_URLS_EMPTY);
        }

        // 2. 超时时间验证
        if (config.getConnectTimeout() == null || config.getConnectTimeout() <= 0) {
            throw new ConfigurationException(ErrorCode.CONFIG_CONNECT_TIMEOUT_INVALID,
                    prefix + ErrorMessage.CONFIG_CONNECT_TIMEOUT_INVALID);
        }
        if (config.getSocketTimeout() == null || config.getSocketTimeout() <= 0) {
            throw new ConfigurationException(ErrorCode.CONFIG_SOCKET_TIMEOUT_INVALID,
                    prefix + ErrorMessage.CONFIG_SOCKET_TIMEOUT_INVALID);
        }

        // 2.1 ES 服务端版本（server-version，可选）
        if (!isEmpty(config.getServerVersion())) {
            try {
                ServerVersion.parse(config.getServerVersion());
            } catch (Exception e) {
                throw new ConfigurationException(ErrorCode.CONFIG_SERVER_VERSION_INVALID,
                        prefix + String.format(ErrorMessage.CONFIG_SERVER_VERSION_INVALID,
                                config.getServerVersion()), e);
            }
        }

        // 3. 连接池配置
        if (config.getMaxConnTotal() == null || config.getMaxConnTotal() <= 0) {
            throw new ConfigurationException(ErrorCode.CONFIG_MAX_CONN_TOTAL_INVALID,
                    prefix + ErrorMessage.CONFIG_MAX_CONN_TOTAL_INVALID);
        }
        if (config.getMaxConnPerRoute() == null || config.getMaxConnPerRoute() <= 0) {
            throw new ConfigurationException(ErrorCode.CONFIG_MAX_CONN_PER_ROUTE_INVALID,
                    prefix + ErrorMessage.CONFIG_MAX_CONN_PER_ROUTE_INVALID);
        }
        if (config.getMaxConnPerRoute() > config.getMaxConnTotal()) {
            throw new ConfigurationException(ErrorCode.CONFIG_MAX_CONN_MISMATCH,
                    prefix + String.format(ErrorMessage.CONFIG_MAX_CONN_MISMATCH,
                            config.getMaxConnPerRoute(), config.getMaxConnTotal()));
        }

        // 4. 代理配置
        if (config.getProxyPort() != null && isEmpty(config.getProxyHost())) {
            throw new ConfigurationException(ErrorCode.CONFIG_PROXY_HOST_MISSING,
                    prefix + ErrorMessage.CONFIG_PROXY_HOST_MISSING);
        }

        // 5. keepAliveStrategy
        if (config.getKeepAliveStrategy() == null || config.getKeepAliveStrategy() <= 0) {
            throw new ConfigurationException(ErrorCode.CONFIG_KEEP_ALIVE_INVALID,
                    prefix + ErrorMessage.CONFIG_KEEP_ALIVE_INVALID);
        }

        // 6. 验证 URL 格式（调用 getResolvedUrls 触发验证）
        try {
            config.getResolvedUrls();
        } catch (Exception e) {
            throw new ConfigurationException(ErrorCode.CONFIG_URL_FORMAT_INVALID,
                    prefix + String.format(ErrorMessage.CONFIG_URL_FORMAT_INVALID, e.getMessage()), e);
        }
    }

    /**
     * 校验路由规则
     */
    private void validateRouteRules(List<RouteRule> rules) {
        Map<String, Integer> exactPatterns = new HashMap<>();
        Map<Integer, Integer> priorityCount = new HashMap<>();

        for (int i = 0; i < rules.size(); i++) {
            RouteRule rule = rules.get(i);
            String rulePrefix = "路由规则 #" + (i + 1) + " ";

            // 1. pattern 不能为空
            if (isEmpty(rule.getPattern())) {
                throw new ConfigurationException(ErrorCode.CONFIG_ROUTE_PATTERN_EMPTY,
                        rulePrefix + ErrorMessage.CONFIG_ROUTE_PATTERN_EMPTY);
            }

            // 2. 数据源必须存在
            if (!sources.containsKey(rule.getDatasource())) {
                throw new ConfigurationException(ErrorCode.CONFIG_ROUTE_DATASOURCE_NOT_FOUND,
                        rulePrefix + String.format(ErrorMessage.CONFIG_ROUTE_DATASOURCE_NOT_FOUND,
                                rule.getPattern(), rule.getDatasource(), sources.keySet()));
            }

            // 3. 匹配类型有效性
            if (!RouteMatchType.isValid(rule.getType())) {
                throw new ConfigurationException(ErrorCode.CONFIG_ROUTE_TYPE_INVALID,
                        rulePrefix + String.format(ErrorMessage.CONFIG_ROUTE_TYPE_INVALID,
                                rule.getPattern(), rule.getType()));
            }

            // 4. priority 范围
            if (rule.getPriority() < ConfigConstant.PRIORITY_MIN
                    || rule.getPriority() > ConfigConstant.PRIORITY_MAX) {
                throw new ConfigurationException(ErrorCode.CONFIG_ROUTE_PRIORITY_INVALID,
                        rulePrefix + String.format(ErrorMessage.CONFIG_ROUTE_PRIORITY_INVALID,
                                rule.getPattern(), rule.getPriority()));
            }

            // 5. regex 类型预编译验证
            if (rule.getMatchType() == RouteMatchType.REGEX) {
                try {
                    java.util.regex.Pattern.compile(rule.getPattern());
                } catch (java.util.regex.PatternSyntaxException e) {
                    throw new ConfigurationException(ErrorCode.CONFIG_ROUTE_REGEX_INVALID,
                            rulePrefix + String.format(ErrorMessage.CONFIG_ROUTE_REGEX_INVALID,
                                    rule.getPattern(), e.getMessage()), e);
                }
            }

            // 收集统计信息
            if (rule.getMatchType() == RouteMatchType.EXACT) {
                exactPatterns.merge(rule.getPattern(), 1, Integer::sum);
            }
            priorityCount.merge(rule.getPriority(), 1, Integer::sum);
        }

        // 6. 检查重复的 exact 规则
        exactPatterns.forEach((pattern, count) -> {
            if (count > 1) {
                throw new ConfigurationException(ErrorCode.CONFIG_ROUTE_EXACT_DUPLICATE,
                        String.format(ErrorMessage.CONFIG_ROUTE_EXACT_DUPLICATE, count, pattern));
            }
        });

        // 7. 警告：相同优先级的规则
        priorityCount.forEach((priority, count) -> {
            if (count > 1) {
                log.warn("存在 {} 个优先级为 {} 的规则，匹配顺序可能不确定", count, priority);
            }
        });
    }

    /**
     * 判断字符串是否为空
     */
    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    private void validateVersionDetectConfig(VersionDetectConfig config) {
        if (config == null) {
            return;
        }
        if (config.getTimeoutMs() != null && config.getTimeoutMs() <= 0) {
            throw new ConfigurationException(ErrorCode.CONFIG_VERSION_DETECT_TIMEOUT_INVALID,
                    ErrorMessage.CONFIG_VERSION_DETECT_TIMEOUT_INVALID);
        }
    }

    /**
     * ES 服务端版本探测配置
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class VersionDetectConfig {
        /**
         * 是否启用版本探测（GET /）
         */
        private boolean enabled = true;

        /**
         * 当未配置 datasource.server-version 且探测失败时，是否快速失败（默认：false）
         */
        private boolean failFastOnDetectError = false;

        /**
         * 探测超时时间（毫秒），默认 1500
         */
        private Integer timeoutMs = ConfigConstant.DEFAULT_VERSION_DETECT_TIMEOUT_MS;
    }

    /**
     * 数据源配置
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class DataSourceConfig {
        /**
         * ES集群地址（旧方式，向后兼容）
         * 格式1: localhost:9200,localhost:9201（配合 useSsl 使用）
         * 格式2: http://localhost:9200,http://localhost:9201（自动识别协议）
         */
        private String hosts;

        /**
         * ES集群地址（新方式，推荐）
         * 必须包含协议: http://localhost:9200,https://localhost:9201
         * 优先级高于 hosts
         */
        private String urls;

        /**
         * ES 服务端版本（可选，如：6.2.2），配置后作为有效版本使用
         */
        private String serverVersion;

        /**
         * 用户名（可选）
         */
        private String username;

        /**
         * 密码（可选）
         */
        private String password;

        /**
         * 连接超时时间（毫秒）
         */
        private Integer connectTimeout = ConfigConstant.DEFAULT_CONNECT_TIMEOUT;

        /**
         * Socket超时时间（毫秒）
         */
        private Integer socketTimeout = ConfigConstant.DEFAULT_SOCKET_TIMEOUT;

        /**
         * 是否使用 SSL/TLS（仅在使用 hosts 且未指定协议时生效）
         */
        private boolean useSsl = false;

        /**
         * 是否跳过 SSL 证书验证（仅开发环境使用！生产环境必须为 false）
         */
        private boolean skipSslValidation = false;

        /**
         * 代理主机（可选）
         */
        private String proxyHost;

        /**
         * 代理端口（可选）
         */
        private Integer proxyPort;

        /**
         * 路径前缀（可选）
         * 例如：ES 通过 Nginx 代理时可能需要 /elasticsearch
         */
        private String pathPrefix;

        /**
         * Keep-Alive 保持时间（秒）
         * 默认：300 秒（5分钟）
         */
        private Integer keepAliveStrategy = ConfigConstant.DEFAULT_KEEP_ALIVE_SECONDS;

        /**
         * 最大连接数（连接池配置）
         * 默认：100
         */
        private Integer maxConnTotal = ConfigConstant.DEFAULT_MAX_CONN_TOTAL;

        /**
         * 每个路由的最大连接数
         * 默认：10
         */
        private Integer maxConnPerRoute = ConfigConstant.DEFAULT_MAX_CONN_PER_ROUTE;

        /**
         * 是否启用连接重用
         * 默认：true
         */
        private boolean enableConnectionReuse = ConfigConstant.DEFAULT_ENABLE_CONNECTION_REUSE;

        /**
         * 获取解析后的 URL 列表（标准化为 http://xxx 或 https://xxx）
         */
        public List<String> getResolvedUrls() {
            String source = urls != null ? urls : hosts;

            if (source == null || source.trim().isEmpty()) {
                throw new ConfigurationException(ErrorCode.CONFIG_HOSTS_AND_URLS_EMPTY,
                        ErrorMessage.OTHER_URL_EMPTY);
            }

            List<String> result = new ArrayList<>();
            String[] parts = source.split(",");

            for (String part : parts) {
                String uri = part.trim();
                if (uri.isEmpty()) {
                    continue;
                }

                // 已经包含协议
                if (uri.contains("://")) {
                    validateUrl(uri);
                    result.add(uri);
                } else {
                    // 旧格式 host:port，根据 useSsl 拼接
                    String protocol = useSsl ? ConfigConstant.PROTOCOL_HTTPS : ConfigConstant.PROTOCOL_HTTP;
                    String fullUrl = protocol + "://" + uri;
                    validateUrl(fullUrl);
                    result.add(fullUrl);
                }
            }

            return result;
        }

        /**
         * 验证 URL 格式
         */
        private void validateUrl(String url) {
            try {
                new java.net.URL(url);
            } catch (java.net.MalformedURLException e) {
                throw new ConfigurationException(ErrorCode.CONFIG_URL_FORMAT_INVALID,
                        String.format(ErrorMessage.OTHER_URL_INVALID, url), e);
            }
        }
    }

    /**
     * 路由规则
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class RouteRule {
        /**
         * 匹配模式
         */
        private String pattern;

        /**
         * 目标数据源
         */
        private String datasource;

        /**
         * 匹配类型（字符串配置，内部会转换为枚举）
         * 支持: exact, prefix, suffix, wildcard, regex
         */
        private String type = RouteMatchType.EXACT.getCode();

        /**
         * 优先级（数字越小优先级越高）
         */
        private int priority = ConfigConstant.PRIORITY_DEFAULT;

        /**
         * 是否启用
         */
        private boolean enable = true;

        /**
         * 获取匹配类型枚举
         *
         * @return 匹配类型枚举
         */
        public RouteMatchType getMatchType() {
            RouteMatchType matchType = RouteMatchType.fromCode(type);
            if (matchType == null) {
                log.warn("Unknown route match type [{}], using EXACT as default", type);
                return RouteMatchType.EXACT;
            }
            return matchType;
        }
    }
}
