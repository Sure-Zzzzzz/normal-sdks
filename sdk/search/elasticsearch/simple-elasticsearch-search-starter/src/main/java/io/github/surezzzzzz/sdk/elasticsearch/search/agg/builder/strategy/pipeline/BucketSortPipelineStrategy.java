package io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.pipeline;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.PipelineAggregationStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.PipelineAggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.AggregationException;
import org.elasticsearch.search.aggregations.PipelineAggregationBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * bucket_sort pipeline 聚合策略
 * 对父 bucket 聚合的结果按指定 metrics 排序，支持 Top N 截取。
 *
 * <p>PipelineAggregatorBuilders / BucketSortPipelineAggregationBuilder 在 6.x/7.x 包路径不同，
 * 故构建走反射，避免编译期硬依赖。</p>
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class BucketSortPipelineStrategy implements PipelineAggregationStrategy {

    @Override
    public PipelineAggregationBuilder build(PipelineAggDefinition def) {
        Object builder = invokeStatic(
                loadPipelineBuildersClass(),
                SimpleElasticsearchSearchConstant.AGG_METHOD_BUCKET_SORT,
                new Class[]{String.class, List.class},
                def.getName(), buildSortFields(def.getSort()));
        if (def.getSize() != null) {
            invokeNoArg(builder, SimpleElasticsearchSearchConstant.AGG_METHOD_SIZE, new Class[]{Integer.class}, def.getSize());
        }
        if (def.getFrom() != null) {
            invokeNoArg(builder, SimpleElasticsearchSearchConstant.AGG_METHOD_FROM, new Class[]{int.class}, def.getFrom());
        }
        return (PipelineAggregationBuilder) builder;
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

    private static Class<?> loadPipelineBuildersClass() {
        try {
            return Class.forName(SimpleElasticsearchSearchConstant.AGG_CLASS_PIPELINE_BUILDERS_ES7);
        } catch (ClassNotFoundException e) {
            try {
                return Class.forName(SimpleElasticsearchSearchConstant.AGG_CLASS_PIPELINE_BUILDERS_ES6);
            } catch (ClassNotFoundException ex) {
                throw new AggregationException(ErrorCode.AGG_REFLECT_CLASS_NOT_FOUND,
                        String.format(ErrorMessage.AGG_REFLECT_CLASS_NOT_FOUND, "PipelineAggregatorBuilders"), ex);
            }
        }
    }

    private static Object invokeStatic(Class<?> clazz, String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            Method m = clazz.getMethod(methodName, paramTypes);
            return m.invoke(null, args);
        } catch (Exception e) {
            throw new AggregationException(ErrorCode.AGG_REFLECT_INVOKE_FAILED,
                    String.format(ErrorMessage.AGG_REFLECT_INVOKE_FAILED, methodName), e);
        }
    }

    private static void invokeNoArg(Object target, String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            Method m = target.getClass().getMethod(methodName, paramTypes);
            m.invoke(target, args);
        } catch (Exception e) {
            throw new AggregationException(ErrorCode.AGG_REFLECT_INVOKE_FAILED,
                    String.format(ErrorMessage.AGG_REFLECT_INVOKE_FAILED, methodName), e);
        }
    }
}
