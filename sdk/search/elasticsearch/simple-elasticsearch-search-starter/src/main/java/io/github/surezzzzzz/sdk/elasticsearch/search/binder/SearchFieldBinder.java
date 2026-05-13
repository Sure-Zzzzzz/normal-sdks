package io.github.surezzzzzz.sdk.elasticsearch.search.binder;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * 基于 field-mapping 配置的字段绑定器
 * <p>
 * field-mapping 结构：key = ES 字段名，value = 中文标签列表
 * 示例：
 * <pre>
 * fieldMapping:
 *   age: ["年龄", "年齡"]
 *   city: ["城市", "市"]
 * </pre>
 * bind("年龄", "users") → "age"
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class SearchFieldBinder implements FieldBinder {

    private final SimpleElasticsearchSearchProperties properties;

    public SearchFieldBinder(SimpleElasticsearchSearchProperties properties) {
        this.properties = properties;
    }

    @Override
    public String bind(String fieldHint, String dataSource) {
        if (fieldHint == null) {
            return null;
        }

        // 优先从 dataSource 对应的 index 配置查找
        SimpleElasticsearchSearchProperties.IndexConfig indexConfig =
                findIndexConfig(dataSource);
        if (indexConfig != null && !CollectionUtils.isEmpty(indexConfig.getFieldMapping())) {
            String mapped = findMapping(indexConfig.getFieldMapping(), fieldHint);
            if (mapped != null) {
                return mapped;
            }
        }

        // 跨索引查找
        for (SimpleElasticsearchSearchProperties.IndexConfig cfg : properties.getIndices()) {
            if (cfg.getFieldMapping() != null && !cfg.getFieldMapping().isEmpty()) {
                String mapped = findMapping(cfg.getFieldMapping(), fieldHint);
                if (mapped != null) {
                    return mapped;
                }
            }
        }

        // 找不到则原样返回
        return fieldHint;
    }

    private SimpleElasticsearchSearchProperties.IndexConfig findIndexConfig(String dataSource) {
        for (SimpleElasticsearchSearchProperties.IndexConfig cfg : properties.getIndices()) {
            if (dataSource.equals(cfg.getName()) || dataSource.equals(cfg.getAlias())) {
                return cfg;
            }
        }
        return null;
    }

    private String findMapping(Map<String, List<String>> fieldMapping, String fieldHint) {
        for (Map.Entry<String, List<String>> entry : fieldMapping.entrySet()) {
            List<String> labels = entry.getValue();
            if (labels != null && labels.contains(fieldHint)) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public List<String> getAvailableFields(String dataSource) {
        SimpleElasticsearchSearchProperties.IndexConfig indexConfig = findIndexConfig(dataSource);
        if (indexConfig != null && indexConfig.getFieldMapping() != null) {
            return new ArrayList<>(indexConfig.getFieldMapping().keySet());
        }
        return Collections.emptyList();
    }

    @Override
    public String getDataSourceType() {
        return "elasticsearch";
    }
}
