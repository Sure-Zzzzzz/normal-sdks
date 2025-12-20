package io.github.surezzzzzz.sdk.elasticsearch.search.configuration;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SensitiveStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.ConfigurationException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple Elasticsearch Search Properties
 *
 * @author surezzzzzz
 */
@Getter
@Setter
@NoArgsConstructor
@Slf4j
@SimpleElasticsearchSearchComponent
@ConfigurationProperties("io.github.surezzzzzz.sdk.elasticsearch.search")
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.elasticsearch.search", name = "enable", havingValue = "true")
public class SimpleElasticsearchSearchProperties {

    /**
     * 是否启用 Search
     */
    private boolean enable = false;

    /**
     * 索引配置列表
     */
    private List<IndexConfig> indices = new ArrayList<>();

    /**
     * Mapping 刷新配置
     */
    private MappingRefreshConfig mappingRefresh = new MappingRefreshConfig();

    /**
     * 查询限制配置
     */
    private QueryLimitsConfig queryLimits = new QueryLimitsConfig();

    /**
     * API 配置
     */
    private ApiConfig api = new ApiConfig();

    @PostConstruct
    public void init() {
        log.info("Simple Elasticsearch Search enabled: {}", enable);
        log.info("Configured indices: {}", indices.size());

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
            // 1. 校验索引配置不能为空
            if (CollectionUtils.isEmpty(indices)) {
                throw new ConfigurationException(ErrorCode.INDICES_CONFIG_REQUIRED, ErrorMessage.INDICES_CONFIG_REQUIRED);
            }

            // 2. 校验每个索引配置
            for (IndexConfig indexConfig : indices) {
                validateIndexConfig(indexConfig);
            }

            // 3. 校验标识符唯一性（有alias用alias，没有用name）
            List<String> identifiers = new ArrayList<>();
            for (IndexConfig indexConfig : indices) {
                String identifier = isEmpty(indexConfig.getAlias()) ? indexConfig.getName() : indexConfig.getAlias();
                if (identifiers.contains(identifier)) {
                    throw new ConfigurationException(ErrorCode.INDEX_ALIAS_DUPLICATE,
                            String.format(ErrorMessage.INDEX_ALIAS_DUPLICATE, identifier));
                }
                identifiers.add(identifier);
            }

            log.info("Configuration validation passed");

        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(ErrorMessage.CONFIG_VALIDATION_FAILED, e);
        }
    }

    /**
     * 校验索引配置
     */
    private void validateIndexConfig(IndexConfig config) {
        // 1. name 不能为空
        if (isEmpty(config.getName())) {
            throw new ConfigurationException(ErrorCode.INDEX_NAME_REQUIRED, ErrorMessage.INDEX_NAME_REQUIRED);
        }

        // 2. 获取标识符（有alias用alias，没有用name）
        String identifier = isEmpty(config.getAlias()) ? config.getName() : config.getAlias();

        // 3. 如果启用日期分割，必须配置 date-pattern（date-field 可选，用于添加 DSL 时间过滤）
        if (config.isDateSplit()) {
            if (isEmpty(config.getDatePattern())) {
                throw new ConfigurationException(ErrorCode.DATE_PATTERN_REQUIRED,
                        String.format(ErrorMessage.DATE_PATTERN_REQUIRED, identifier));
            }
            // date-field 是可选的，如果配置了，会在查询时自动添加日期范围过滤
            // 如果未配置，仅影响索引路由，不影响 DSL 查询条件
        }

        // 4. 校验敏感字段配置
        if (!CollectionUtils.isEmpty(config.getSensitiveFields())) {
            for (SensitiveFieldConfig sensitiveField : config.getSensitiveFields()) {
                if (isEmpty(sensitiveField.getField())) {
                    throw new ConfigurationException(ErrorCode.SENSITIVE_FIELD_NAME_REQUIRED,
                            String.format(ErrorMessage.SENSITIVE_FIELD_NAME_REQUIRED, identifier));
                }
                if (isEmpty(sensitiveField.getStrategy())) {
                    throw new ConfigurationException(ErrorCode.SENSITIVE_FIELD_STRATEGY_REQUIRED,
                            String.format(ErrorMessage.SENSITIVE_FIELD_STRATEGY_REQUIRED,
                                    identifier, sensitiveField.getField()));
                }

                // 验证 strategy 值是否合法
                String strategy = sensitiveField.getStrategy().toLowerCase();
                if (!SensitiveStrategy.FORBIDDEN.getStrategy().equals(strategy) &&
                        !SensitiveStrategy.MASK.getStrategy().equals(strategy)) {
                    throw new ConfigurationException(ErrorCode.SENSITIVE_FIELD_STRATEGY_INVALID,
                            String.format(ErrorMessage.SENSITIVE_FIELD_STRATEGY_INVALID,
                                    identifier, sensitiveField.getField(), sensitiveField.getStrategy()));
                }
            }
        }
    }

    /**
     * 判断字符串是否为空
     */
    private boolean isEmpty(String str) {
        return !StringUtils.hasText(str);
    }

    // ========== 静态内部类 ==========

    /**
     * 索引配置
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class IndexConfig {
        /**
         * 索引名称（支持通配符，如: app_log_*）
         */
        private String name;

        /**
         * 索引别名（用于 API 查询，可选，默认使用 name）
         * 注意：这不是 ES 的原生 alias，只是应用层的别名
         */
        private String alias;

        /**
         * 是否为日期分割索引
         */
        private boolean dateSplit = false;

        /**
         * 日期格式（如: yyyy.MM.dd）
         */
        private String datePattern;

        /**
         * 日期字段名（索引中的时间字段）
         */
        private String dateField;

        /**
         * 是否缓存 mapping
         */
        private boolean cacheMapping = true;

        /**
         * 是否懒加载（true: 首次查询时加载，false: 启动时加载）
         */
        private boolean lazyLoad = false;

        /**
         * 敏感字段配置
         */
        private List<SensitiveFieldConfig> sensitiveFields = new ArrayList<>();
    }

    /**
     * 敏感字段配置
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class SensitiveFieldConfig {
        /**
         * 字段名
         */
        private String field;

        /**
         * 处理策略: FORBIDDEN(禁止) / MASK(脱敏)
         */
        private String strategy;

        /**
         * 脱敏保留前 N 位（仅 MASK 策略有效）
         */
        private Integer maskStart;

        /**
         * 脱敏保留后 N 位（仅 MASK 策略有效）
         */
        private Integer maskEnd;

        /**
         * 脱敏字符（默认: ****）
         */
        private String maskPattern = "****";
    }

    /**
     * Mapping 刷新配置
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class MappingRefreshConfig {
        /**
         * 是否启用定时刷新
         */
        private boolean enabled = false;

        /**
         * 刷新间隔（秒）
         */
        private int intervalSeconds = 300;
    }

    /**
     * 查询限制配置
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class QueryLimitsConfig {
        /**
         * 单次查询最大返回数量
         */
        private int maxSize = 10000;

        /**
         * 默认分页大小
         */
        private int defaultSize = 20;

        /**
         * from + size 的最大值（超过此值强制使用 search_after）
         */
        private int maxOffset = 10000;

        /**
         * 是否忽略不存在的索引（适用于日期分割索引场景）
         * <p>
         * true: 查询不存在的索引时不报错，返回已存在索引的数据
         * false: 查询不存在的索引时抛出 index_not_found_exception（默认）
         * </p>
         */
        private boolean ignoreUnavailableIndices = false;
    }

    /**
     * API 配置
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ApiConfig {
        /**
         * 是否启用 API
         */
        private boolean enabled = true;

        /**
         * API 路径前缀
         */
        private String basePath = "/api";

        /**
         * 是否返回 _score（评分）
         */
        private boolean includeScore = false;

        /**
         * 是否返回原始响应（仅 ES 6.x 聚合场景）
         * <p>
         * true: 在响应中包含 ES 原始 JSON，让用户自主选择使用解析后的数据或原始数据
         * false: 仅返回解析后的统一格式数据（默认）
         * </p>
         */
        private boolean includeRawResponse = false;
    }
}
