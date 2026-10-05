package io.github.surezzzzzz.sdk.elasticsearch.search.metadata;

import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties.IndexConfig;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.ResolvedIndex;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest.DateRange;
import lombok.extern.slf4j.Slf4j;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static io.github.surezzzzzz.sdk.elasticsearch.search.constant.SearchProtocolConstant.*;
import static io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.*;

/**
 * 索引允许列表与日期范围预检；不使用写模板，也不查询多个集群后拼结果。
 */
@Slf4j
public class SearchIndexResolver {
    private final SimpleElasticsearchSearchProperties properties;
    private final RouteResolver routes;
    private final SimpleElasticsearchRouteProperties routeProperties;

    /**
     * 启动期检查固定配置与通配规则归属，不用日期抽样冒充全域证明。
     */
    public SearchIndexResolver(SimpleElasticsearchSearchProperties properties, RouteResolver routes,
                               SimpleElasticsearchRouteProperties routeProperties) {
        this.properties = properties;
        this.routes = routes;
        this.routeProperties = routeProperties;
        validate();
    }

    /**
     * 固定单位时长；不接受 ES date-math 或未受限的任意字符串。
     */
    public static long durationMillis(String value, long max) {
        if (!text(value) || !value.matches(REGEX_DURATION)) throw invalid();
        try {
            long amount = Long.parseLong(value.substring(0, value.length() - 1));
            char unit = value.charAt(value.length() - 1);
            long multiplier = unit == CHAR_SECOND ? MILLIS_PER_SECOND : unit == CHAR_MINUTE ? MILLIS_PER_MINUTE : unit == CHAR_HOUR ? MILLIS_PER_HOUR : MILLIS_PER_DAY;
            long result = Math.multiplyExact(amount, multiplier);
            if (result > max) throw invalid();
            return result;
        } catch (RuntimeException error) {
            throw invalid();
        }
    }

    /**
     * 精确 alias 优先；物理索引命中多个配置时拒绝。
     */
    public IndexConfig find(String identifier) {
        if (!text(identifier)) throw invalid();
        IndexConfig found = null;
        for (IndexConfig config : properties.getIndices()) {
            if (identifier.equals(identifier(config)) || identifier.equals(config.getName())) return config;
            if (!wildcard(identifier) && matches(config.getName(), identifier)) {
                if (found != null) throw invalid();
                found = config;
            }
        }
        if (found == null) throw invalid();
        return found;
    }

    /**
     * 配置 identifier 不等同于 Elasticsearch alias。
     */
    public String identifier(IndexConfig config) {
        return text(config.getAlias()) ? config.getAlias() : config.getName();
    }

