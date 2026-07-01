package io.github.surezzzzzz.sdk.elasticsearch.route.configuration;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.*;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.ConfigurationException;
import lombok.Data;
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
@Data
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
     * 代理类型（默认 AUTO）
     */
    private String proxyType = ProxyType.AUTO.getCode();

    /**
     * 写操作索引配置（推荐配置块）
     */
    private GlobalWriteIndexConfig writeIndex = new GlobalWriteIndexConfig();

    /**
     * 写操作日期索引的全局默认时区（兼容旧平铺配置，不配则使用 JVM 默认时区）
     *
     * <p>格式为合法的 {@link java.time.ZoneId} 字符串，如 {@code Asia/Shanghai}、{@code UTC}。
     * 推荐使用 {@link #writeIndex} 的 {@code zoneId} 配置。
     *
     * <p>非法值启动时打 WARN 日志，运行时降级为 JVM 默认时区，不阻断启动。
     */
    private String writeIndexZoneId;

    /**
     * 获取生效的全局写操作日期索引时区
     *
     * @return 生效的全局时区配置
     */
    public String getEffectiveWriteIndexZoneId() {
        if (writeIndex != null && hasText(writeIndex.getZoneId())) {
            return writeIndex.getZoneId();
        }
        return writeIndexZoneId;
    }

    /**
     * 获取生效的全局写操作日期索引时区配置项名称
     *
     * @return 生效的全局时区配置项名称
     */
    public String getEffectiveWriteIndexZoneIdConfigName() {
        if (writeIndex != null && hasText(writeIndex.getZoneId())) {
            return SimpleElasticsearchRouteConstant.CONFIG_WRITE_INDEX_ZONE_ID;
        }
        return SimpleElasticsearchRouteConstant.CONFIG_WRITE_INDEX_ZONE_ID_LEGACY;
    }

    /**
     * 判断字符串是否有有效文本
     *
     * @param value 待判断字符串
     * @return 是否有有效文本
     */
    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 全局写操作索引配置
     */
    @Data
    public static class GlobalWriteIndexConfig {
        /**
         * 写操作日期索引的全局默认时区
         */
        private String zoneId;
    }

    /**
     * ES 服务端版本探测配置
     */
    @Data
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
    @Data
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
         * 异步写线程池大小（所有配了该 datasource 的 rule 共用）
         * 默认：8
         */
        private int asyncWriteThreadPoolSize = SimpleElasticsearchRouteConstant.DEFAULT_ASYNC_WRITE_THREAD_POOL_SIZE;

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
    @Data
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
         * 写操作索引配置（推荐配置块）
         */
        private WriteIndexConfig writeIndex = new WriteIndexConfig();

        /**
         * 读操作索引配置（推荐配置块）
         */
        private ReadIndexConfig readIndex = new ReadIndexConfig();

        /**
         * 写操作索引名模板（兼容旧平铺配置，不配则默认行为）
         *
         * <p>支持日期占位符，格式为 {@code {pattern}}，其中 pattern 为合法的 {@link java.time.format.DateTimeFormatter} 格式串。
         * 每次写操作时将占位符替换为当日日期，并将结果作为实际写入索引名。
         * 推荐使用 {@link #writeIndex} 的 {@code template} 配置。
         *
         * <p>示例：
         * <ul>
         *   <li>{@code "app-log-{yyyy.MM.dd}"} → {@code "app-log-2026.07.01"}（Logstash 风格）</li>
         *   <li>{@code "metric-{yyyy-MM-dd}"} → {@code "metric-2026-07-01"}（ISO 日期）</li>
         *   <li>{@code "{yyyyMMdd}-access"} → {@code "20260701-access"}（紧凑格式）</li>
         * </ul>
         *
         * <p>非法 pattern 会在启动时打 WARN 日志，运行时直接使用原始模板字符串（不抛异常）。
         */
        private String writeIndexTemplate;

        /**
         * 读操作索引名（兼容旧平铺配置，不配则默认行为）
         *
         * <p>配置后，search / get 等读操作将使用此值替换原始索引名，支持通配符。
         * 配合写操作索引模板使用时，写入当日分片，读取覆盖所有历史分片。
         * 推荐使用 {@link #readIndex} 的 {@code pattern} 配置。
         *
         * <p>示例：
         * <ul>
         *   <li>{@code "app-log-*"}：覆盖所有日期分片</li>
         *   <li>{@code "app-log-2026.*"}：只覆盖 2026 年的分片</li>
         * </ul>
         */
        private String readIndexPattern;

        /**
         * 写操作是否强制异步（默认 false）
         *
         * <p>设为 {@code true} 时，写操作（save/index/bulkIndex 等）提交到独立线程池后立即返回 {@code null}，
         * 不等待 ES 响应。执行异常在线程池内部记录错误日志，不向调用方抛出。
         *
         * <p>适用场景：日志、埋点、审计等允许少量丢失的写场景，对吞吐量要求高。
         * <p>不适用：需要确认写入结果、强一致性要求、事务语义的场景。
         *
         * <p>线程池由 {@link DataSourceConfig#asyncWriteThreadPoolSize} 配置，按 datasource 隔离。
         */
        private boolean asyncWrite = false;

        /**
         * 写操作日期索引的时区（兼容旧平铺配置，不配则继承全局配置）
         *
         * <p>格式为合法的 {@link java.time.ZoneId} 字符串，如 {@code Asia/Shanghai}、{@code UTC}。
         * 推荐使用 {@link #writeIndex} 的 {@code zoneId} 配置。
         *
         * <p>非法值启动时打 WARN 日志，运行时降级为全局配置或 JVM 默认时区，不阻断启动。
         */
        private String writeIndexZoneId;

        /**
         * 获取生效的写操作索引名模板
         *
         * @return 生效的写操作索引名模板
         */
        public String getEffectiveWriteIndexTemplate() {
            if (writeIndex != null && hasText(writeIndex.getTemplate())) {
                return writeIndex.getTemplate();
            }
            return writeIndexTemplate;
        }

        /**
         * 获取生效的读操作索引名
         *
         * @return 生效的读操作索引名
         */
        public String getEffectiveReadIndexPattern() {
            if (readIndex != null && hasText(readIndex.getPattern())) {
                return readIndex.getPattern();
            }
            return readIndexPattern;
        }

        /**
         * 获取生效的写操作日期索引时区
         *
         * @return 生效的写操作日期索引时区
         */
        public String getEffectiveWriteIndexZoneId() {
            if (writeIndex != null && hasText(writeIndex.getZoneId())) {
                return writeIndex.getZoneId();
            }
            return writeIndexZoneId;
        }

        /**
         * 获取生效的写操作日期索引时区配置项名称
         *
         * @return 生效的写操作日期索引时区配置项名称
         */
        public String getEffectiveWriteIndexZoneIdConfigName() {
            if (writeIndex != null && hasText(writeIndex.getZoneId())) {
                return SimpleElasticsearchRouteConstant.CONFIG_WRITE_INDEX_ZONE_ID;
            }
            return SimpleElasticsearchRouteConstant.CONFIG_WRITE_INDEX_ZONE_ID_LEGACY;
        }

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

    /**
     * rule 级写操作索引配置
     */
    @Data
    public static class WriteIndexConfig {
        /**
         * 写操作索引名模板
         */
        private String template;

        /**
         * 写操作日期索引时区
         */
        private String zoneId;
    }

    /**
     * rule 级读操作索引配置
     */
    @Data
    public static class ReadIndexConfig {
        /**
         * 读操作索引名或通配符
         */
        private String pattern;
    }
}
