package io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.AggregationStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.AggregationStrategyRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.bucket.CompositeAggregationStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.PipelineAggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.PipelineAggType;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.AggregationException;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.FieldException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 聚合构建器
 * 负责将 AggDefinition 转换为 ES AggregationBuilder，聚合类型构建委托给 {@link AggregationStrategyRegistry}
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class AggregationDslBuilder {

    @Autowired
    private MappingManager mappingManager;

    @Autowired
    private AggregationStrategyRegistry aggStrategyRegistry;

    @Autowired
    private CompositeAggregationStrategy compositeAggregationStrategy;

    /**
     * 构建聚合
     *
     * @param indexAlias     索引别名
     * @param aggDefinitions 聚合定义列表
     * @param after          composite 聚合翻页游标，key 为聚合名称，null 时不注入游标
     * @return ES AggregationBuilder 数组
     */
    public AggregationBuilder[] build(String indexAlias, List<AggDefinition> aggDefinitions,
                                      Map<String, Map<String, Object>> after) {
        return build(mappingManager.getMetadata(indexAlias), aggDefinitions, after);
    }

    public AggregationBuilder[] build(IndexMetadata metadata, List<AggDefinition> aggDefinitions,
                                      Map<String, Map<String, Object>> after) {
        if (aggDefinitions == null || aggDefinitions.isEmpty()) {
            return new AggregationBuilder[0];
        }

        AggregationBuilder[] builders = new AggregationBuilder[aggDefinitions.size()];
        for (int i = 0; i < aggDefinitions.size(); i++) {
            AggDefinition def = aggDefinitions.get(i);
            Map<String, Object> afterForThis = after != null ? after.get(def.getName()) : null;
            builders[i] = buildOne(def, metadata, afterForThis);
        }
        return builders;
    }

    /**
     * 构建单个聚合
     * composite 聚合（AggDefinition.composite = true）走独立分支，其余委托给 AggregationStrategyRegistry
     */
    private AggregationBuilder buildOne(AggDefinition definition, IndexMetadata metadata,
                                        Map<String, Object> after) {
        // 校验字段（field 为 null 时跳过，如 filter 聚合）
        String field = definition.getField();
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

        // composite 聚合走独立分支（composite 不是 AggType 枚举值，通过标志位区分）
        // 提前校验：composite 下不允许挂 pipelineAggs
        if (Boolean.TRUE.equals(definition.getComposite())) {
            if (!CollectionUtils.isEmpty(definition.getPipelineAggs())) {
                throw new AggregationException(ErrorCode.PIPELINE_INVALID_PARENT,
                        ErrorMessage.PIPELINE_INVALID_PARENT);
            }
            return compositeAggregationStrategy.build(definition, metadata, after);
        }

        // 普通聚合委托给 registry
        AggregationStrategy strategy = aggStrategyRegistry.resolve(definition.getTypeEnum());
        AggregationBuilder builder = strategy.build(definition, metadata, after);

        // bucket 聚合追加 sub-agg 和 pipeline agg
        if (definition.getTypeEnum().isBucket()) {
            if (!CollectionUtils.isEmpty(definition.getAggs())) {
                for (AggDefinition subAgg : definition.getAggs()) {
                    builder.subAggregation(buildOne(subAgg, metadata, null));
                }
            }
            appendPipelineAggs(builder, definition);
        }

        return builder;
    }

    private void appendPipelineAggs(AggregationBuilder parent, AggDefinition def) {
        if (CollectionUtils.isEmpty(def.getPipelineAggs())) {
            return;
        }
        for (PipelineAggDefinition pipelineDef : def.getPipelineAggs()) {
            PipelineAggType pipelineType = PipelineAggType.fromCode(pipelineDef.getType());
            if (pipelineType == null) {
                throw new AggregationException(ErrorCode.PIPELINE_UNSUPPORTED_TYPE,
                        String.format(ErrorMessage.PIPELINE_UNSUPPORTED_TYPE, pipelineDef.getType()));
            }
            if (pipelineType == PipelineAggType.BUCKET_SELECTOR
                    && !StringUtils.hasText(pipelineDef.getScript())) {
                throw new AggregationException(ErrorCode.PIPELINE_MISSING_SCRIPT,
                        ErrorMessage.PIPELINE_MISSING_SCRIPT);
            }
            parent.subAggregation(aggStrategyRegistry.resolvePipeline(pipelineType).build(pipelineDef));
        }
    }
}
