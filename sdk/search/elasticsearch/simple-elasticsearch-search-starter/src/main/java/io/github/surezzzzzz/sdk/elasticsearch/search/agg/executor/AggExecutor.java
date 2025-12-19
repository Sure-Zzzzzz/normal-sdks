package io.github.surezzzzzz.sdk.elasticsearch.search.agg.executor;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;

/**
 * 聚合执行器接口
 *
 * @author surezzzzzz
 */
public interface AggExecutor {

    /**
     * 执行聚合查询
     *
     * @param request 聚合请求
     * @return 聚合响应
     */
    AggResponse execute(AggRequest request);
}
