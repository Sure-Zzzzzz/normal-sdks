package io.github.surezzzzzz.sdk.elasticsearch.route.configuration;

import io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.RouteMatchType;
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
    private String defaultSource = "primary";

    /**
     * 数据源配置
     */
    private Map<String, DataSourceConfig> sources = new HashMap<>();

    /**
     * 路由规则
     */
    private List<RouteRule> rules = new ArrayList<>();

    @PostConstruct
    public void init() {
        log.info("Simple Elasticsearch Route enabled: {}", enable);
        log.info("Default datasource: {}", defaultSource);
        log.info("Configured datasources: {}", sources.keySet());
        log.info("Configured route rules: {}", rules.size());

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
                throw new IllegalArgumentException("配置项 'sources' 不能为空，至少需要配置一个数据源");
            }

            // 2. 校验默认数据源必须存在
            if (!sources.containsKey(defaultSource)) {
                throw new IllegalArgumentException(
                        "默认数据源 [" + defaultSource + "] 不存在，已配置的数据源: " + sources.keySet());
            }

            // 3. 校验每个数据源配置
            sources.forEach((key, config) -> validateDataSourceConfig(key, config));

            // 4. 校验路由规则
            if (!CollectionUtils.isEmpty(rules)) {
                validateRouteRules(rules);
            }

            log.info("Configuration validation passed");

        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Simple Elasticsearch Route 配置验证失败，请检查配置文件", e);
        }
    }

    /**
     * 校验数据源配置
     */
    private void validateDataSourceConfig(String key, DataSourceConfig config) {
        String prefix = "数据源 [" + key + "] ";

        // 1. hosts/urls 至少有一个
        if (isEmpty(config.getHosts()) && isEmpty(config.getUrls())) {
            throw new IllegalArgumentException(prefix + "必须配置 hosts 或 urls");
        }

        // 2. 超时时间验证
        if (config.getConnectTimeout() == null || config.getConnectTimeout() <= 0) {
            throw new IllegalArgumentException(prefix + "connectTimeout 必须 > 0");
        }
        if (config.getSocketTimeout() == null || config.getSocketTimeout() <= 0) {
            throw new IllegalArgumentException(prefix + "socketTimeout 必须 > 0");
        }

        // 3. 连接池配置
        if (config.getMaxConnTotal() == null || config.getMaxConnTotal() <= 0) {
            throw new IllegalArgumentException(prefix + "maxConnTotal 必须 > 0");
        }
        if (config.getMaxConnPerRoute() == null || config.getMaxConnPerRoute() <= 0) {
            throw new IllegalArgumentException(prefix + "maxConnPerRoute 必须 > 0");
        }
        if (config.getMaxConnPerRoute() > config.getMaxConnTotal()) {
            throw new IllegalArgumentException(
                    prefix + "maxConnPerRoute (" + config.getMaxConnPerRoute()
                            + ") 不能大于 maxConnTotal (" + config.getMaxConnTotal() + ")");
        }

        // 4. 代理配置
        if (config.getProxyPort() != null && isEmpty(config.getProxyHost())) {
            throw new IllegalArgumentException(prefix + "设置了 proxyPort 必须同时设置 proxyHost");
        }

        // 5. keepAliveStrategy
        if (config.getKeepAliveStrategy() == null || config.getKeepAliveStrategy() <= 0) {
            throw new IllegalArgumentException(prefix + "keepAliveStrategy 必须 > 0");
        }

        // 6. 验证 URL 格式（调用 getResolvedUrls 触发验证）
        try {
            config.getResolvedUrls();
        } catch (Exception e) {
            throw new IllegalArgumentException(prefix + "URL 格式验证失败: " + e.getMessage(), e);
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
                throw new IllegalArgumentException(rulePrefix + "pattern 不能为空");
            }

            // 2. 数据源必须存在
            if (!sources.containsKey(rule.getDatasource())) {
                throw new IllegalArgumentException(
                        rulePrefix + "[" + rule.getPattern() + "] 引用的数据源 ["
                                + rule.getDatasource() + "] 不存在，已配置的数据源: " + sources.keySet());
            }

            // 3. 匹配类型有效性
            if (!RouteMatchType.isValid(rule.getType())) {
                throw new IllegalArgumentException(
                        rulePrefix + "[" + rule.getPattern() + "] 匹配类型 [" + rule.getType()
                                + "] 无效，有效值: exact, prefix, suffix, wildcard, regex");
            }

            // 4. priority 范围
            if (rule.getPriority() < 1 || rule.getPriority() > 10000) {
                throw new IllegalArgumentException(
                        rulePrefix + "[" + rule.getPattern() + "] priority 必须在 [1, 10000] 范围内，当前值: "
                                + rule.getPriority());
            }

            // 5. regex 类型预编译验证
            if (rule.getMatchType() == RouteMatchType.REGEX) {
                try {
                    java.util.regex.Pattern.compile(rule.getPattern());
                } catch (java.util.regex.PatternSyntaxException e) {
                    throw new IllegalArgumentException(
                            rulePrefix + "[" + rule.getPattern() + "] 正则表达式语法错误: " + e.getMessage(), e);
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
                throw new IllegalArgumentException(
                        "存在 " + count + " 个 exact 类型的重复规则，pattern: " + pattern);
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
        private Integer connectTimeout = 5000;

        /**
         * Socket超时时间（毫秒）
         */
        private Integer socketTimeout = 60000;

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
        private Integer keepAliveStrategy = 300;

        /**
         * 最大连接数（连接池配置）
         * 默认：100
         */
        private Integer maxConnTotal = 100;

        /**
         * 每个路由的最大连接数
         * 默认：10
         */
        private Integer maxConnPerRoute = 10;

        /**
         * 是否启用连接重用
         * 默认：true
         */
        private boolean enableConnectionReuse = true;

        /**
         * 获取解析后的 URL 列表（标准化为 http://xxx 或 https://xxx）
         */
        public List<String> getResolvedUrls() {
            String source = urls != null ? urls : hosts;

            if (source == null || source.trim().isEmpty()) {
                throw new IllegalStateException("hosts 和 urls 都为空");
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
                    String protocol = useSsl ? "https" : "http";
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
                throw new IllegalArgumentException("无效的 URL 格式: " + url, e);
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
        private int priority = 100;

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
