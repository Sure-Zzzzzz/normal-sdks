package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自然语言聚合请求
 *
 * @author surezzzzzz
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NLAggRequest {

    /**
     * 自然语言查询文本
     */
    private String nl;

    /**
     * 数据源标识（优先级高于 NL 中的索引提示）
     */
    private String dataSource;
}