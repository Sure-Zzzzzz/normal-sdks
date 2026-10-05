package io.github.surezzzzzz.sdk.elasticsearch.search.agg;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.PipelineAggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.AggType;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.PipelineAggType;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.ResolvedIndex;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.SensitiveFieldProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.SearchDslBuilder;
import lombok.RequiredArgsConstructor;

import java.util.*;

import static io.github.surezzzzzz.sdk.elasticsearch.search.constant.SearchProtocolConstant.*;
import static io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.*;

/**
 * 已发布结构化聚合能力的 JSON 实现；不保留 ES 6 反射或原响应旁路。
 */
@RequiredArgsConstructor
public class DefaultAggregationDslBuilder implements AggregationDslBuilder {
    private final SearchDslBuilder queries;
    private final SensitiveFieldProcessor sensitive;
    private final SimpleElasticsearchSearchProperties properties;

    /**
     * 将结构化定义编译为校验后的协议对象。
     */
    public Map<String, Object> build(List<AggDefinition> definitions, Map<String, Map<String, Object>> after, ResolvedIndex target, Map<String, FieldMetadata> fields) {
        if (definitions == null || definitions.isEmpty()) throw invalid();
        Map<String, Map<String, Object>> cursors = after == null ? Collections.emptyMap() : after;
        Set<String> consumed = new HashSet<>(), names = new HashSet<>();
        Map<String, Object> result = definitions(definitions, cursors, consumed, names, target, fields, 0, new int[]{0});
        if (!consumed.equals(cursors.keySet())) throw invalid();
        return result;
    }

