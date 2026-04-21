package io.github.surezzzzzz.sdk.elasticsearch.search.metadata.parser;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.FieldType;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SensitiveStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 字段元数据解析器
 * 负责将 ES mapping properties 解析为 FieldMetadata 列表
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class FieldMetadataParser {

    /**
     * 解析字段列表（递归处理嵌套字段和 multi-fields）
     *
     * @param propertiesMap ES mapping properties
     * @param prefix        字段前缀（嵌套字段用，顶层传空字符串）
     * @param indexConfig   索引配置（用于敏感字段判断）
     * @return 字段元数据列表
     */
    public List<FieldMetadata> parse(Map<String, Object> propertiesMap, String prefix,
                                     SimpleElasticsearchSearchProperties.IndexConfig indexConfig) {
        List<FieldMetadata> fields = new ArrayList<>();
        parseRecursive(propertiesMap, prefix, fields, indexConfig);
        return fields;
    }

    @SuppressWarnings("unchecked")
    private void parseRecursive(Map<String, Object> propertiesMap, String prefix,
                                List<FieldMetadata> fields,
                                SimpleElasticsearchSearchProperties.IndexConfig indexConfig) {
        for (Map.Entry<String, Object> entry : propertiesMap.entrySet()) {
            String fieldName = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Map<String, Object> fieldDef = (Map<String, Object>) entry.getValue();

            String typeStr = (String) fieldDef.get(SimpleElasticsearchSearchConstant.ES_MAPPING_TYPE);
            FieldType fieldType = FieldType.fromString(typeStr);

            SimpleElasticsearchSearchProperties.SensitiveFieldConfig sensitiveConfig =
                    findSensitiveFieldConfig(indexConfig, fieldName);

            boolean isSensitive = sensitiveConfig != null;
            boolean isForbidden = isSensitive &&
                    SensitiveStrategy.FORBIDDEN.getStrategy().equalsIgnoreCase(sensitiveConfig.getStrategy());
            boolean isMasked = isSensitive &&
                    SensitiveStrategy.MASK.getStrategy().equalsIgnoreCase(sensitiveConfig.getStrategy());

            // 解析 multi-fields
            Map<String, FieldMetadata> subFields = null;
            if (fieldDef.containsKey(SimpleElasticsearchSearchConstant.ES_MAPPING_FIELDS)) {
                Map<String, Object> fieldsMap = (Map<String, Object>) fieldDef.get(SimpleElasticsearchSearchConstant.ES_MAPPING_FIELDS);
                subFields = new HashMap<>();
                for (Map.Entry<String, Object> subFieldEntry : fieldsMap.entrySet()) {
                    String subFieldName = subFieldEntry.getKey();
                    Map<String, Object> subFieldDef = (Map<String, Object>) subFieldEntry.getValue();
                    String subFieldTypeStr = (String) subFieldDef.get(SimpleElasticsearchSearchConstant.ES_MAPPING_TYPE);
                    FieldType subFieldType = FieldType.fromString(subFieldTypeStr);
                    String fullSubFieldName = fieldName + "." + subFieldName;

                    subFields.put(subFieldName, FieldMetadata.builder()
                            .name(fullSubFieldName)
                            .type(subFieldType)
                            .array(false)
                            .searchable(!isForbidden)
                            .sortable(!isForbidden && subFieldType.isSortable())
                            .aggregatable(!isForbidden && subFieldType.isAggregatable())
                            .sensitive(isSensitive)
                            .masked(isMasked)
                            .reason(isForbidden ? SimpleElasticsearchSearchConstant.SENSITIVE_FIELD_REASON : null)
                            .build());
                }
            }

            fields.add(FieldMetadata.builder()
                    .name(fieldName)
                    .type(fieldType)
                    .array(false)
                    .searchable(!isForbidden)
                    .sortable(!isForbidden && fieldType.isSortable())
                    .aggregatable(!isForbidden && fieldType.isAggregatable())
                    .sensitive(isSensitive)
                    .masked(isMasked)
                    .format(fieldType == FieldType.DATE ? (String) fieldDef.get(SimpleElasticsearchSearchConstant.ES_MAPPING_FORMAT) : null)
                    .reason(isForbidden ? SimpleElasticsearchSearchConstant.SENSITIVE_FIELD_REASON : null)
                    .subFields(subFields)
                    .build());

            // 递归处理嵌套字段
            if (fieldDef.containsKey(SimpleElasticsearchSearchConstant.ES_MAPPING_PROPERTIES)) {
                Map<String, Object> nestedProperties = (Map<String, Object>) fieldDef.get(SimpleElasticsearchSearchConstant.ES_MAPPING_PROPERTIES);
                parseRecursive(nestedProperties, fieldName, fields, indexConfig);
            }
        }
    }

    private SimpleElasticsearchSearchProperties.SensitiveFieldConfig findSensitiveFieldConfig(
            SimpleElasticsearchSearchProperties.IndexConfig indexConfig, String fieldName) {
        if (indexConfig.getSensitiveFields() == null) {
            return null;
        }
        for (SimpleElasticsearchSearchProperties.SensitiveFieldConfig config : indexConfig.getSensitiveFields()) {
            if (config.getField().equals(fieldName)) {
                return config;
            }
        }
        return null;
    }
}
