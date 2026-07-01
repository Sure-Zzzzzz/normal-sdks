package io.github.surezzzzzz.sdk.elasticsearch.route.validator;

import io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent;
import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.RouteMatchType;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ServerVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Simple Elasticsearch Route 配置校验器
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchRouteComponent
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = SimpleElasticsearchRouteConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
public class SimpleElasticsearchRouteValidator {

    private final SimpleElasticsearchRouteProperties properties;

    @PostConstruct
    public void init() {
        log.info("Simple Elasticsearch Route 启用状态：{}", properties.isEnable());
        log.info("默认数据源：{}", properties.getDefaultSource());
        log.info("已配置数据源：{}", properties.getSources().keySet());
        log.info("已配置路由规则数：{}", properties.getRules().size());
        log.info("服务端版本探测启用状态：{}，探测失败是否快速失败：{}",
                properties.getVersionDetect().isEnabled(), properties.getVersionDetect().isFailFastOnDetectError());

        validate();
    }

    /**
     * 校验配置
     */
    private void validate() {
        if (!properties.isEnable()) {
            return;
        }

        try {
            // 1. 校验数据源不能为空
            if (CollectionUtils.isEmpty(properties.getSources())) {
                throw new ConfigurationException(ErrorCode.CONFIG_SOURCES_EMPTY,
                        ErrorMessage.CONFIG_SOURCES_EMPTY);
            }

            // 2. 校验默认数据源必须存在
            if (!properties.getSources().containsKey(properties.getDefaultSource())) {
                throw new ConfigurationException(ErrorCode.CONFIG_DEFAULT_SOURCE_NOT_FOUND,
                        String.format(ErrorMessage.CONFIG_DEFAULT_SOURCE_NOT_FOUND,
                                properties.getDefaultSource(), properties.getSources().keySet()));
            }

            // 3. 校验每个数据源配置
            properties.getSources().forEach(this::validateDataSourceConfig);

            validateVersionDetectConfig(properties.getVersionDetect());

            // 3.1 全局写索引配置新旧冲突提示
            validateGlobalWriteIndexConfigCompatibility();

            // 4. 校验路由规则
            if (!CollectionUtils.isEmpty(properties.getRules())) {
                validateRouteRules(properties.getRules());
            }

            log.info("Simple Elasticsearch Route 配置校验通过");

        } catch (ConfigurationException e) {
            throw new ConfigurationException(ErrorCode.CONFIG_VALIDATION_FAILED,
                    ErrorMessage.CONFIG_VALIDATION_FAILED, e);
        }
    }

