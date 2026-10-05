package io.github.surezzzzzz.sdk.elasticsearch.persistence.support;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceRestException;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.PersistenceProtocolConstant.*;

/**
 * 只借用 Route 托管客户端；错误原因链不保留 ES 响应正文或连接信息。
 */
@Slf4j
@RequiredArgsConstructor
public class PersistenceRestHelper {
    private final SimpleElasticsearchRouteRegistry registry;
    private final PersistencePayloadCodec codec;

    /**
     * 严格读取响应对象；缺失字段不是空成功结果。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value) {
        if (!(value instanceof Map)) throw protocol();
        return (Map<String, Object>) value;
    }

    /**
     * 严格读取非负整数统计。
     */
    public static long number(Map<String, Object> value, String key) {
        Object number = value.get(key);
        if (!(number instanceof Number)) throw protocol();
        try {
            long integer = new BigDecimal(number.toString()).longValueExact();
            if (integer < 0) throw protocol();
            return integer;
        } catch (ArithmeticException | NumberFormatException error) {
            throw protocol();
        }
    }

    /**
     * 固定协议错误，不透传源响应。
     */
    public static PersistenceExecutionException protocol() {
        return new PersistenceExecutionException(ErrorCode.ES_RESPONSE_PARSE_FAILED,
                ErrorMessage.ES_RESPONSE_PARSE_FAILED);
    }

    /**
     * 执行已冻结的协议请求，不创建或关闭客户端。
     */
    public Map<String, Object> perform(String source, String method, String endpoint,
                                       Map<String, String> parameters, String body) {
        long start = System.nanoTime();
        try {
            Request request = new Request(method, endpoint);
            parameters.forEach(request::addParameter);
            if (body != null) {
                if (PATH_BULK.equals(endpoint))
                    request.setEntity(new StringEntity(body, ContentType.create(CONTENT_TYPE_NDJSON, StandardCharsets.UTF_8)));
                else request.setJsonEntity(body);
            }
            Response response = registry.getLowLevelClient(source).performRequest(request);
            log.debug("持久化 REST 完成 datasource={} status={} tookNanos={}", source,
                    response.getStatusLine().getStatusCode(), System.nanoTime() - start);
            if (response.getEntity() == null) throw protocol();
            return codec.decode(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
        } catch (ResponseException error) {
            int status = error.getResponse().getStatusLine().getStatusCode();
            // 消费错误响应以归还连接，绝不把正文保留在异常或日志里。
            boolean documentMissing = false;
            try {
                Map<String, Object> failure = codec.decode(EntityUtils.toString(error.getResponse().getEntity(), StandardCharsets.UTF_8));
                documentMissing = status == HTTP_NOT_FOUND && !failure.containsKey(VALUE_ERROR) && VALUE_NOT_FOUND.equals(failure.get(VALUE_RESULT))
                        && failure.get(VALUE_ES_ID) instanceof String && failure.get(VALUE_ES_INDEX) instanceof String;
            } catch (Exception ignored) {
                // 不能识别的 404 不得伪装成删除成功。
            } finally {
                EntityUtils.consumeQuietly(error.getResponse().getEntity());
            }
            log.debug("持久化 REST 拒绝 datasource={} status={}", source, status);
            throw new PersistenceRestException(status, documentMissing);
        } catch (PersistenceExecutionException error) {
            throw error;
        } catch (Exception error) {
            log.debug("持久化 REST 失败 datasource={} category={}", source, error.getClass().getSimpleName());
            throw new PersistenceExecutionException(ErrorCode.EXECUTION_FAILED, ErrorMessage.EXECUTION_FAILED);
        }
    }
}
