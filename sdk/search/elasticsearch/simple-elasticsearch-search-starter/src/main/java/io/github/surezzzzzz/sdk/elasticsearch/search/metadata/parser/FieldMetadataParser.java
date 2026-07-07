package io.github.surezzzzzz.sdk.elasticsearch.search.metadata.parser;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.FieldType;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SensitiveStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

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

    /**
     * 解析并合并多个索引的字段元数据（用于通配符索引场景）。
     *
     * <p>调用方按索引名升序传入（{@link LinkedHashMap} 保证遍历顺序），
     * 使最新索引的字段定义在冲突时胜出。</p>
     *
     * <p>合并规则：</p>
     * <ul>
     *   <li>字段取并集，按字段名去重</li>
     *   <li>同名字段：取最新索引的 type / format / searchable / sortable / aggregatable / sensitive / masked / reason</li>
     *   <li>子字段（multi-fields）：取所有索引的并集，冲突时最新索引胜出</li>
     *   <li>类型冲突：打 warn 日志，采用最新索引定义</li>
     * </ul>
     *
     * @param indexProperties 索引名 -> properties 映射（升序，最新索引最后）
     * @param indexConfig     索引配置（用于敏感字段判断）
     * @return 合并后的字段元数据列表
     */
    public List<FieldMetadata> parseAndMerge(
            LinkedHashMap<String, Map<String, Object>> indexProperties,
            SimpleElasticsearchSearchProperties.IndexConfig indexConfig) {
        Map<String, FieldMetadata> mergedByName = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : indexProperties.entrySet()) {
            String indexName = entry.getKey();
            List<FieldMetadata> parsed = parse(entry.getValue(), "", indexConfig);
            for (FieldMetadata field : parsed) {
                FieldMetadata existing = mergedByName.get(field.getName());
                if (existing == null) {
                    mergedByName.put(field.getName(), field);
                } else {
                    mergedByName.put(field.getName(),
                            mergeFieldMetadata(existing, field, indexName));
                }
            }
        }
        return new ArrayList<>(mergedByName.values());
    }

    /**
     * 合并同名字段元数据：最新索引的标量属性胜出，子字段取并集（最新胜出）。
     *
     * @param older          较旧索引的字段定义
     * @param newer          较新索引的字段定义
     * @param newerIndexName 较新索引名（用于冲突日志）
     * @return 合并后的字段定义
     */
    private FieldMetadata mergeFieldMetadata(FieldMetadata older, FieldMetadata newer, String newerIndexName) {
        if (older.getType() != newer.getType()) {
            log.warn("字段 [{}] 类型在索引间不一致: {} -> {}，采用最新索引 [{}] 的定义",
                    newer.getName(), older.getType(), newer.getType(), newerIndexName);
        }
        Map<String, FieldMetadata> mergedSubFields = new LinkedHashMap<>();
        if (older.getSubFields() != null) {
            mergedSubFields.putAll(older.getSubFields());
        }
        if (newer.getSubFields() != null) {
            mergedSubFields.putAll(newer.getSubFields());
        }
        return FieldMetadata.builder()
                .name(newer.getName())
                .type(newer.getType())
                .array(newer.isArray())
                .searchable(newer.isSearchable())
                .sortable(newer.isSortable())
                .aggregatable(newer.isAggregatable())
                .sensitive(newer.isSensitive())
                .masked(newer.isMasked())
                .format(newer.getFormat())
                .reason(newer.getReason())
                .subFields(mergedSubFields.isEmpty() ? null : mergedSubFields)
                .build();
    }
}