    private Map<String, Object> definitions(List<AggDefinition> values, Map<String, Map<String, Object>> after, Set<String> consumed,
                                            Set<String> names, ResolvedIndex target, Map<String, FieldMetadata> fields, int depth, int[] nodes) {
        if (depth > properties.getQueryLimits().getMaxDepth()) throw invalid();
        Map<String, Object> result = new LinkedHashMap<>();
        for (AggDefinition value : values) {
            if (value == null || !name(value.getName()) || !names.add(value.getName()) || ++nodes[0] > properties.getQueryLimits().getMaxNodes())
                throw invalid();
            AggType type;
            try {
                type = value.getTypeEnum();
            } catch (RuntimeException error) {
                throw invalid();
            }
            if (type == null) throw invalid();
            if (value.getSize() != null && (value.getSize() <= 0 || value.getSize() > properties.getQueryLimits().getMaxSize()))
                throw invalid();
            if (type.isMetrics() && ((value.getAggs() != null && !value.getAggs().isEmpty()) || (value.getPipelineAggs() != null && !value.getPipelineAggs().isEmpty())))
                throw invalid();
            Map<String, Object> config = new LinkedHashMap<>();
            boolean composite = Boolean.TRUE.equals(value.getComposite());
            if (composite && !Arrays.asList(AggType.TERMS, AggType.DATE_HISTOGRAM, AggType.HISTOGRAM).contains(type))
                throw invalid();
            if (type != AggType.FILTER && type != AggType.FILTERS) {
                sensitive.require(target.getConfig(), value.getField(), true);
                config.put(VALUE_FIELD, queries.field(value.getField(), target, fields, true));
            }
            String fieldType = type == AggType.FILTER || type == AggType.FILTERS ? null : fields.get(value.getField()).getType();
            boolean numeric = Arrays.asList(VALUE_BYTE, VALUE_SHORT, VALUE_INTEGER, VALUE_LONG, VALUE_UNSIGNED_LONG, VALUE_HALF_FLOAT, VALUE_FLOAT, VALUE_DOUBLE, VALUE_SCALED_FLOAT).contains(fieldType);
            if ((Arrays.asList(AggType.SUM, AggType.AVG, AggType.MIN, AggType.MAX, AggType.STATS, AggType.EXTENDED_STATS,
                    AggType.PERCENTILES, AggType.PERCENTILE_RANKS, AggType.HISTOGRAM, AggType.RANGE).contains(type) && !numeric)
                    || type == AggType.DATE_RANGE && !Arrays.asList(VALUE_DATE, VALUE_DATE_NANOS).contains(fieldType) || type == AggType.IP_RANGE && !VALUE_IP.equals(fieldType))
                throw field();
            switch (type) {
                case TERMS:
                    if (!composite && value.getSize() != null) config.put(VALUE_SIZE, value.getSize());
                    if (value.getOrder() != null) {
                        if (!Arrays.asList(VALUE_ASC, VALUE_DESC).contains(value.getOrder())) throw invalid();
                        config.put(VALUE_ORDER, composite ? value.getOrder() : node(VALUE_ES_COUNT, value.getOrder()));
                    }
                    break;
                case DATE_HISTOGRAM:
                    if (!Arrays.asList(VALUE_DATE, VALUE_DATE_NANOS).contains(fields.get(value.getField()).getType()) || !text(value.getInterval()))
                        throw field();
                    boolean calendar = Arrays.asList(VALUE_1M, VALUE_1H, VALUE_1D, VALUE_1W, CALENDAR_MONTH_INTERVAL, VALUE_1Q, VALUE_1Y, VALUE_MINUTE, VALUE_HOUR, VALUE_DAY, VALUE_WEEK, VALUE_MONTH, VALUE_QUARTER, VALUE_YEAR).contains(value.getInterval());
                    if (!calendar && !value.getInterval().matches(REGEX_FIXED_INTERVAL)) throw invalid();
                    config.put(calendar ? VALUE_CALENDAR_INTERVAL : VALUE_FIXED_INTERVAL, value.getInterval());
                    if (!composite) config.put(VALUE_MIN_DOC_COUNT, 1);
                    break;
                case HISTOGRAM:
                    try {
                        double interval = Double.parseDouble(value.getInterval());
                        if (!Double.isFinite(interval) || interval <= 0) throw invalid();
                        config.put(VALUE_INTERVAL, interval);
                    } catch (RuntimeException error) {
                        throw invalid();
                    }
                    if (!composite) config.put(VALUE_MIN_DOC_COUNT, 1);
                    break;
                case RANGE:
                case DATE_RANGE:
                case IP_RANGE: {
                    if (value.getRanges() == null || value.getRanges().isEmpty() || value.getRanges().size() > properties.getQueryLimits().getMaxNodes())
                        throw invalid();
                    List<Object> ranges = new ArrayList<>();
                    Set<String> rangeKeys = new HashSet<>();
                    for (AggDefinition.Range range : value.getRanges()) {
                        if (range == null || (range.getFrom() == null && range.getTo() == null)) throw invalid();
                        for (Object bound : Arrays.asList(range.getFrom(), range.getTo()))
                            if (bound != null) {
                                if (type == AggType.RANGE && (!(bound instanceof Number) || !Double.isFinite(((Number) bound).doubleValue())))
                                    throw invalid();
                                if (type == AggType.DATE_RANGE && !(bound instanceof String) && !(bound instanceof Number))
                                    throw invalid();
                                if (type == AggType.IP_RANGE && !(bound instanceof String)) throw invalid();
                            }
                        Map<String, Object> bounds = new LinkedHashMap<>();
                        if (range.getFrom() != null) bounds.put(VALUE_FROM, range.getFrom());
                        if (range.getTo() != null) bounds.put(VALUE_TO, range.getTo());
                        if (range.getKey() != null) {
                            if (!rangeKeys.add(range.getKey())) throw invalid();
                            bounds.put(VALUE_KEY, range.getKey());
                        }
                        ranges.add(bounds);
                    }
                    config.put(VALUE_RANGES, ranges);
                    break;
                }
                case FILTER:
                    config = filter(value.getQuery(), target, fields);
                    break;
                case FILTERS: {
                    if (value.getFilters() == null || value.getFilters().isEmpty() || value.getFilters().size() > properties.getQueryLimits().getMaxNodes())
                        throw invalid();
                    Map<String, Object> filters = new LinkedHashMap<>();
                    value.getFilters().forEach((key, condition) -> {
                        if (!text(key)) throw invalid();
                        filters.put(key, filter(condition, target, fields));
                    });
                    config.put(VALUE_FILTERS, filters);
                    break;
                }
                case PERCENTILES:
                    if (value.getPercents() != null) {
                        if (value.getPercents().isEmpty() || value.getPercents().size() > properties.getQueryLimits().getMaxNodes())
                            throw invalid();
                        for (Double percent : value.getPercents())
                            if (percent == null || !Double.isFinite(percent) || percent < 0 || percent > MAX_PERCENTILE)
                                throw invalid();
                        config.put(VALUE_PERCENTS, value.getPercents());
                    }
                    break;
                case PERCENTILE_RANKS:
                    if (value.getValues() == null || value.getValues().isEmpty() || value.getValues().size() > properties.getQueryLimits().getMaxNodes())
                        throw invalid();
                    for (Double number : value.getValues())
                        if (number == null || !Double.isFinite(number)) throw invalid();
                    config.put(VALUE_VALUES, value.getValues());
                    break;
                default:
                    break;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            if (composite) {
                Map<String, Object> compositeConfig = new LinkedHashMap<>();
                compositeConfig.put(VALUE_SIZE, value.getSize() == null ? properties.getQueryLimits().getDefaultSize() : value.getSize());
                compositeConfig.put(VALUE_SOURCES, Collections.singletonList(node(value.getField(), node(type.getType(), config))));
                if (after.containsKey(value.getName())) {
                    Map<String, Object> cursor = after.get(value.getName());
                    if (cursor == null || cursor.size() != 1 || !cursor.containsKey(value.getField())) throw invalid();
                    compositeConfig.put(VALUE_AFTER, cursor);
                    consumed.add(value.getName());
                }
                body.put(VALUE_COMPOSITE, compositeConfig);
            } else body.put(type == AggType.COUNT ? VALUE_VALUE_COUNT : type.getType(), config);
            // composite 不能置于多桶聚合之下；仅允许根级或单桶 filter/missing 子级。
            if (value.getAggs() != null && type != AggType.FILTER && type != AggType.MISSING)
                for (AggDefinition child : value.getAggs())
                    if (child != null && Boolean.TRUE.equals(child.getComposite())) throw invalid();
            Map<String, Object> children = value.getAggs() == null ? new LinkedHashMap<>() : definitions(value.getAggs(), after, consumed, names, target, fields, depth + 1, nodes);
            if (value.getPipelineAggs() != null && !value.getPipelineAggs().isEmpty()) {
                if (composite || !type.isBucket() || Arrays.asList(AggType.FILTER, AggType.MISSING).contains(type))
                    throw invalid();
                for (PipelineAggDefinition pipeline : value.getPipelineAggs()) {
                    if (pipeline == null || !name(pipeline.getName()) || !names.add(pipeline.getName()) || ++nodes[0] > properties.getQueryLimits().getMaxNodes())
                        throw invalid();
                    PipelineAggType pipelineType = PipelineAggType.fromCode(pipeline.getType());
                    Map<String, Object> pipelineConfig = new LinkedHashMap<>();
                    if (pipelineType == PipelineAggType.BUCKET_SORT) {
                        if (pipeline.getSort() == null || pipeline.getSort().isEmpty()) throw invalid();
                        List<Object> sorts = new ArrayList<>();
                        for (Map.Entry<String, String> order : pipeline.getSort().entrySet()) {
                            path(order.getKey(), value.getAggs(), true);
                            if (!Arrays.asList(VALUE_ASC, VALUE_DESC).contains(order.getValue())) throw invalid();
                            sorts.add(node(order.getKey(), node(VALUE_ORDER, order.getValue())));
                        }
                        pipelineConfig.put(VALUE_SORT, sorts);
                        if (pipeline.getSize() != null) {
                            if (pipeline.getSize() <= 0 || pipeline.getSize() > properties.getQueryLimits().getMaxSize())
                                throw invalid();
                            pipelineConfig.put(VALUE_SIZE, pipeline.getSize());
                        }
                        if (pipeline.getFrom() != null) {
                            if (pipeline.getFrom() < 0) throw invalid();
                            pipelineConfig.put(VALUE_FROM, pipeline.getFrom());
                        }
                    } else if (pipelineType == PipelineAggType.BUCKET_SELECTOR) {
                        if (!text(pipeline.getScript()) || pipeline.getBucketsPath() == null || pipeline.getBucketsPath().isEmpty())
                            throw invalid();
                        for (Map.Entry<String, String> entry : pipeline.getBucketsPath().entrySet()) {
                            if (!name(entry.getKey())) throw invalid();
                            path(entry.getValue(), value.getAggs(), false);
                        }
                        pipelineConfig.put(VALUE_BUCKETS_PATH, pipeline.getBucketsPath());
                        pipelineConfig.put(VALUE_SCRIPT, pipeline.getScript());
                    } else throw invalid();
                    children.put(pipeline.getName(), node(pipelineType.getCode(), pipelineConfig));
                }
            }
            if (!children.isEmpty()) body.put(VALUE_AGGS, children);
            result.put(value.getName(), body);
        }
        return result;
    }

    private Map<String, Object> filter(io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition condition, ResolvedIndex target, Map<String, FieldMetadata> fields) {
        // 接口扩展不应被强制转成默认实现；无日期范围的 target 专供局部 filter。
        ResolvedIndex local = new ResolvedIndex(target.getConfig(), target.getIdentifier(), target.getIndices(), target.getDatasource(), null, null, target.getDowngradeLevel());
        return queries.query(condition, local, fields);
    }

    private boolean name(String value) {
        return text(value) && value.matches(REGEX_AGG_NAME) && !Arrays.asList(VALUE_KEY, VALUE_COUNT).contains(value);
    }

    private void path(String value, List<AggDefinition> siblings, boolean sort) {
        if (!text(value) || !value.matches(REGEX_PIPELINE_PATH)) throw invalid();
        if (VALUE_ES_COUNT.equals(value) || sort && VALUE_ES_KEY.equals(value)) return;
        String[] chain = value.split(PATH_AGG_CHILD_SEPARATOR, -1);
        List<AggDefinition> children = siblings;
        for (int i = 0; i < chain.length; i++) {
            String[] leaf = chain[i].split(REGEX_LITERAL_DOT, -1);
            if (leaf.length > METRIC_PATH_PART_COUNT || children == null) throw invalid();
            AggDefinition current = null;
            for (AggDefinition item : children) if (item != null && leaf[0].equals(item.getName())) current = item;
            if (current == null || Boolean.TRUE.equals(current.getComposite())) throw invalid();
            AggType type = current.getTypeEnum();
            if (i < chain.length - 1) {
                if (leaf.length != 1 || (type != AggType.FILTER && type != AggType.MISSING)) throw invalid();
                children = current.getAggs();
                if (i + 1 == chain.length - 1 && VALUE_ES_COUNT.equals(chain[i + 1])) return;
            } else {
                if (!type.isMetrics()) throw invalid();
                boolean multi = type == AggType.STATS || type == AggType.EXTENDED_STATS;
                if (multi) {
                    List<String> keys = new ArrayList<>(Arrays.asList(VALUE_COUNT, VALUE_MIN, VALUE_MAX, VALUE_AVG, VALUE_SUM));
                    if (type == AggType.EXTENDED_STATS)
                        keys.addAll(Arrays.asList(VALUE_SUM_OF_SQUARES, VALUE_VARIANCE, VALUE_VARIANCE_POPULATION,
                                VALUE_VARIANCE_SAMPLING, VALUE_STD_DEVIATION, VALUE_STD_DEVIATION_POPULATION, VALUE_STD_DEVIATION_SAMPLING));
                    if (leaf.length != METRIC_PATH_PART_COUNT || !keys.contains(leaf[1])) throw invalid();
                } else if (leaf.length != 1 || type == AggType.PERCENTILES || type == AggType.PERCENTILE_RANKS)
                    throw invalid();
            }
        }
    }
}
