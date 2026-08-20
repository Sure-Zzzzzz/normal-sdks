package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.document.ElasticsearchDocumentQueryRequest;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.document.ElasticsearchDocumentQueryRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Elasticsearch 文档查询输入边界测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class ElasticsearchDocumentQueryRequestValidatorTest {

    private final ElasticsearchDocumentQueryRequestValidator validator = new ElasticsearchDocumentQueryRequestValidator(
            32, 512, 20, 100, new ObjectMapper());

    @Test
    void shouldAllowStatelessQueryAndNativeAggregation() {
        String dsl = "{\"query\":{\"term\":{\"state\":\"OPEN\"}},\"size\":20,\"aggs\":{\"by_owner\":{\"terms\":{\"field\":\"owner\"}}}}";
        assertDoesNotThrow(() -> validator.validate(request(dsl)));
    }

    @Test
    void shouldAllowBusinessIndexWildcardAndRejectHiddenOrPathIndex() {
        assertDoesNotThrow(() -> validator.validate(request("orders-*", "{}")));
        assertDoesNotThrow(() -> validator.validate(request("orders-2026.0?", "{}")));
        assertDoesNotThrow(() -> validator.validate(request("orders-*,events-*", "{}")));
        assertIndexRejected("");
        assertIndexRejected(".system");
        assertIndexRejected("_all");
        assertIndexRejected("orders,,events");
        assertIndexRejected("orders/_search");
        assertIndexRejected("Orders");
    }

    @Test
    void shouldRejectPersistentContextAndScriptComponentsAtAnyDepth() {
        log.info("Elasticsearch 文档查询拒绝持续上下文和脚本执行成分");
        assertDslRejected("{\"pit\":{\"id\":\"x\"}}");
        assertDslRejected("{\"scroll\":\"1m\"}");
        assertDslRejected("{\"search_after\":[1]}");
        assertDslRejected("{\"runtime_mappings\":{}}");
        assertDslRejected("{\"script_fields\":{}}");
        assertDslRejected("{\"profile\":true}");
        assertDslRejected("{\"aggs\":{\"unsafe\":{\"terms\":{\"script\":\"doc['owner']\"}}}}");
        assertDslRejected("{\"query\":{\"bool\":{\"filter\":[{\"script\":{\"script\":\"true\"}}]}}}");
    }

    @Test
    void shouldRejectInvalidDslAndPagingOutsideServerBound() {
        MiddlewareOpsException malformed = assertThrows(MiddlewareOpsException.class,
                () -> validator.validate(request("{")));
        assertEquals(400, malformed.getStatus().value());
        assertEquals("JSON DSL 格式无效", malformed.getMessage());

        assertSizeRejected("{\"size\":21}");
        assertSizeRejected("{\"size\":-1}");
        assertSizeRejected("{\"size\":1.5}");
        assertFromRejected("{\"from\":101,\"size\":1}");
        assertFromRejected("{\"from\":-1}");
        assertFromRejected("{\"from\":1.5}");
    }

    private void assertIndexRejected(String index) {
        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> validator.validate(request(index, "{}")), index);
        assertEquals(400, exception.getStatus().value());
        assertEquals("索引不符合查询规范", exception.getMessage());
    }

    private void assertDslRejected(String dsl) {
        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> validator.validate(request(dsl)), dsl);
        assertEquals(400, exception.getStatus().value());
        assertEquals("JSON DSL 不符合查询规范", exception.getMessage());
    }

    private void assertSizeRejected(String dsl) {
        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class, () -> validator.validate(request(dsl)));
        assertEquals(400, exception.getStatus().value());
        assertEquals("结果数量超出允许范围", exception.getMessage());
    }

    private void assertFromRejected(String dsl) {
        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class, () -> validator.validate(request(dsl)));
        assertEquals(400, exception.getStatus().value());
        assertEquals("起始位置超出允许范围", exception.getMessage());
    }

    private ElasticsearchDocumentQueryRequest request(String dsl) {
        return request("orders", dsl);
    }

    private ElasticsearchDocumentQueryRequest request(String index, String dsl) {
        return ElasticsearchDocumentQueryRequest.builder().datasourceKey("search-primary").index(index).dsl(dsl).build();
    }
}
