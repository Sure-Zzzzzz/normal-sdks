package io.github.surezzzzzz.sdk.elasticsearch.route.configuration;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.RouteMatchType;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ServerVersion;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
@ConfigurationProperties(SimpleElasticsearchRouteConstant.CONFIG_PREFIX)
public class SimpleElasticsearchRouteProperties {

    /**
     * 是否启用路由
     */
    private boolean enable = false;

    /**
     * 默认数据源key
     */
    private String defaultSource = SimpleElasticsearchRouteConstant.DEFAULT_DATASOURCE_KEY;

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
        private Integer timeoutMs = SimpleElasticsearchRouteConstant.DEFAULT_VERSION_DETECT_TIMEOUT_MS;
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
        private Integer connectTimeout = SimpleElasticsearchRouteConstant.DEFAULT_CONNECT_TIMEOUT;

        /**
         * Socket超时时间（毫秒）
         */
        private Integer socketTimeout = SimpleElasticsearchRouteConstant.DEFAULT_SOCKET_TIMEOUT;

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
        private Integer keepAliveStrategy = SimpleElasticsearchRouteConstant.DEFAULT_KEEP_ALIVE_SECONDS;

        /**
         * 最大连接数（连接池配置）
         * 默认：100
         */
        private Integer maxConnTotal = SimpleElasticsearchRouteConstant.DEFAULT_MAX_CONN_TOTAL;

        /**
         * 每个路由的最大连接数
         * 默认：10
         */
        private Integer maxConnPerRoute = SimpleElasticsearchRouteConstant.DEFAULT_MAX_CONN_PER_ROUTE;

        /**
         * 是否启用连接重用
         * 默认：true
         */
        private boolean enableConnectionReuse = SimpleElasticsearchRouteConstant.DEFAULT_ENABLE_CONNECTION_REUSE;

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
                    String protocol = useSsl ? SimpleElasticsearchRouteConstant.PROTOCOL_HTTPS : SimpleElasticsearchRouteConstant.PROTOCOL_HTTP;
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
        private int priority = SimpleElasticsearchRouteConstant.PRIORITY_DEFAULT;

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
                return RouteMatchType.EXACT;
            }
            return matchType;
        }
    }
}
