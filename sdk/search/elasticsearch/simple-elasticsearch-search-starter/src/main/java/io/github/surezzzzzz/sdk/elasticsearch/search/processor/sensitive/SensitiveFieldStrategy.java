package io.github.surezzzzzz.sdk.elasticsearch.search.processor.sensitive;

import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;

import java.util.Map;

/**
 * 敏感字段处理策略接口
 * 注意：接口名使用 SensitiveFieldStrategy 避免与枚举 {@link io.github.surezzzzzz.sdk.elasticsearch.search.constant.SensitiveStrategy} 冲突
 *
 * @author surezzzzzz
 */
public interface SensitiveFieldStrategy {

    /**
     * 处理敏感字段（in-place 修改 document）
     *
     * @param document  文档
     * @param fieldName 字段名
     * @param config    脱敏配置
     */
    void process(Map<String, Object> document, String fieldName,
                 SimpleElasticsearchSearchProperties.SensitiveFieldConfig config);
}
