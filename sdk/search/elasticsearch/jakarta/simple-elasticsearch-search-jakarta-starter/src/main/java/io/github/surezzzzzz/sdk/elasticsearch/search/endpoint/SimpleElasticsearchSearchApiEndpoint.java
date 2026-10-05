package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.FieldDirectoryResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.IndexDirectoryResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.engine.SearchEngine;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.SearchIndexResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.SensitiveFieldProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.github.surezzzzzz.sdk.elasticsearch.search.constant.SearchProtocolConstant.*;

/**
 * 可选查询端点；索引授权与用户权限必须由宿主安全策略负责。
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = SimpleElasticsearchSearchConstant.CONFIG_PREFIX, name = {VALUE_ENABLE, VALUE_API_ENABLED}, havingValue = VALUE_TRUE)
@RequestMapping("${io.github.surezzzzzz.sdk.elasticsearch.search.api.base-path:/api}")
public class SimpleElasticsearchSearchApiEndpoint {
    private final SearchEngine engine;
    private final MappingManager mappings;
    private final SearchIndexResolver indices;
    private final SimpleElasticsearchSearchProperties properties;
    private final SensitiveFieldProcessor sensitive;

    /**
     * 执行结构化查询，返回受字段保护的分页结果。
     */
    @PostMapping("/query")
    public QueryResponse query(@RequestBody QueryRequest request) {
        return engine.query(request);
    }

    /**
     * 执行结构化聚合，返回统一指标或桶结果。
     */
    @PostMapping("/agg")
    public AggResponse aggregate(@RequestBody AggRequest request) {
        return engine.aggregate(request);
    }

    /**
     * 返回配置允许的索引目录，不枚举集群全部索引。
     */
    @GetMapping("/indices")
    public IndexDirectoryResponse indices() {
        List<IndexDirectoryResponse.IndexEntry> values = new ArrayList<>();
        for (SimpleElasticsearchSearchProperties.IndexConfig config : properties.getIndices()) {
            values.add(new IndexDirectoryResponse.IndexEntry(config.getName(), indices.identifier(config), config.isDateSplit()));
        }
        return new IndexDirectoryResponse(values);
    }

    /**
     * 返回可见字段元数据及配置标签，隐藏禁止字段。
     */
    @GetMapping("/indices/{alias}/fields")
    public FieldDirectoryResponse fields(@PathVariable String alias) {
        io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.ResolvedIndex target = indices.resolve(alias, null);
        List<FieldMetadata> visible = new ArrayList<>();
        for (FieldMetadata field : mappings.fields(target).values()) {
            try {
                sensitive.require(target.getConfig(), field.getName(), false);
                List<String> labels = target.getConfig().getFieldMapping().get(field.getName());
                visible.add(field.withLabels(labels == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(labels))));
            } catch (
                    io.github.surezzzzzz.sdk.elasticsearch.search.exception.SimpleElasticsearchSearchException ignored) {
            }
        }
        return new FieldDirectoryResponse(visible);
    }

    /**
     * 手动刷新指定索引的字段元数据，失败保留旧快照。
     */
    @PutMapping("/indices/{alias}/mapping-cache")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void refresh(@PathVariable String alias) {
        mappings.refresh(indices.resolve(alias, null));
    }

    /**
     * 逐项刷新配置目录，失败向调用方明确暴露。
     */
    @PutMapping("/indices/mapping-cache")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void refreshAll() {
        for (SimpleElasticsearchSearchProperties.IndexConfig config : properties.getIndices())
            mappings.refresh(indices.resolve(indices.identifier(config), null));
    }

    /**
     * 认证游标后在原数据源关闭查询快照。
     */
    @DeleteMapping("/query/pit/{cursorToken}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void closePit(@PathVariable String cursorToken) {
        engine.closePit(cursorToken);
    }

    /**
     * 认证游标后在原数据源释放遍历上下文。
     */
    @DeleteMapping("/query/scroll/{cursorToken}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearScroll(@PathVariable String cursorToken) {
        engine.clearScroll(cursorToken);
    }
}
