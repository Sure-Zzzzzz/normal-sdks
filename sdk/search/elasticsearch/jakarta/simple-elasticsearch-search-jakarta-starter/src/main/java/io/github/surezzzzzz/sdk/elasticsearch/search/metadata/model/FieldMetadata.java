package io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model;

import lombok.Value;
import lombok.With;

import java.util.List;

/**
 * 合并后的字段元数据，冲突和 nested 字段不会被伪装成可用标量。
 */
@Value
@With
public class FieldMetadata {
    String name, type, keywordField;
    boolean indexed, docValues, conflict, nested;
    List<String> labels;
}
