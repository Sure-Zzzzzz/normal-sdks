package io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.bucket.*;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.metric.*;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.pipeline.BucketSelectorPipelineStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.pipeline.BucketSortPipelineStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.AggType;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.PipelineAggType;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.AggregationException;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.ConfigurationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 聚合策略注册表
 * 内置 17 种聚合类型策略和 2 种 pipeline 策略，启动时自动注册。
 * composite 聚合通过 AggDefinition.composite 标志位走独立分支，不在此注册。
 * 用户可通过注入此 Bean 调用 {@link #register} 扩展自定义策略，但不允许覆盖内置 key。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
@RequiredArgsConstructor
public class AggregationStrategyRegistry {

    private final Map<String, AggregationStrategy> strategies = new LinkedHashMap<>();
    private final Map<String, PipelineAggregationStrategy> pipelineStrategies = new LinkedHashMap<>();

    private final SumAggregationStrategy sumStrategy;
    private final AvgAggregationStrategy avgStrategy;
    private final MinAggregationStrategy minStrategy;
    private final MaxAggregationStrategy maxStrategy;
    private final CountAggregationStrategy countStrategy;
    private final CardinalityAggregationStrategy cardinalityStrategy;
    private final StatsAggregationStrategy statsStrategy;
    private final ExtendedStatsAggregationStrategy extendedStatsStrategy;
    private final TermsAggregationStrategy termsStrategy;
    private final DateHistogramAggregationStrategy dateHistogramStrategy;
    private final HistogramAggregationStrategy histogramStrategy;
    private final RangeAggregationStrategy rangeStrategy;
    private final FilterAggregationStrategy filterStrategy;
    private final FiltersAggregationStrategy filtersStrategy;
    private final MissingAggregationStrategy missingStrategy;
    private final DateRangeAggregationStrategy dateRangeStrategy;
    private final IpRangeAggregationStrategy ipRangeStrategy;
    private final BucketSortPipelineStrategy bucketSortStrategy;
    private final BucketSelectorPipelineStrategy bucketSelectorStrategy;

    @PostConstruct
    public void init() {
        register(AggType.SUM.getType(), sumStrategy);
        register(AggType.AVG.getType(), avgStrategy);
        register(AggType.MIN.getType(), minStrategy);
        register(AggType.MAX.getType(), maxStrategy);
        register(AggType.COUNT.getType(), countStrategy);
        register(AggType.CARDINALITY.getType(), cardinalityStrategy);
        register(AggType.STATS.getType(), statsStrategy);
        register(AggType.EXTENDED_STATS.getType(), extendedStatsStrategy);
        register(AggType.TERMS.getType(), termsStrategy);
        register(AggType.DATE_HISTOGRAM.getType(), dateHistogramStrategy);
        register(AggType.HISTOGRAM.getType(), histogramStrategy);
        register(AggType.RANGE.getType(), rangeStrategy);
        register(AggType.FILTER.getType(), filterStrategy);
        register(AggType.FILTERS.getType(), filtersStrategy);
        register(AggType.MISSING.getType(), missingStrategy);
        register(AggType.DATE_RANGE.getType(), dateRangeStrategy);
        register(AggType.IP_RANGE.getType(), ipRangeStrategy);
        pipelineStrategies.put(PipelineAggType.BUCKET_SORT.getCode(), bucketSortStrategy);
        pipelineStrategies.put(PipelineAggType.BUCKET_SELECTOR.getCode(), bucketSelectorStrategy);
        log.info("AggregationStrategyRegistry initialized with {} strategies, {} pipeline strategies",
                strategies.size(), pipelineStrategies.size());
    }

    /**
     * 根据聚合类型解析对应策略
     *
     * @param type 聚合类型枚举
     * @return 匹配的策略
     * @throws AggregationException 找不到匹配策略时
     */
    public AggregationStrategy resolve(AggType type) {
        AggregationStrategy strategy = strategies.get(type.getType());
        if (strategy == null) {
            throw new AggregationException(ErrorCode.UNSUPPORTED_AGG_TYPE,
                    String.format(ErrorMessage.UNSUPPORTED_AGG_TYPE, type));
        }
        return strategy;
    }

    /**
     * 根据 pipeline 聚合类型解析对应策略
     *
     * @param type pipeline 聚合类型枚举
     * @return 匹配的策略
     * @throws AggregationException 找不到匹配策略时
     */
    public PipelineAggregationStrategy resolvePipeline(PipelineAggType type) {
        PipelineAggregationStrategy strategy = pipelineStrategies.get(type.getCode());
        if (strategy == null) {
            throw new AggregationException(ErrorCode.PIPELINE_UNSUPPORTED_TYPE,
                    String.format(ErrorMessage.PIPELINE_UNSUPPORTED_TYPE, type));
        }
        return strategy;
    }

    /**
     * 注册自定义聚合策略，key 已存在时抛异常，防止覆盖内置策略
     *
     * @param key      策略 key，与 {@link AggType#getType()} 保持一致
     * @param strategy 策略实现
     * @throws ConfigurationException key 已存在时
     */
    public void register(String key, AggregationStrategy strategy) {
        if (strategies.containsKey(key)) {
            throw new ConfigurationException(ErrorCode.AGG_STRATEGY_DUPLICATE,
                    String.format(ErrorMessage.AGG_STRATEGY_DUPLICATE, key));
        }
        strategies.put(key, strategy);
        log.debug("Registered aggregation strategy: key={}, impl={}", key, strategy.getClass().getSimpleName());
    }
}
