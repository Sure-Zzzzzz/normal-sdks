package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.ElasticsearchIndexListRequest;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.ElasticsearchIndexListRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Elasticsearch 索引目录请求校验测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class ElasticsearchIndexListRequestValidatorTest {

    private final ElasticsearchIndexListRequestValidator validator = new ElasticsearchIndexListRequestValidator();

    @Test
    void shouldAllowSpecifiedDatasourceOnly() {
        assertDoesNotThrow(() -> validator.validate(request("search-primary")));
    }

    @Test
    void shouldRejectMissingDatasource() {
        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> validator.validate(request(" ")));
        log.info("空数据源索引目录请求：status={}，message={}", exception.getStatus(), exception.getMessage());

        assertEquals(400, exception.getStatus().value());
        assertEquals("数据源标识不能为空", exception.getMessage());
    }

    private ElasticsearchIndexListRequest request(String datasourceKey) {
        return ElasticsearchIndexListRequest.builder().datasourceKey(datasourceKey).build();
    }
}
