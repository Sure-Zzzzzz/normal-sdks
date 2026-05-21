package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 自然语言聚合请求
 *
 * @author surezzzzzz
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NLAggRequest {

    /**
     * 自然语言查询文本
     */
    private String nl;

    /**
     * 数据源标识（优先级高于 NL 中的索引提示）
     */
    private String dataSource;

    /**
     * 日期范围（可选）
     * 用于日期分割索引路由，若传入则覆盖 NL 解析结果
     */
    private QueryRequest.DateRange dateRange;

    /**
     * composite 聚合翻页游标（可选）
     * key：聚合名称，value：上一页响应中返回的 afterKey
     */
    private Map<String, Map<String, Object>> after;
}
