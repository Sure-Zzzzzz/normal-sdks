package io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 索引元数据
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexMetadata {

    /**
     * 索引别名
     */
    private String alias;

    /**
     * 索引名称（可能包含通配符）
     */
    private String indexName;

    /**
     * 实际匹配的索引列表（通配符展开后）
     */
    private List<String> actualIndices;

    /**
     * 字段元数据列表
     */
    private List<FieldMetadata> fields;
    /**
     * 日期格式（如: yyyy.MM.dd）
     */
    private String datePattern;

    /**
     * 字段映射（字段名 -> 字段元数据）
     * 用于快速查找
     */
    private Map<String, FieldMetadata> fieldMap;

    /**
     * 是否为日期分割索引
     */
    private boolean dateSplit;

    /**
     * 日期字段名
     */
    private String dateField;

    /**
     * 缓存时间戳
     */
    private long cachedAt;

    /**
     * 获取字段元数据
     */
    public FieldMetadata getField(String fieldName) {
        if (fieldMap == null) {
            buildFieldMap();
        }
        return fieldMap.get(fieldName);
    }

    /**
     * 构建字段映射
     */
    public void buildFieldMap() {
        if (fieldMap == null) {
            fieldMap = new ConcurrentHashMap<>();
        }
        if (fields != null) {
            for (FieldMetadata field : fields) {
                fieldMap.put(field.getName(), field);
            }
        }
    }

    /**
     * 初始化集合
     */
    public static class IndexMetadataBuilder {
        public IndexMetadataBuilder() {
            this.actualIndices = new ArrayList<>();
            this.fields = new ArrayList<>();
            this.fieldMap = new ConcurrentHashMap<>();
        }
    }
}
