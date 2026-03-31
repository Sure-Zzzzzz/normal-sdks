package io.github.surezzzzzz.sdk.elasticsearch.search.core.interceptor;

import io.github.surezzzzzz.sdk.elasticsearch.search.core.model.QueryExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;

/**
 * ES 查询执行拦截器
 *
 * <p>在查询执行后触发，用于审计、监控等扩展功能。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public interface QueryExecutionInterceptor {
    /**
     * 查询执行后回调
     *
     * @param request  查询请求
     * @param response 查询响应
     * @param context  执行上下文（包含实际索引、数据源等信息）
     */
    void afterQueryExecuted(QueryRequest request, QueryResponse response, QueryExecutionContext context);
}
