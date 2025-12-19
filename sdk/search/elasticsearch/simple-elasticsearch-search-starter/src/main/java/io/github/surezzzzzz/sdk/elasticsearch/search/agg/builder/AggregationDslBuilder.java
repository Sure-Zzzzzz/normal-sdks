package io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.AggType;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.Constants;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessages;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregationBuilder;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

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
     * @param indexAlias 索引别名
     * @param aggDefinitions  聚合定义列表
     * @return ES AggregationBuilder 列表
     */
    public AggregationBuilder[] build(String indexAlias, List<AggDefinition> aggDefinitions) {
        if (aggDefinitions == null || aggDefinitions.isEmpty()) {
            return new AggregationBuilder[0];
        }

        IndexMetadata metadata = mappingManager.getMetadata(indexAlias);

        AggregationBuilder[] builders = new AggregationBuilder[aggDefinitions.size()];
        for (int i = 0; i < aggDefinitions.size(); i++) {
            builders[i] = buildAggregation(aggDefinitions.get(i), metadata);
        }

        return builders;
    }

    /**
     * 构建单个聚合
     */
    private AggregationBuilder buildAggregation(AggDefinition definition, IndexMetadata metadata) {
        String name = definition.getName();
        String field = definition.getField();
        AggType aggType = definition.getTypeEnum();

        // 验证字段
        if (field != null) {
            FieldMetadata fieldMetadata = metadata.getField(field);
            if (fieldMetadata == null) {
                throw new IllegalArgumentException(String.format(ErrorMessages.FIELD_NOT_FOUND, field));
            }
            if (!fieldMetadata.isAggregatable()) {
                throw new IllegalArgumentException(String.format(ErrorMessages.FIELD_NOT_AGGREGATABLE, field));
            }
        }

        // 根据聚合类型构建
        AggregationBuilder aggBuilder = buildByType(definition, aggType);

        // 添加嵌套聚合
        if (definition.getAggs() != null && !definition.getAggs().isEmpty()) {
            for (AggDefinition subAgg : definition.getAggs()) {
                AggregationBuilder subBuilder = buildAggregation(subAgg, metadata);
                aggBuilder.subAggregation(subBuilder);
            }
        }

        return aggBuilder;
    }

    /**
     * 根据类型构建聚合
     */
    private AggregationBuilder buildByType(AggDefinition definition, AggType aggType) {
        String name = definition.getName();
        String field = definition.getField();

        switch (aggType) {
            // Metrics 聚合
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

            // Bucket 聚合
            case TERMS:
                int termsSize = definition.getSize() != null ? definition.getSize() : Constants.DEFAULT_TERMS_SIZE;
                return AggregationBuilders.terms(name).field(field).size(termsSize);

            case DATE_HISTOGRAM:
                DateHistogramInterval interval = parseDateInterval(definition.getInterval());
                return AggregationBuilders.dateHistogram(name)
                        .field(field)
                        .dateHistogramInterval(interval);

            case HISTOGRAM:
                double histInterval = definition.getSize() != null ? definition.getSize() : Constants.DEFAULT_HISTOGRAM_INTERVAL;
                return AggregationBuilders.histogram(name)
                        .field(field)
                        .interval(histInterval);

            case RANGE:
                return buildRangeAggregation(definition);

            default:
                throw new IllegalArgumentException(String.format(ErrorMessages.UNSUPPORTED_AGG_TYPE, aggType));
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
                    rangeAgg.addRange(
                            range.getKey(),
                            ((Number) range.getFrom()).doubleValue(),
                            ((Number) range.getTo()).doubleValue()
                    );
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
            case Constants.DATE_INTERVAL_SECOND:
            case Constants.DATE_INTERVAL_S:
                return DateHistogramInterval.SECOND;
            case Constants.DATE_INTERVAL_MINUTE:
            case Constants.DATE_INTERVAL_M:
                return DateHistogramInterval.MINUTE;
            case Constants.DATE_INTERVAL_HOUR:
            case Constants.DATE_INTERVAL_H:
                return DateHistogramInterval.HOUR;
            case Constants.DATE_INTERVAL_DAY:
            case Constants.DATE_INTERVAL_D:
                return DateHistogramInterval.DAY;
            case Constants.DATE_INTERVAL_WEEK:
            case Constants.DATE_INTERVAL_W:
                return DateHistogramInterval.WEEK;
            case Constants.DATE_INTERVAL_MONTH:
            case Constants.DATE_INTERVAL_MONTH_ABBR:
                return DateHistogramInterval.MONTH;
            case Constants.DATE_INTERVAL_QUARTER:
            case Constants.DATE_INTERVAL_Q:
                return DateHistogramInterval.QUARTER;
            case Constants.DATE_INTERVAL_YEAR:
            case Constants.DATE_INTERVAL_Y:
                return DateHistogramInterval.YEAR;
            default:
                // 自定义间隔
                return new DateHistogramInterval(interval);
        }
    }
}
