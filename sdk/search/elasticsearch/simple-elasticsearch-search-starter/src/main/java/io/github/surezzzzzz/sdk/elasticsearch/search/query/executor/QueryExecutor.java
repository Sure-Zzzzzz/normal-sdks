package io.github.surezzzzzz.sdk.elasticsearch.search.query.executor;

import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;

/**
 * 查询执行器接口
 *
 * @author surezzzzzz
 */
public interface QueryExecutor {

    /**
     * 执行查询
     *
     * @param request 查询请求
     * @return 查询响应
     */
    QueryResponse execute(QueryRequest request);
}
