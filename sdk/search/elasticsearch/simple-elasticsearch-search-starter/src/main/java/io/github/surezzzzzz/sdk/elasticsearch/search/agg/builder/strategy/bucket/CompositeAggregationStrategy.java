package io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.bucket;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.AggregationStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.metric.*;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.AggType;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.AggregationException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.DateIntervalHelper;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.*;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * composite 聚合策略（支持翻页的分组聚合）
 * 支持 terms、date_histogram、histogram 三种 source 类型
 * 内部直接注入 metrics 策略 Bean，处理 composite 内部的 sub-agg
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
@RequiredArgsConstructor
public class CompositeAggregationStrategy implements AggregationStrategy {

    private final SumAggregationStrategy sumStrategy;
    private final AvgAggregationStrategy avgStrategy;
    private final MinAggregationStrategy minStrategy;
    private final MaxAggregationStrategy maxStrategy;
    private final CountAggregationStrategy countStrategy;
    private final CardinalityAggregationStrategy cardinalityStrategy;
    private final StatsAggregationStrategy statsStrategy;
    private final ExtendedStatsAggregationStrategy extendedStatsStrategy;

    private Map<String, AggregationStrategy> metricsStrategyMap;

    @PostConstruct
    public void init() {
        metricsStrategyMap = new HashMap<>();
        metricsStrategyMap.put(AggType.SUM.getType(), sumStrategy);
        metricsStrategyMap.put(AggType.AVG.getType(), avgStrategy);
        metricsStrategyMap.put(AggType.MIN.getType(), minStrategy);
        metricsStrategyMap.put(AggType.MAX.getType(), maxStrategy);
        metricsStrategyMap.put(AggType.COUNT.getType(), countStrategy);
        metricsStrategyMap.put(AggType.CARDINALITY.getType(), cardinalityStrategy);
        metricsStrategyMap.put(AggType.STATS.getType(), statsStrategy);
        metricsStrategyMap.put(AggType.EXTENDED_STATS.getType(), extendedStatsStrategy);
    }

    @Override
    public AggregationBuilder build(AggDefinition definition, IndexMetadata metadata,
                                    Map<String, Object> after) {
        AggType aggType = definition.getTypeEnum();
        String field = definition.getField();
        SortOrder sortOrder = SortOrder.fromString(
                StringUtils.hasText(definition.getOrder())
                        ? definition.getOrder()
                        : SimpleElasticsearchSearchConstant.COMPOSITE_DEFAULT_ORDER);

        CompositeValuesSourceBuilder<?> source;
        switch (aggType) {
            case TERMS:
                source = new TermsValuesSourceBuilder(field).field(field).order(sortOrder);
                break;
            case DATE_HISTOGRAM:
                source = new DateHistogramValuesSourceBuilder(field)
                        .field(field)
                        .dateHistogramInterval(DateIntervalHelper.parse(definition.getInterval()))
                        .order(sortOrder);
                break;
            case HISTOGRAM:
                double interval = definition.getSize() != null
                        ? definition.getSize()
                        : SimpleElasticsearchSearchConstant.DEFAULT_HISTOGRAM_INTERVAL;
                source = new HistogramValuesSourceBuilder(field).field(field).interval(interval).order(sortOrder);
                break;
            default:
                throw new AggregationException(ErrorCode.COMPOSITE_UNSUPPORTED_TYPE,
                        String.format(ErrorMessage.COMPOSITE_UNSUPPORTED_TYPE, definition.getType()));
        }

        int size = definition.getSize() != null
                ? definition.getSize()
                : SimpleElasticsearchSearchConstant.COMPOSITE_DEFAULT_SIZE;
        CompositeAggregationBuilder composite =
                new CompositeAggregationBuilder(definition.getName(), Collections.singletonList(source))
                        .size(size);

        if (after != null && !after.isEmpty()) {
            composite.aggregateAfter(after);
        }

        // composite 内部只允许嵌套 metrics 聚合
        if (definition.getAggs() != null && !definition.getAggs().isEmpty()) {
            for (AggDefinition subAgg : definition.getAggs()) {
                if (Boolean.TRUE.equals(subAgg.getComposite())
                        || (subAgg.getTypeEnum() != null && subAgg.getTypeEnum().isBucket())) {
                    throw new AggregationException(ErrorCode.COMPOSITE_NESTED_NOT_ALLOWED,
                            ErrorMessage.COMPOSITE_NESTED_NOT_ALLOWED);
                }
                AggregationStrategy subStrategy = metricsStrategyMap.get(subAgg.getTypeEnum().getType());
                if (subStrategy == null) {
                    throw new AggregationException(ErrorCode.UNSUPPORTED_AGG_TYPE,
                            String.format(ErrorMessage.UNSUPPORTED_AGG_TYPE, subAgg.getType()));
                }
                composite.subAggregation(subStrategy.build(subAgg, metadata, null));
            }
        }

        return composite;
    }
}
