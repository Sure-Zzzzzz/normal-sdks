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

        // 校验数据源不能为空
        if (CollectionUtils.isEmpty(sources)) {
            throw new IllegalStateException("No elasticsearch datasource configured!");
        }

        // 校验默认数据源必须存在
        if (!sources.containsKey(defaultSource)) {
            throw new IllegalStateException(
                    "Default datasource [" + defaultSource + "] not found in configured sources: " + sources.keySet());
        }

        // 校验规则引用的数据源必须存在 + 校验匹配类型有效性
        if (!CollectionUtils.isEmpty(rules)) {
            for (RouteRule rule : rules) {
                // 校验数据源
                if (!sources.containsKey(rule.getDatasource())) {
                    throw new IllegalStateException(
                            "Route rule [" + rule.getPattern() + "] references non-existent datasource: " + rule.getDatasource());
                }

                // 校验匹配类型
                if (!RouteMatchType.isValid(rule.getType())) {
                    throw new IllegalStateException(
                            "Route rule [" + rule.getPattern() + "] has invalid match type: " + rule.getType()
                                    + ", valid types are: exact, prefix, suffix, wildcard, regex");
                }
            }
        }

        log.info("Configuration validation passed");
    }

    /**
     * 数据源配置
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class DataSourceConfig {
        /**
         * ES集群地址，多个用逗号分隔
         * 例如: localhost:9200,localhost:9201
         */
        private String hosts;

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
         * 是否使用 SSL/TLS
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
