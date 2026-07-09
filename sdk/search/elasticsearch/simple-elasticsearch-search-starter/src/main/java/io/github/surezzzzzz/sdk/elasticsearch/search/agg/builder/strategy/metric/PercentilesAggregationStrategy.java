package io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.metric;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.AggregationStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.AggregationException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * percentiles 聚合策略
 * 计算数值字段的百分位数，如 P50/P95/P99 等。
 *
 * <p>PercentilesAggregationBuilder 在 ES 6.x 位于 metrics.percentiles 子包，7.x 提到 metrics 顶层，
 * 故 {@code percentiles(double[])} 调用走反射，避免编译期硬依赖。</p>
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class PercentilesAggregationStrategy implements AggregationStrategy {

    @Override
    public AggregationBuilder build(AggDefinition definition, IndexMetadata metadata,
                                    Map<String, Object> after) {
        AggregationBuilder builder =
                AggregationBuilders.percentiles(definition.getName()).field(definition.getField());
        if (!CollectionUtils.isEmpty(definition.getPercents())) {
            double[] percentsArray = definition.getPercents().stream().mapToDouble(Double::doubleValue).toArray();
            try {
                Method percentiles = builder.getClass().getMethod("percentiles", double[].class);
                percentiles.invoke(builder, percentsArray);
            } catch (Exception e) {
                throw new AggregationException(ErrorCode.AGG_REFLECT_INVOKE_FAILED,
                        String.format(ErrorMessage.AGG_REFLECT_INVOKE_FAILED, "percentiles"), e);
            }
        }
        return builder;
    }
}
