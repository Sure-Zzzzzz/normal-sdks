package io.github.surezzzzzz.sdk.elasticsearch.search.processor;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SensitiveStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

/**
 * 敏感字段处理器
 * 负责对返回结果进行脱敏处理
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class SensitiveFieldProcessor {

    @Autowired
    private MappingManager mappingManager;

    /**
     * 处理敏感字段
     *
     * @param indexAlias 索引别名
     * @param document   文档
     */
    public void process(String indexAlias, Map<String, Object> document) {
        // 获取索引配置
        SimpleElasticsearchSearchProperties.IndexConfig indexConfig = findIndexConfig(indexAlias);
        if (indexConfig == null || indexConfig.getSensitiveFields() == null) {
            return;
        }

        // 处理每个敏感字段
        for (SimpleElasticsearchSearchProperties.SensitiveFieldConfig config : indexConfig.getSensitiveFields()) {
            String fieldName = config.getField();
            String strategy = config.getStrategy();

            if (SensitiveStrategy.FORBIDDEN.getStrategy().equalsIgnoreCase(strategy)) {
                // 禁止访问：直接移除
                document.remove(fieldName);
            } else if (SensitiveStrategy.MASK.getStrategy().equalsIgnoreCase(strategy)) {
                // 脱敏：替换值
                Object value = document.get(fieldName);
                if (value != null) {
                    String masked = maskValue(value.toString(), config);
                    document.put(fieldName, masked);
                }
            }
        }
    }

    /**
     * 脱敏处理
     */
    private String maskValue(String value, SimpleElasticsearchSearchProperties.SensitiveFieldConfig config) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        Integer maskStart = config.getMaskStart();
        Integer maskEnd = config.getMaskEnd();
        String maskPattern = config.getMaskPattern();

        if (maskPattern == null) {
            maskPattern = SimpleElasticsearchSearchConstant.DEFAULT_MASK_PATTERN;
        }

        int length = value.length();

        // 如果没有配置 start 和 end，默认全部脱敏
        if (maskStart == null && maskEnd == null) {
            return maskPattern;
        }

        // 保留前 N 位和后 M 位
        int start = maskStart != null ? maskStart : SimpleElasticsearchSearchConstant.DEFAULT_MASK_START;
        int end = maskEnd != null ? maskEnd : SimpleElasticsearchSearchConstant.DEFAULT_MASK_END;

        if (start + end >= length) {
            // 保留位数超过总长度，全部脱敏
            return maskPattern;
        }

        String prefix = value.substring(0, start);
        String suffix = value.substring(length - end);

        return prefix + maskPattern + suffix;
    }

    /**
     * 查找索引配置（支持通过alias或name查找）
     */
    private SimpleElasticsearchSearchProperties.IndexConfig findIndexConfig(String identifier) {
        for (SimpleElasticsearchSearchProperties.IndexConfig config : mappingManager.getAllIndices()) {
            // 优先匹配alias（如果有）
            if (config.getAlias() != null && config.getAlias().equals(identifier)) {
                return config;
            }
            // 其次匹配name
            if (config.getName().equals(identifier)) {
                return config;
            }
        }
        return null;
    }
}
