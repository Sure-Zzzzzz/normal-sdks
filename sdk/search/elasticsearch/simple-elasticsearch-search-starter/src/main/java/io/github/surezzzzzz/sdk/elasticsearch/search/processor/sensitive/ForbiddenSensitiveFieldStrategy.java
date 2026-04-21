package io.github.surezzzzzz.sdk.elasticsearch.search.processor.sensitive;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;

import java.util.Map;

/**
 * 禁止访问策略：直接移除字段
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class ForbiddenSensitiveFieldStrategy implements SensitiveFieldStrategy {

    @Override
    public void process(Map<String, Object> document, String fieldName,
                        SimpleElasticsearchSearchProperties.SensitiveFieldConfig config) {
        document.remove(fieldName);
    }
}
