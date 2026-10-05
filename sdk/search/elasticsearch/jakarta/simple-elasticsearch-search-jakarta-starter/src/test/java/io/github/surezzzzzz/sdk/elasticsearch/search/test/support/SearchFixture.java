package io.github.surezzzzzz.sdk.elasticsearch.search.test.support;

import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchPayloadCodec;
import lombok.RequiredArgsConstructor;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 搜索数据准备使用 Route，独立于被测查询门面。
 */
@Component
@RequiredArgsConstructor
public class SearchFixture {
    private final SimpleElasticsearchRouteRegistry routes;
    private final SearchPayloadCodec codec;

    /**
     * 执行数据准备或直接业务结果核验，不关闭托管连接。
     */
    public Map<String, Object> request(String source, String method, String path, String body) throws Exception {
        Request request = new Request(method, path);
        if (body != null) request.setJsonEntity(body);
        return codec.decode(EntityUtils.toString(routes.getLowLevelClient(source).performRequest(request).getEntity(), StandardCharsets.UTF_8));
    }

    /**
     * 仅删除本模块本次创建的精确索引。
     */
    public void delete(String source, String index) throws Exception {
        // 日期 fixture 仍须是精确 UUID 所有权，不允许把通配表达式交给 DELETE。
        if (!index.matches("test-js[78]-[a-z0-9-]+")
                && !index.matches("test-js[78]-date-[a-f0-9-]{36}--[0-9]{4}\\.[0-9]{2}\\.[0-9]{2}"))
            throw new IllegalArgumentException("非测试索引");
        request(source, "DELETE", "/" + index, null);
    }
}
