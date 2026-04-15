package io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.AggType;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.AggregationException;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.FieldException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.composite.*;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregationBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 聚合构建器
 * 负责将 AggDefinition 转换为 ES AggregationBuilder
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class AggregationDslBuilder {

    @Autowired
    private MappingManager mappingManager;

    /**
     * 构建聚合
     *
     * @param indexAlias     索引别名
     * @param aggDefinitions 聚合定义列表
     * @param after          composite 聚合翻页游标，key 为聚合名称，null 时不注入游标
     * @return ES AggregationBuilder 列表
     */
    public AggregationBuilder[] build(String indexAlias, List<AggDefinition> aggDefinitions,
                                      Map<String, Map<String, Object>> after) {
        if (aggDefinitions == null || aggDefinitions.isEmpty()) {
            return new AggregationBuilder[0];
        }

        IndexMetadata metadata = mappingManager.getMetadata(indexAlias);

        AggregationBuilder[] builders = new AggregationBuilder[aggDefinitions.size()];
        for (int i = 0; i < aggDefinitions.size(); i++) {
            AggDefinition def = aggDefinitions.get(i);
            Map<String, Object> afterForThis = (after != null) ? after.get(def.getName()) : null;
            builders[i] = buildAggregation(def, metadata, afterForThis);
        }

        return builders;
    }

    /**
     * 构建单个聚合
     */
    private AggregationBuilder buildAggregation(AggDefinition definition, IndexMetadata metadata,
                                                Map<String, Object> after) {
        String field = definition.getField();

        // 验证字段
        if (field != null) {
            FieldMetadata fieldMetadata = metadata.getField(field);
            if (fieldMetadata == null) {
                throw new FieldException(ErrorCode.FIELD_NOT_FOUND,
                        String.format(ErrorMessage.FIELD_NOT_FOUND, field));
            }
            if (!fieldMetadata.isAggregatable()) {
                throw new FieldException(ErrorCode.FIELD_NOT_AGGREGATABLE,
                        String.format(ErrorMessage.FIELD_NOT_AGGREGATABLE, field));
            }
        }

        // composite 聚合走独立分支
        if (Boolean.TRUE.equals(definition.getComposite())) {
            return buildCompositeAgg(definition, metadata, after);
        }

        // 普通聚合
        AggregationBuilder aggBuilder = buildByType(definition, definition.getTypeEnum());

        // 添加嵌套聚合
        if (definition.getAggs() != null && !definition.getAggs().isEmpty()) {
            for (AggDefinition subAgg : definition.getAggs()) {
                AggregationBuilder subBuilder = buildAggregation(subAgg, metadata, null);
                aggBuilder.subAggregation(subBuilder);
            }
        }

        return aggBuilder;
    }

    /**
     * 构建 composite 聚合
     */
    private CompositeAggregationBuilder buildCompositeAgg(AggDefinition definition, IndexMetadata metadata,
                                                          Map<String, Object> after) {
        AggType aggType = definition.getTypeEnum();
        String field = definition.getField();
        SortOrder sortOrder = SortOrder.fromString(
                StringUtils.hasText(definition.getOrder()) ? definition.getOrder()
                        : SimpleElasticsearchSearchConstant.COMPOSITE_DEFAULT_ORDER);

        CompositeValuesSourceBuilder<?> source;
        switch (aggType) {
            case TERMS:
                source = new TermsValuesSourceBuilder(field).field(field).order(sortOrder);
                break;
            case DATE_HISTOGRAM:
                source = new DateHistogramValuesSourceBuilder(field)
                        .field(field)
                        .dateHistogramInterval(parseDateInterval(definition.getInterval()))
                        .order(sortOrder);
                break;
            case HISTOGRAM:
                double interval = definition.getSize() != null ? definition.getSize()
                        : SimpleElasticsearchSearchConstant.DEFAULT_HISTOGRAM_INTERVAL;
                source = new HistogramValuesSourceBuilder(field).field(field).interval(interval).order(sortOrder);
                break;
            default:
                throw new AggregationException(ErrorCode.COMPOSITE_UNSUPPORTED_TYPE,
                        String.format(ErrorMessage.COMPOSITE_UNSUPPORTED_TYPE, definition.getType()));
        }

        int size = definition.getSize() != null ? definition.getSize()
                : SimpleElasticsearchSearchConstant.COMPOSITE_DEFAULT_SIZE;
        CompositeAggregationBuilder composite =
                new CompositeAggregationBuilder(definition.getName(), Collections.singletonList(source))
                        .size(size);

        // 注入翻页游标
        if (after != null && !after.isEmpty()) {
            composite.aggregateAfter(after);
        }

        // 嵌套 metrics 聚合（composite 内部只允许 metrics）
        if (definition.getAggs() != null && !definition.getAggs().isEmpty()) {
            for (AggDefinition subAgg : definition.getAggs()) {
                if (Boolean.TRUE.equals(subAgg.getComposite()) || (subAgg.getTypeEnum() != null && subAgg.getTypeEnum().isBucket())) {
                    throw new AggregationException(ErrorCode.COMPOSITE_NESTED_NOT_ALLOWED,
                            ErrorMessage.COMPOSITE_NESTED_NOT_ALLOWED);
                }
                composite.subAggregation(buildByType(subAgg, subAgg.getTypeEnum()));
            }
        }

        return composite;
    }

    /**
     * 根据类型构建普通聚合
     */
    private AggregationBuilder buildByType(AggDefinition definition, AggType aggType) {
        String name = definition.getName();
        String field = definition.getField();

        switch (aggType) {
            case SUM:
                return AggregationBuilders.sum(name).field(field);
            case AVG:
                return AggregationBuilders.avg(name).field(field);
            case MIN:
                return AggregationBuilders.min(name).field(field);
            case MAX:
                return AggregationBuilders.max(name).field(field);
            case COUNT:
                return AggregationBuilders.count(name).field(field);
            case CARDINALITY:
                return AggregationBuilders.cardinality(name).field(field);
            case STATS:
                return AggregationBuilders.stats(name).field(field);
            case EXTENDED_STATS:
                return AggregationBuilders.extendedStats(name).field(field);
            case TERMS:
                int termsSize = definition.getSize() != null ? definition.getSize() : SimpleElasticsearchSearchConstant.DEFAULT_TERMS_SIZE;
                return AggregationBuilders.terms(name).field(field).size(termsSize);
            case DATE_HISTOGRAM:
                return AggregationBuilders.dateHistogram(name)
                        .field(field)
                        .dateHistogramInterval(parseDateInterval(definition.getInterval()));
            case HISTOGRAM:
                double histInterval = definition.getSize() != null ? definition.getSize() : SimpleElasticsearchSearchConstant.DEFAULT_HISTOGRAM_INTERVAL;
                return AggregationBuilders.histogram(name).field(field).interval(histInterval);
            case RANGE:
                return buildRangeAggregation(definition);
            default:
                throw new AggregationException(ErrorCode.UNSUPPORTED_AGG_TYPE,
                        String.format(ErrorMessage.UNSUPPORTED_AGG_TYPE, aggType));
        }
    }

    /**
     * 构建范围聚合
     */
    private AggregationBuilder buildRangeAggregation(AggDefinition definition) {
        RangeAggregationBuilder rangeAgg = AggregationBuilders.range(definition.getName())
                .field(definition.getField());

        if (definition.getRanges() != null) {
            for (AggDefinition.Range range : definition.getRanges()) {
                if (range.getFrom() != null && range.getTo() != null) {
                    rangeAgg.addRange(range.getKey(),
                            ((Number) range.getFrom()).doubleValue(),
                            ((Number) range.getTo()).doubleValue());
                } else if (range.getFrom() != null) {
                    rangeAgg.addUnboundedFrom(range.getKey(), ((Number) range.getFrom()).doubleValue());
                } else if (range.getTo() != null) {
                    rangeAgg.addUnboundedTo(range.getKey(), ((Number) range.getTo()).doubleValue());
                }
            }
        }

        return rangeAgg;
    }

    /**
     * 解析日期间隔
     */
    private DateHistogramInterval parseDateInterval(String interval) {
        if (interval == null) {
            return DateHistogramInterval.DAY;
        }

        switch (interval.toLowerCase()) {
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_SECOND:
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_S:
                return DateHistogramInterval.SECOND;
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_MINUTE:
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_M:
                return DateHistogramInterval.MINUTE;
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_HOUR:
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_H:
                return DateHistogramInterval.HOUR;
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_DAY:
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_D:
                return DateHistogramInterval.DAY;
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_WEEK:
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_W:
                return DateHistogramInterval.WEEK;
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_MONTH:
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_MONTH_ABBR:
                return DateHistogramInterval.MONTH;
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_QUARTER:
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_Q:
                return DateHistogramInterval.QUARTER;
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_YEAR:
            case SimpleElasticsearchSearchConstant.DATE_INTERVAL_Y:
                return DateHistogramInterval.YEAR;
            default:
                return new DateHistogramInterval(interval);
        }
    }
}
