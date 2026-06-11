package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 自然语言查询请求
 *
 * @author surezzzzzz
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NLQueryRequest {

    /**
     * 自然语言查询文本
     * scroll 续页时（pagination.scrollId 非空）可为空
     */
    private String nl;

    /**
     * 数据源标识（优先级高于 NL 中的索引提示）
     * 必填，包括 scroll 续页
     */
    private String dataSource;

    /**
     * 分页信息（可选）
     * 若传入则覆盖 NL 解析出的分页，支持 scroll / search_after / pit 等所有翻页模式
     */
    private PaginationInfo pagination;

    /**
     * 日期范围（可选）
     * 用于日期分割索引路由，若传入则覆盖 NL 解析结果
     */
    private QueryRequest.DateRange dateRange;

    /**
     * 字段投影（可选）
     * 只返回指定字段
     */
    private List<String> fields;

    /**
     * 字段折叠去重（可选）
     */
    private QueryRequest.CollapseField collapse;

    /**
     * 是否仅返回总数（不走 _search，走 _count API）
     * 默认 false
     */
    private Boolean countOnly;
}
