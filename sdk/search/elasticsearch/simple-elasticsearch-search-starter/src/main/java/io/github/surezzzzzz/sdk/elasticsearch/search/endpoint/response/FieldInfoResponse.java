package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段信息响应
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FieldInfoResponse {

    /**
     * 字段名
     */
    private String name;

    /**
     * 字段类型
     */
    private String type;

    /**
     * 是否可搜索
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
     * 是否数组
     */
    private boolean array;

    /**
     * 是否敏感字段
     */
    private Boolean sensitive;

    /**
     * 是否脱敏
     */
    private Boolean masked;

    /**
     * 日期格式
     */
    private String format;

    /**
     * 不可用原因
     */
    private String reason;

    /**
     * 从元数据创建
     */
    public static FieldInfoResponse from(FieldMetadata field) {
        FieldInfoResponseBuilder builder = FieldInfoResponse.builder()
                .name(field.getName())
                .type(field.getType().getType())
                .searchable(field.isSearchable())
                .sortable(field.isSortable())
                .aggregatable(field.isAggregatable())
                .array(field.isArray());

        if (field.isSensitive()) {
            builder.sensitive(true);
            builder.masked(field.isMasked());
        }

        if (field.getFormat() != null) {
            builder.format(field.getFormat());
        }

        if (field.getReason() != null) {
            builder.reason(field.getReason());
        }

        return builder.build();
    }
}
