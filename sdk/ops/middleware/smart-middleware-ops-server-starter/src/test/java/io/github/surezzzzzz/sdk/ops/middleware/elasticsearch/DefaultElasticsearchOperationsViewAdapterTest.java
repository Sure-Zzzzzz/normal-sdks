package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch;

import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.ops.middleware.constant.SmartMiddlewareOpsServerConstant;
import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Elasticsearch 索引目录安全投影测试。
 *
 * @author surezzzzzz
 */
class DefaultElasticsearchOperationsViewAdapterTest {

    private final DefaultElasticsearchOperationsViewAdapter adapter =
            new DefaultElasticsearchOperationsViewAdapter(mock(SimpleElasticsearchRouteRegistry.class), 1000L, 4096);

    @Test
    void shouldReturnOpenNonHiddenIndicesInStableOrder() {
        ElasticsearchIndexListResponse response = adapter.parseIndexList("search-primary", content("{\n"
                + "  \"z-order\": {\"aliases\": {}},\n"
                + "  \".tasks\": {\"aliases\": {}},\n"
                + "  \"a-order\": {\"aliases\": {}},\n"
                + "  \"m-order\": {\"aliases\": {}}\n"
                + "}"));

        assertEquals("search-primary", response.getDatasourceKey());
        assertEquals(asList("a-order", "m-order", "z-order"), response.getItems());
        assertFalse(response.isTruncated());
    }

    @Test
    void shouldLimitIndexDirectoryToFirstHundredNames() {
        StringBuilder body = new StringBuilder("{");
        for (int index = 0; index <= SmartMiddlewareOpsServerConstant.MAX_ELASTICSEARCH_INDEX_LIST_SIZE; index++) {
            if (index > 0) {
                body.append(',');
            }
            body.append('\"').append(String.format("index-%03d", index)).append("\":{}");
        }
        body.append('}');

        ElasticsearchIndexListResponse response = adapter.parseIndexList("search-primary", content(body.toString()));

        assertTrue(response.isTruncated());
        assertEquals(SmartMiddlewareOpsServerConstant.MAX_ELASTICSEARCH_INDEX_LIST_SIZE, response.getItems().size());
        assertEquals("index-000", response.getItems().get(0));
        assertEquals("index-099", response.getItems().get(99));
    }

    @Test
    void shouldMapMalformedDirectoryResponseToSafeError() {
        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> adapter.parseIndexList("search-primary", content("[]")));

        assertEquals(503, exception.getStatus().value());
        assertEquals("Elasticsearch 运维查询暂不可用", exception.getMessage());
    }

    private ByteArrayInputStream content(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private List<String> asList(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            result.add(value);
        }
        return result;
    }
}
