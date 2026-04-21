package io.github.surezzzzzz.sdk.elasticsearch.search.processor;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.sensitive.SensitiveFieldStrategyRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

/**
 * 敏感字段处理器
 * 负责对返回结果进行脱敏处理，具体策略委托给 {@link SensitiveFieldStrategyRegistry}
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class SensitiveFieldProcessor {

    @Autowired
    private MappingManager mappingManager;

    @Autowired
    private SensitiveFieldStrategyRegistry sensitiveFieldStrategyRegistry;

    /**
     * 处理敏感字段
     *
     * @param indexAlias 索引别名
     * @param document   文档
     */
    public void process(String indexAlias, Map<String, Object> document) {
        SimpleElasticsearchSearchProperties.IndexConfig indexConfig = findIndexConfig(indexAlias);
        if (indexConfig == null || indexConfig.getSensitiveFields() == null) {
            return;
        }
        for (SimpleElasticsearchSearchProperties.SensitiveFieldConfig config : indexConfig.getSensitiveFields()) {
            sensitiveFieldStrategyRegistry.resolve(config.getStrategy())
                    .process(document, config.getField(), config);
        }
    }

    private SimpleElasticsearchSearchProperties.IndexConfig findIndexConfig(String identifier) {
        for (SimpleElasticsearchSearchProperties.IndexConfig config : mappingManager.getAllIndices()) {
            if (config.getAlias() != null && config.getAlias().equals(identifier)) {
                return config;
            }
            if (config.getName().equals(identifier)) {
                return config;
            }
        }
        return null;
    }
}