    /**
     * 校验数据源配置
     */
    private void validateDataSourceConfig(String key, SimpleElasticsearchRouteProperties.DataSourceConfig config) {
        String prefix = String.format(SimpleElasticsearchRouteConstant.TEMPLATE_DATASOURCE_PREFIX, key);

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

        // 5.1 异步写线程池大小
        if (config.getAsyncWriteThreadPoolSize() <= 0) {
            throw new ConfigurationException(ErrorCode.CONFIG_ASYNC_WRITE_THREAD_POOL_INVALID,
                    prefix + "异步写线程池大小必须大于 0");
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
    private void validateRouteRules(java.util.List<SimpleElasticsearchRouteProperties.RouteRule> rules) {
        java.util.Map<String, Integer> exactPatterns = new java.util.HashMap<>();
        java.util.Map<Integer, Integer> priorityCount = new java.util.HashMap<>();

        for (int i = 0; i < rules.size(); i++) {
            SimpleElasticsearchRouteProperties.RouteRule rule = rules.get(i);
            String rulePrefix = String.format(SimpleElasticsearchRouteConstant.TEMPLATE_RULE_PREFIX, i + 1);

            // 1. pattern 不能为空
            if (isEmpty(rule.getPattern())) {
                throw new ConfigurationException(ErrorCode.CONFIG_ROUTE_PATTERN_EMPTY,
                        rulePrefix + ErrorMessage.CONFIG_ROUTE_PATTERN_EMPTY);
            }

            // 2. 数据源不能为空
            if (isEmpty(rule.getDatasource())) {
                throw new ConfigurationException(ErrorCode.CONFIG_ROUTE_DATASOURCE_NOT_FOUND,
                        rulePrefix + "datasource 不能为空");
            }

            // 2.1 数据源必须存在
            if (!properties.getSources().containsKey(rule.getDatasource())) {
                throw new ConfigurationException(ErrorCode.CONFIG_ROUTE_DATASOURCE_NOT_FOUND,
                        rulePrefix + String.format(ErrorMessage.CONFIG_ROUTE_DATASOURCE_NOT_FOUND,
                                rule.getPattern(), rule.getDatasource(), properties.getSources().keySet()));
            }

            // 2.2 新旧索引配置冲突提示
            validateIndexConfigCompatibility(rule, rulePrefix);

            // 2.3 writeIndexTemplate 格式校验
            if (!isEmpty(rule.getEffectiveWriteIndexTemplate())) {
                validateWriteIndexTemplate(rule.getEffectiveWriteIndexTemplate(), rulePrefix);
            }

            // 2.4 rule 级时区校验
            if (!isEmpty(rule.getEffectiveWriteIndexZoneId())) {
                validateWriteIndexZoneId(rule.getEffectiveWriteIndexZoneId(),
                        rule.getEffectiveWriteIndexZoneIdConfigName(), rulePrefix);
            }

            if (!RouteMatchType.isValid(rule.getType())) {
                throw new ConfigurationException(ErrorCode.CONFIG_ROUTE_TYPE_INVALID,
                        rulePrefix + String.format(ErrorMessage.CONFIG_ROUTE_TYPE_INVALID,
                                rule.getPattern(), rule.getType()));
            }

            // 4. priority 范围
            if (rule.getPriority() < SimpleElasticsearchRouteConstant.PRIORITY_MIN
                    || rule.getPriority() > SimpleElasticsearchRouteConstant.PRIORITY_MAX) {
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

    private void validateVersionDetectConfig(SimpleElasticsearchRouteProperties.VersionDetectConfig config) {
        if (config == null) {
            return;
        }
        if (config.getTimeoutMs() != null && config.getTimeoutMs() <= 0) {
            throw new ConfigurationException(ErrorCode.CONFIG_VERSION_DETECT_TIMEOUT_INVALID,
                    ErrorMessage.CONFIG_VERSION_DETECT_TIMEOUT_INVALID);
        }
    }

    /**
     * 校验全局写索引新旧配置冲突（冲突只 warn，不抛异常）
     */
    private void validateGlobalWriteIndexConfigCompatibility() {
        if (properties.getWriteIndex() == null) {
            return;
        }
        warnIfBothConfigured("", SimpleElasticsearchRouteConstant.CONFIG_WRITE_INDEX_ZONE_ID, properties.getWriteIndex().getZoneId(),
                SimpleElasticsearchRouteConstant.CONFIG_WRITE_INDEX_ZONE_ID_LEGACY, properties.getWriteIndexZoneId());
    }

    /**
     * 校验 rule 级索引新旧配置冲突（冲突只 warn，不抛异常）
     */
    private void validateIndexConfigCompatibility(SimpleElasticsearchRouteProperties.RouteRule rule, String rulePrefix) {
        if (rule.getWriteIndex() != null) {
            warnIfBothConfigured(rulePrefix, SimpleElasticsearchRouteConstant.CONFIG_WRITE_INDEX_TEMPLATE,
                    rule.getWriteIndex().getTemplate(),
                    SimpleElasticsearchRouteConstant.CONFIG_WRITE_INDEX_TEMPLATE_LEGACY,
                    rule.getWriteIndexTemplate());
            warnIfBothConfigured(rulePrefix, SimpleElasticsearchRouteConstant.CONFIG_WRITE_INDEX_ZONE_ID,
                    rule.getWriteIndex().getZoneId(),
                    SimpleElasticsearchRouteConstant.CONFIG_WRITE_INDEX_ZONE_ID_LEGACY,
                    rule.getWriteIndexZoneId());
        }
        if (rule.getReadIndex() != null) {
            warnIfBothConfigured(rulePrefix, SimpleElasticsearchRouteConstant.CONFIG_READ_INDEX_PATTERN,
                    rule.getReadIndex().getPattern(),
                    SimpleElasticsearchRouteConstant.CONFIG_READ_INDEX_PATTERN_LEGACY,
                    rule.getReadIndexPattern());
        }
    }

    /**
     * 新旧配置同时配置且值不一致时提示优先级
     */
    private void warnIfBothConfigured(String prefix, String newName, String newValue,
                                      String legacyName, String legacyValue) {
        if (!isEmpty(newValue) && !isEmpty(legacyValue) && !newValue.trim().equals(legacyValue.trim())) {
            log.warn("{}同时配置 [{}] 和 [{}]，将优先使用 [{}]", prefix, newName, legacyName, newName);
        }
    }

    /**
     * 校验 writeIndexTemplate 格式（非法只 warn，不抛异常）
     */
    private void validateWriteIndexTemplate(String template, String rulePrefix) {
        int start = template.indexOf("{");
        int end = template.lastIndexOf("}");
        if (start == -1 || end == -1 || start >= end) {
            return;
        }
        String pattern = template.substring(start + 1, end);
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            LocalDate.now().format(formatter);
        } catch (IllegalArgumentException | DateTimeException e) {
            log.warn("{}writeIndexTemplate 日期模板不可渲染，pattern=[{}]，错误=[{}]，运行时将使用原始模板",
                    rulePrefix, pattern, e.getMessage());
        }
    }

    /**
     * 校验 writeIndexZoneId 格式（非法只 warn，不抛异常）
     */
    private void validateWriteIndexZoneId(String zoneId, String configName, String prefix) {
        if (isEmpty(zoneId)) {
            return;
        }
        try {
            ZoneId.of(zoneId);
        } catch (Exception e) {
            log.warn("{}{}=[{}] 非法，将降级为全局默认时区；全局不可用时使用 JVM 默认时区，错误=[{}]",
                    prefix, configName, zoneId, e.getMessage());
        }
    }

    /**
     * 判断字符串是否为空
     */
    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
}