    /**
     * 解析一次读取，日期末日输入覆盖整天，时间戳上界为排他边界。
     */
    public ResolvedIndex resolve(String requested, DateRange range) {
        IndexConfig config = find(requested);
        ZoneId zone = ZoneId.of(config.getZoneId());
        Instant from = null, to = null;
        if (range != null) {
            if (!text(range.getFrom()) || !text(range.getTo())) throw invalid();
            from = parse(range.getFrom(), zone, false);
            to = parse(range.getTo(), zone, true);
            if (!from.isBefore(to)) throw invalid();
        } else if (config.isDateSplit()) {
            String fallback = properties.getQueryLimits().getDefaultDateRange();
            if (text(fallback)) {
                long millis = durationMillis(fallback, Long.MAX_VALUE);
                to = Instant.now();
                from = to.minusMillis(millis);
            } else if (!properties.getQueryLimits().isAllowFullScan()) throw invalid();
        }
        if (from != null && !text(config.getDateField())) throw invalid();
        boolean physical = !requested.equals(identifier(config)) && !requested.equals(config.getName());
        List<String> indices = new ArrayList<>();
        int level = 0;
        if (physical) indices.add(requested);
        else if (config.isDateSplit() && from != null) {
            LocalDate first = from.atZone(zone).toLocalDate();
            LocalDate last = to.minusNanos(1).atZone(zone).toLocalDate();
            long days = ChronoUnit.DAYS.between(first, last) + 1;
            int limit = properties.getQueryLimits().getMaxIndices();
            if (days <= limit) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(config.getDatePattern());
                for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1))
                    indices.add(config.getName().replace(WILDCARD_STAR, date.format(formatter)));
            } else if (properties.getQueryLimits().isStrictDateFilter()
                    && VALUE_YYYY_MM_DD.equals(config.getDatePattern())) {
                LocalDate month = first.withDayOfMonth(1), lastMonth = last.withDayOfMonth(1);
                long months = ChronoUnit.MONTHS.between(month, lastMonth) + 1;
                if (months <= limit) {
                    level = 1;
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(VALUE_YYYY_MM);
                    for (LocalDate date = month; !date.isAfter(lastMonth); date = date.plusMonths(1))
                        indices.add(config.getName().replace(WILDCARD_STAR, date.format(formatter) + REGEX_MATCH_ANY));
                } else if ((long) last.getYear() - first.getYear() + 1 <= limit) {
                    level = DOWNGRADE_YEAR;
                    for (int year = first.getYear(); year <= last.getYear(); year++)
                        indices.add(config.getName().replace(WILDCARD_STAR, year + REGEX_MATCH_ANY));
                } else if (properties.getQueryLimits().isAllowFullScan()) {
                    level = DOWNGRADE_WILDCARD;
                    indices.add(config.getName());
                } else throw invalid();
            } else if (properties.getQueryLimits().isAllowFullScan() && properties.getQueryLimits().isStrictDateFilter()) {
                level = DOWNGRADE_WILDCARD;
                indices.add(config.getName());
            } else throw invalid();
        } else indices.add(config.getName());
        String[] actual = new LinkedHashSet<>(indices).toArray(new String[0]);
        if (actual.length == 0 || actual.length > properties.getQueryLimits().getMaxIndices()) throw invalid();
        String source = datasource(actual);
        log.debug("查询索引解析 datasource={} count={} downgrade={}", source, actual.length, level);
        return new ResolvedIndex(config, identifier(config), actual, source, from == null ? null : from.toString(), to == null ? null : to.toString(), level);
    }

    /**
     * 续页复用 token 的冻结目标；重新校验允许列表和 Route 归属。
     */
    public ResolvedIndex resume(String requested, String[] actual, String source, String from, String to, int level) {
        IndexConfig config = find(requested);
        if (actual == null || actual.length == 0 || actual.length > properties.getQueryLimits().getMaxIndices())
            throw cursor();
        for (String index : actual) {
            requireIndex(index);
            if (!matches(config.getName(), index)) throw cursor();
        }
        if (!source.equals(datasource(actual))) throw cursor();
        return new ResolvedIndex(config, identifier(config), actual.clone(), source, from, to, level);
    }

    /**
     * 任一候选跨源立即失败；多源通配仅接受可证明的单个结尾星号。
     */
    public String datasource(String... indices) {
        if (indices == null || indices.length == 0) throw invalid();
        for (String index : indices) requireIndex(index);
        String source;
        try {
            source = routes.resolveDataSourceOrThrow(indices);
        } catch (RuntimeException error) {
            throw invalid();
        }
        if (!routeProperties.getSources().containsKey(source)) throw invalid();
        if (routeProperties.getSources().size() > 1) for (String index : indices)
            if (wildcard(index)) {
                String current = prefix(index);
                if (current.isEmpty() || !index.equals(current + WILDCARD_STAR)) throw invalid();
                boolean covered = false;
                for (SimpleElasticsearchRouteProperties.RouteRule rule : routeProperties.getRules()) {
                    if (!rule.isEnable()) continue;
                    String type = rule.getType(), pattern = rule.getPattern();
                    if (!source.equals(rule.getDatasource())) {
                        if (!Arrays.asList(VALUE_EXACT, VALUE_PREFIX, VALUE_WILDCARD).contains(type)) throw invalid();
                        String other = prefix(pattern);
                        if (current.startsWith(other) || other.startsWith(current)) throw invalid();
                    } else {
                        String owner = prefix(pattern);
                        if ((VALUE_PREFIX.equals(type) || (VALUE_WILDCARD.equals(type) && pattern.equals(owner + WILDCARD_STAR)))
                                && current.startsWith(owner)) covered = true;
                    }
                }
                if (!covered) throw invalid();
            }
        return source;
    }

    private void validate() {
        try {
            SimpleElasticsearchSearchProperties.QueryLimits limits = properties.getQueryLimits();
            if (limits == null || properties.getApi() == null || properties.getCursor() == null || properties.getMappingRefresh() == null
                    || properties.getExpression() == null || properties.getExpression().getMaxLength() <= 0 || properties.getExpression().getMaxTokens() <= 0
                    || properties.getIndices() == null || properties.getIndices().isEmpty()
                    || limits.getDefaultSize() <= 0 || limits.getMaxSize() < limits.getDefaultSize() || limits.getMaxOffset() < limits.getMaxSize()
                    || limits.getMaxIndices() <= 0 || limits.getMaxDepth() <= 0 || limits.getMaxNodes() <= 0 || limits.getMappingCacheSize() <= 0
                    || properties.getMappingRefresh().getIntervalSeconds() <= 0) throw config();
            if (text(limits.getDefaultDateRange())) durationMillis(limits.getDefaultDateRange(), Long.MAX_VALUE);
            durationMillis(properties.getCursor().getKeepAlive(), io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant.MAX_CURSOR_SECONDS * MILLIS_PER_SECOND_LONG);
            Set<String> identifiers = new HashSet<>();
            Set<String> labels = new HashSet<>();
            for (IndexConfig value : properties.getIndices()) {
                if (value == null || !labels.add(value.getName()) || text(value.getAlias()) && !value.getAlias().equals(value.getName()) && !labels.add(value.getAlias()))
                    throw config();
            }
            for (IndexConfig config : properties.getIndices()) {
                requireIndex(config.getName());
                if (!identifiers.add(identifier(config)) || config.getSensitiveFields() == null || config.getFieldMapping() == null)
                    throw config();
                Map<String, String> fieldLabels = new HashMap<>();
                for (Map.Entry<String, List<String>> entry : config.getFieldMapping().entrySet()) {
                    if (!text(entry.getKey()) || entry.getValue() == null || entry.getValue().stream().anyMatch(label -> !text(label)))
                        throw config();
                    for (String label : entry.getValue()) {
                        String previous = fieldLabels.putIfAbsent(label, entry.getKey());
                        if (previous != null && !previous.equals(entry.getKey())) throw config();
                        if (config.getFieldMapping().containsKey(label) && !label.equals(entry.getKey()))
                            throw config();
                    }
                }
                ZoneId.of(config.getZoneId());
                if (config.isDateSplit()) {
                    String prefix = prefix(config.getName());
                    if (!config.getName().equals(prefix + WILDCARD_STAR) || config.getName().indexOf(CHAR_WILDCARD_SINGLE) >= 0 || !text(config.getDatePattern())
                            || !text(config.getDateField())) throw config();
                    String generated = config.getName().replace(WILDCARD_STAR, LocalDate.of(SAMPLE_LEAP_YEAR, SAMPLE_LEAP_MONTH, SAMPLE_LEAP_DAY).format(DateTimeFormatter.ofPattern(config.getDatePattern())));
                    requireIndex(generated);
                }
                Set<String> sensitive = new HashSet<>();
                for (SimpleElasticsearchSearchProperties.SensitiveFieldConfig rule : config.getSensitiveFields()) {
                    if (rule == null || !text(rule.getField()) || !sensitive.add(rule.getField())
                            || !Arrays.asList(VALUE_FORBIDDEN, VALUE_MASK).contains(rule.getStrategy()) || !text(rule.getMaskPattern())
                            || (rule.getMaskStart() != null && rule.getMaskStart() < 0) || (rule.getMaskEnd() != null && rule.getMaskEnd() < 0))
                        throw config();
                    if (VALUE_FORBIDDEN.equals(rule.getStrategy()) && config.getFieldMapping().keySet().stream()
                            .anyMatch(field -> field.equals(rule.getField()) || field.startsWith(rule.getField() + DOT) || rule.getField().startsWith(field + DOT)))
                        throw config();
                }
                datasource(config.getName());
            }
            if (!text(properties.getApi().getBasePath()) || !properties.getApi().getBasePath().startsWith(PATH_SEPARATOR)
                    || properties.getApi().getBasePath().contains(PATH_VARIABLE_OPEN)) throw config();
        } catch (RuntimeException error) {
            throw config();
        }
    }

    private Instant parse(String value, ZoneId zone, boolean upper) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException ignored) {
        }
        try {
            return LocalDateTime.parse(value).atZone(zone).toInstant();
        } catch (RuntimeException ignored) {
        }
        try {
            LocalDate date = LocalDate.parse(value);
            return (upper ? date.plusDays(1) : date).atStartOfDay(zone).toInstant();
        } catch (RuntimeException error) {
            throw invalid();
        }
    }
}
