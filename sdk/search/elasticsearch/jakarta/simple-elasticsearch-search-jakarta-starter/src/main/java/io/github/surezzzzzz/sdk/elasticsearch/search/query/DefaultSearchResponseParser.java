package io.github.surezzzzzz.sdk.elasticsearch.search.query;

import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.ResolvedIndex;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.SensitiveFieldProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.surezzzzzz.sdk.elasticsearch.search.constant.SearchProtocolConstant.*;
import static io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.*;

/**
 * 精确 total 和受保护的 source；不存在未标记的 total 下界。
 */
@RequiredArgsConstructor
public class DefaultSearchResponseParser implements SearchResponseParser {
    private final SensitiveFieldProcessor sensitive;
    private final SimpleElasticsearchSearchProperties properties;

    /**
     * 拒绝超时、分片失败和不完整的协议结果。
     */
    public void complete(Map<String, Object> response) {
        if (response.containsKey(VALUE_TIMED_OUT) && !(response.get(VALUE_TIMED_OUT) instanceof Boolean) || Boolean.TRUE.equals(response.get(VALUE_TIMED_OUT)) || number(object(response.get(VALUE_ES_SHARDS)), VALUE_FAILED) > 0
                || response.containsKey(VALUE_ERROR)) throw protocol();
    }

    /**
     * 将已验证的 REST 响应转换为公开结果，不返回原响应旁路。
     */
    public QueryResponse parse(Map<String, Object> response, ResolvedIndex target, Integer page, int size) {
        complete(response);
        Map<String, Object> hits = object(response.get(VALUE_HITS));
        Map<String, Object> total = object(hits.get(VALUE_TOTAL));
        if (!VALUE_EQ.equals(total.get(VALUE_RELATION))) throw protocol();
        Object raw = hits.get(VALUE_HITS);
        if (!(raw instanceof List) || ((List<?>) raw).size() > size) throw protocol();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object value : (List<?>) raw) {
            Map<String, Object> hit = object(value);
            if (!(hit.get(VALUE_ES_ID) instanceof String) || !(hit.get(VALUE_ES_INDEX) instanceof String))
                throw protocol();
            Map<String, Object> source = hit.get(VALUE_ES_SOURCE) == null ? new LinkedHashMap<>() : new LinkedHashMap<>(object(hit.get(VALUE_ES_SOURCE)));
            source.put(VALUE_ES_ID, hit.get(VALUE_ES_ID));
            source.put(VALUE_ES_INDEX, hit.get(VALUE_ES_INDEX));
            if (properties.getApi().isIncludeScore()) source.put(VALUE_ES_SCORE, hit.get(VALUE_ES_SCORE));
            items.add(sensitive.protect(target.getConfig(), source));
        }
        return QueryResponse.builder().total(number(total, VALUE_VALUE)).page(page).size(size).items(items).took(number(response, VALUE_TOOK)).build();
    }
}
