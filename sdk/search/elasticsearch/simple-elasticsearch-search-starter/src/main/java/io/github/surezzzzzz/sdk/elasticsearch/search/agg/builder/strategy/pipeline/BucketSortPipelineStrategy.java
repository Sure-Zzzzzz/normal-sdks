package io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.pipeline;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.PipelineAggregationStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.PipelineAggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import org.elasticsearch.search.aggregations.PipelineAggregationBuilder;
import org.elasticsearch.search.aggregations.PipelineAggregatorBuilders;
import org.elasticsearch.search.aggregations.pipeline.BucketSortPipelineAggregationBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * bucket_sort pipeline 聚合策略
 * 对父 bucket 聚合的结果按指定 metrics 排序，支持 Top N 截取。
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class BucketSortPipelineStrategy implements PipelineAggregationStrategy {

    @Override
    public PipelineAggregationBuilder build(PipelineAggDefinition def) {
        BucketSortPipelineAggregationBuilder builder =
                PipelineAggregatorBuilders.bucketSort(def.getName(), buildSortFields(def.getSort()));
        if (def.getSize() != null) {
            builder.size(def.getSize());
        }
        if (def.getFrom() != null) {
            builder.from(def.getFrom());
        }
        return builder;
    }

    private List<FieldSortBuilder> buildSortFields(Map<String, String> sort) {
        List<FieldSortBuilder> fields = new ArrayList<>();
        if (sort == null) {
            return fields;
        }
        for (Map.Entry<String, String> entry : sort.entrySet()) {
            SortOrder order = SimpleElasticsearchSearchConstant.SORT_ORDER_DESC.equalsIgnoreCase(entry.getValue())
                    ? SortOrder.DESC : SortOrder.ASC;
            fields.add(SortBuilders.fieldSort(entry.getKey()).order(order));
        }
        return fields;
    }
}
