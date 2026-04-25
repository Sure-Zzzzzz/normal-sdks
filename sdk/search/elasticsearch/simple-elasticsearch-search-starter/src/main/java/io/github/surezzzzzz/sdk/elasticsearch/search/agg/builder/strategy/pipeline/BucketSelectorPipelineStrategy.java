package io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.pipeline;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.builder.strategy.PipelineAggregationStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.PipelineAggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.PipelineAggregationBuilder;
import org.elasticsearch.search.aggregations.PipelineAggregatorBuilders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * bucket_selector pipeline 聚合策略
 * 通过 Painless 脚本过滤不满足条件的 bucket，等价于 SQL HAVING。
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
        return PipelineAggregatorBuilders.bucketSelector(
                def.getName(), paths, new Script(def.getScript()));
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
}
