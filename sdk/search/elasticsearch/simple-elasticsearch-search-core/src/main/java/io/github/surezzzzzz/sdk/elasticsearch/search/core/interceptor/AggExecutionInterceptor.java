package io.github.surezzzzzz.sdk.elasticsearch.search.core.interceptor;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.model.AggExecutionContext;

/**
 * ES 聚合执行拦截器
 *
 * <p>在聚合执行后触发，用于审计、监控等扩展功能。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public interface AggExecutionInterceptor {
    /**
     * 聚合执行后回调
     *
     * @param request  聚合请求
     * @param response 聚合响应
     * @param context  执行上下文（包含实际索引、数据源等信息）
     */
    void afterAggExecuted(AggRequest request, AggResponse response, AggExecutionContext context);
}
