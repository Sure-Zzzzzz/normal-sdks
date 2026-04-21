package io.github.surezzzzzz.sdk.elasticsearch.search.processor.sensitive;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;

import java.util.Map;

/**
 * 脱敏策略：保留前 N 位和后 M 位，中间替换为 maskPattern
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchSearchComponent
public class MaskSensitiveFieldStrategy implements SensitiveFieldStrategy {

    @Override
    public void process(Map<String, Object> document, String fieldName,
                        SimpleElasticsearchSearchProperties.SensitiveFieldConfig config) {
        Object value = document.get(fieldName);
        if (value == null) {
            return;
        }
        document.put(fieldName, maskValue(value.toString(), config));
    }

    private String maskValue(String value, SimpleElasticsearchSearchProperties.SensitiveFieldConfig config) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        Integer maskStart = config.getMaskStart();
        Integer maskEnd = config.getMaskEnd();
        String maskPattern = config.getMaskPattern() != null
                ? config.getMaskPattern()
                : SimpleElasticsearchSearchConstant.DEFAULT_MASK_PATTERN;

        int length = value.length();

        if (maskStart == null && maskEnd == null) {
            return maskPattern;
        }

        int start = maskStart != null ? maskStart : SimpleElasticsearchSearchConstant.DEFAULT_MASK_START;
        int end = maskEnd != null ? maskEnd : SimpleElasticsearchSearchConstant.DEFAULT_MASK_END;

        if (start + end >= length) {
            return maskPattern;
        }

        return value.substring(0, start) + maskPattern + value.substring(length - end);
    }
}
