package io.github.surezzzzzz.sdk.elasticsearch.search.support;

import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.SimpleElasticsearchSearchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static io.github.surezzzzzz.sdk.elasticsearch.search.constant.SearchProtocolConstant.HTTP_BAD_GATEWAY;
import static io.github.surezzzzzz.sdk.elasticsearch.search.constant.SearchProtocolConstant.HTTP_NOT_FOUND;

/**
 * 只借用 Route 客户端；不保留 HTTP 原始失败报文。
 */
@Slf4j
@RequiredArgsConstructor
public class SearchRestHelper {
    private final SimpleElasticsearchRouteRegistry registry;
    private final SearchPayloadCodec codec;

    /**
     * 使用 Route 托管客户端执行请求，不创建或关闭连接。
     */
    public Map<String, Object> perform(String source, String method, String endpoint, Map<String, String> params, Object body) {
        long start = System.nanoTime();
        try {
            Request request = new Request(method, endpoint);
            params.forEach(request::addParameter);
            if (body != null) request.setJsonEntity(codec.encode(body));
            Response response = registry.getLowLevelClient(source).performRequest(request);
            log.debug("查询 REST 完成 datasource={} status={} tookNanos={}", source, response.getStatusLine().getStatusCode(), System.nanoTime() - start);
            if (response.getEntity() == null) throw SearchValidationHelper.protocol();
            return codec.decode(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
        } catch (ResponseException error) {
            int status = error.getResponse().getStatusLine().getStatusCode();
            EntityUtils.consumeQuietly(error.getResponse().getEntity());
            log.debug("查询 REST 拒绝 datasource={} status={}", source, status);
            throw new SimpleElasticsearchSearchException(ErrorCode.REST_FAILED, ErrorMessage.REST_FAILED, status == HTTP_NOT_FOUND ? HTTP_NOT_FOUND : HTTP_BAD_GATEWAY);
        } catch (SimpleElasticsearchSearchException error) {
            throw error;
        } catch (Exception error) {
            log.debug("查询 REST 失败 datasource={} category={}", source, error.getClass().getSimpleName());
            throw new SimpleElasticsearchSearchException(ErrorCode.REST_FAILED, ErrorMessage.REST_FAILED, HTTP_BAD_GATEWAY);
        }
    }
}
