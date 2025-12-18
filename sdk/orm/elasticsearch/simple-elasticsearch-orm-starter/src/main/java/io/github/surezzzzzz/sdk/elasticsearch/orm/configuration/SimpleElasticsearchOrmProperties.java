package io.github.surezzzzzz.sdk.elasticsearch.orm.configuration;

import io.github.surezzzzzz.sdk.elasticsearch.orm.annotation.SimpleElasticsearchOrmComponent;
import io.github.surezzzzzz.sdk.elasticsearch.orm.constant.ErrorMessages;
import io.github.surezzzzzz.sdk.elasticsearch.orm.constant.SensitiveStrategy;
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
 * Simple Elasticsearch ORM Properties
 *
 * @author surezzzzzz
 */
@Getter
@Setter
@NoArgsConstructor
@Slf4j
@SimpleElasticsearchOrmComponent
@ConfigurationProperties("io.github.surezzzzzz.sdk.elasticsearch.orm")
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.elasticsearch.orm", name = "enable", havingValue = "true")
public class SimpleElasticsearchOrmProperties {

    /**
     * 是否启用 ORM
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
        log.info("Simple Elasticsearch ORM enabled: {}", enable);
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
                throw new IllegalArgumentException(ErrorMessages.INDICES_CONFIG_REQUIRED);
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
                    throw new IllegalArgumentException(String.format(ErrorMessages.INDEX_ALIAS_DUPLICATE, identifier));
                }
                identifiers.add(identifier);
            }

            log.info("Configuration validation passed");

        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(ErrorMessages.CONFIG_VALIDATION_FAILED, e);
        }
    }

    /**
     * 校验索引配置
     */
    private void validateIndexConfig(IndexConfig config) {
        // 1. name 不能为空
        if (isEmpty(config.getName())) {
            throw new IllegalArgumentException(ErrorMessages.INDEX_NAME_REQUIRED);
        }

        // 2. 获取标识符（有alias用alias，没有用name）
        String identifier = isEmpty(config.getAlias()) ? config.getName() : config.getAlias();

        // 3. 如果启用日期分割，必须配置 date-pattern 和 date-field
        if (config.isDateSplit()) {
            if (isEmpty(config.getDatePattern())) {
                throw new IllegalArgumentException(
                        String.format(ErrorMessages.DATE_PATTERN_REQUIRED, identifier));
            }
            if (isEmpty(config.getDateField())) {
                throw new IllegalArgumentException(
                        String.format(ErrorMessages.DATE_FIELD_REQUIRED, identifier));
            }
        }

        // 4. 校验敏感字段配置
        if (!CollectionUtils.isEmpty(config.getSensitiveFields())) {
            for (SensitiveFieldConfig sensitiveField : config.getSensitiveFields()) {
                if (isEmpty(sensitiveField.getField())) {
                    throw new IllegalArgumentException(
                            String.format(ErrorMessages.SENSITIVE_FIELD_NAME_REQUIRED, identifier));
                }
                if (isEmpty(sensitiveField.getStrategy())) {
                    throw new IllegalArgumentException(
                            String.format(ErrorMessages.SENSITIVE_FIELD_STRATEGY_REQUIRED,
                                    identifier, sensitiveField.getField()));
                }

                // 验证 strategy 值是否合法
                String strategy = sensitiveField.getStrategy().toLowerCase();
                if (!SensitiveStrategy.FORBIDDEN.getStrategy().equals(strategy) &&
                        !SensitiveStrategy.MASK.getStrategy().equals(strategy)) {
                    throw new IllegalArgumentException(
                            String.format(ErrorMessages.SENSITIVE_FIELD_STRATEGY_INVALID,
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
    }
}
