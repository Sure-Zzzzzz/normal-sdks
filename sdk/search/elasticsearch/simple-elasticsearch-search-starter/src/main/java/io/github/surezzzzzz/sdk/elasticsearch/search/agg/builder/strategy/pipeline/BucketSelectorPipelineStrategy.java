package io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.pipeline;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.PipelineAggregationStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.PipelineAggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.AggregationException;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.PipelineAggregationBuilder;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * bucket_selector pipeline 聚合策略
 * 通过 Painless 脚本过滤不满足条件的 bucket，等价于 SQL HAVING。
 *
 * <p>PipelineAggregatorBuilders 在 6.x/7.x 包路径不同，故构建走反射，避免编译期硬依赖。</p>
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class BucketSelectorPipelineStrategy implements PipelineAggregationStrategy {

    private static final Pattern PARAMS_PATTERN = Pattern.compile("params\\.([a-zA-Z_][a-zA-Z0-9_]*)");

    @Override
    public PipelineAggregationBuilder build(PipelineAggDefinition def) {
        Map<String, String> paths = def.getBucketsPath() != null
                ? def.getBucketsPath()
                : inferBucketsPath(def.getScript());
        Class<?> buildersClass = loadPipelineBuildersClass();
        try {
            Method bucketSelector = buildersClass.getMethod(
                    SimpleElasticsearchSearchConstant.AGG_METHOD_BUCKET_SELECTOR,
                    String.class, Map.class, Script.class);
            Object builder = bucketSelector.invoke(null, def.getName(), paths, new Script(def.getScript()));
            return (PipelineAggregationBuilder) builder;
        } catch (Exception e) {
            throw new AggregationException(ErrorCode.AGG_REFLECT_INVOKE_FAILED,
                    String.format(ErrorMessage.AGG_REFLECT_INVOKE_FAILED,
                            SimpleElasticsearchSearchConstant.AGG_METHOD_BUCKET_SELECTOR), e);
        }
    }

    /**
     * 从 script 中提取 params.xxx 变量名，自动映射为同名聚合
     * 示例："params.total_sales > 100" -> {"total_sales": "total_sales"}
     */
    private Map<String, String> inferBucketsPath(String script) {
        Map<String, String> paths = new LinkedHashMap<>();
        Matcher m = PARAMS_PATTERN.matcher(script);
        while (m.find()) {
            String varName = m.group(1);
            paths.put(varName, varName);
        }
        return paths;
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
}
