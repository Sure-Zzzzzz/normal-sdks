package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.ElasticsearchFieldCapabilitiesRequest;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.ElasticsearchFieldCapabilitiesRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Elasticsearch 字段能力目录请求边界测试。
 *
 * @author surezzzzzz
 */
class ElasticsearchFieldCapabilitiesRequestValidatorTest {

    private final ElasticsearchFieldCapabilitiesRequestValidator validator =
            new ElasticsearchFieldCapabilitiesRequestValidator(32);

    @Test
    void shouldAllowExactNonHiddenIndex() {
        assertDoesNotThrow(() -> validator.validate(request("orders-2026.08")));
    }

    @Test
    void shouldRejectNonExactIndexPathAndWildcardSyntax() {
        assertRejected("");
        assertRejected(".system");
        assertRejected("_all");
        assertRejected("orders*");
        assertRejected("orders,events");
        assertRejected("orders/_search");
        assertRejected("orders%2f_search");
        assertRejected("Orders");
        assertRejected("orders space");
        assertRejected("orders\nnext");
        assertRejected("orders-2026.08-too-long-index-name");
    }

    private void assertRejected(String index) {
        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> validator.validate(request(index)));
        assertEquals(400, exception.getStatus().value());
        assertEquals("索引不符合字段补全规范", exception.getMessage());
    }

    private ElasticsearchFieldCapabilitiesRequest request(String index) {
        return ElasticsearchFieldCapabilitiesRequest.builder().datasourceKey("search-primary").index(index).build();
    }
}
