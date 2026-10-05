package io.github.surezzzzzz.sdk.elasticsearch.search.query;

import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.ResolvedIndex;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;

import java.util.Map;

/**
 * 命中结果解析扩展口，必须拒绝超时和分片局部失败。
 */
public interface SearchResponseParser {
    /**
     * 将已验证的 REST 响应转换为公开结果，不返回原响应旁路。
     */
    QueryResponse parse(Map<String, Object> response, ResolvedIndex target, Integer page, int size);

    /**
     * 拒绝超时、分片失败和不完整的协议结果。
     */
    void complete(Map<String, Object> response);
}
