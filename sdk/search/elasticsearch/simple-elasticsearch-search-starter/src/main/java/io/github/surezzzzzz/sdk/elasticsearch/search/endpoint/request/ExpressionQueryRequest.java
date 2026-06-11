package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 条件表达式查询请求
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExpressionQueryRequest {

    /**
     * 索引别名（必填）
     */
    private String index;

    /**
     * 条件表达式字符串（必填）
     * 示例：威胁类型 = "木马" AND 攻击次数 >= 10
     */
    private String expression;

    /**
     * 分页信息（可选，不填使用默认值）
     */
    private PaginationInfo pagination;

    /**
     * 字段投影（可选）
     */
    private List<String> fields;

    /**
     * 日期分割索引的路由范围（可选）
     */
    private QueryRequest.DateRange dateRange;

    /**
     * 是否仅返回总数（不走 _search，走 _count API）
     * 默认 false
     */
    private Boolean countOnly;
}
