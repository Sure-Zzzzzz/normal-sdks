package io.github.surezzzzzz.sdk.elasticsearch.persistence.test.support;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistencePayloadCodec;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import lombok.RequiredArgsConstructor;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 数据准备和核验也借用 Route 客户端，清理只接受本模块的精确索引。
 */
@Component
@RequiredArgsConstructor
public class PersistenceFixture {
    private final SimpleElasticsearchRouteRegistry routes;
    private final PersistencePayloadCodec codec;

    /**
     * 执行测试准备请求，返回解析结果而不输出正文。
     */
    public Map<String, Object> request(String source, String method, String path, String body) throws Exception {
        Request request = new Request(method, path);
        if (body != null) request.setJsonEntity(body);
        return codec.decode(EntityUtils.toString(routes.getLowLevelClient(source).performRequest(request).getEntity(), StandardCharsets.UTF_8));
    }

    /**
     * 只删除调用方创建且带专用前缀的单个索引。
     */
    public void delete(String source, String index) throws Exception {
        if (!index.matches("test-jp[78]-[a-z0-9-]+")) throw new IllegalArgumentException("非测试索引");
        request(source, "DELETE", "/" + index, null);
    }
}
