package io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.FieldType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段元数据
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldMetadata {

    /**
     * 字段名（支持嵌套: user.address.city）
     */
    private String name;

    /**
     * 字段类型
     */
    private FieldType type;

    /**
     * 是否为数组
     */
    private boolean array;

    /**
     * 是否可查询
     */
    private boolean searchable;

    /**
     * 是否可排序
     */
    private boolean sortable;

    /**
     * 是否可聚合
     */
    private boolean aggregatable;

    /**
     * 是否为敏感字段
     */
    private boolean sensitive;

    /**
     * 是否脱敏（仅当 sensitive=true 时有效）
     */
    private boolean masked;

    /**
     * 日期格式（仅 DATE 类型）
     */
    private String format;

    /**
     * 不可访问原因（仅当 searchable=false 时）
     */
    private String reason;
}
