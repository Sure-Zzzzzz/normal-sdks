package io.github.surezzzzzz.sdk.elasticsearch.orm.endpoint;

import io.github.surezzzzzz.sdk.elasticsearch.orm.agg.executor.AggExecutor;
import io.github.surezzzzzz.sdk.elasticsearch.orm.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.orm.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.orm.annotation.SimpleElasticsearchOrmComponent;
import io.github.surezzzzzz.sdk.elasticsearch.orm.configuration.SimpleElasticsearchOrmProperties;
import io.github.surezzzzzz.sdk.elasticsearch.orm.constant.ApiMessages;
import io.github.surezzzzzz.sdk.elasticsearch.orm.endpoint.response.IndexFieldsResponse;
import io.github.surezzzzzz.sdk.elasticsearch.orm.endpoint.response.IndexInfoResponse;
import io.github.surezzzzzz.sdk.elasticsearch.orm.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.orm.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.orm.query.executor.QueryExecutor;
import io.github.surezzzzzz.sdk.elasticsearch.orm.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.orm.query.model.QueryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Elasticsearch ORM API Endpoint
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchOrmComponent
@RestController
@RequestMapping("${io.github.surezzzzzz.sdk.elasticsearch.orm.api.base-path:/api}")
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.elasticsearch.orm.api", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimpleElasticsearchOrmApiEndpoint {

    @Autowired
    private QueryExecutor queryExecutor;

    @Autowired
    private AggExecutor aggExecutor;

    @Autowired
    private MappingManager mappingManager;

    @Autowired
    private SimpleElasticsearchOrmProperties properties;

    /**
     * 查询数据
     */
    @PostMapping("/query")
    public ResponseEntity<ApiResponse<QueryResponse>> query(@RequestBody QueryRequest request) {
        try {
            log.debug("Received query request: index={}", request.getIndex());
            QueryResponse response = queryExecutor.execute(request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.warn("Query validation failed: index={}, error={}", request.getIndex(), e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Query failed: index={}", request.getIndex(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 聚合查询
     */
    @PostMapping("/agg")
    public ResponseEntity<ApiResponse<AggResponse>> aggregate(@RequestBody AggRequest request) {
        try {
            log.debug("Received aggregation request: index={}", request.getIndex());
            AggResponse response = aggExecutor.execute(request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.warn("Aggregation validation failed: index={}, error={}", request.getIndex(), e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Aggregation failed: index={}", request.getIndex(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 获取所有索引列表
     */
    @GetMapping("/indices")
    public ResponseEntity<ApiResponse<List<IndexInfoResponse>>> getIndices() {
        try {
            List<SimpleElasticsearchOrmProperties.IndexConfig> indices = mappingManager.getAllIndices();

            List<IndexInfoResponse> result = indices.stream()
                    .map(config -> IndexInfoResponse.from(config, mappingManager))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Get indices failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 获取索引字段信息
     *
     * @param alias     索引别名
     * @param indexName 具体索引名称（可选，用于通配符索引）
     */
    @GetMapping("/indices/{alias}/fields")
    public ResponseEntity<ApiResponse<IndexFieldsResponse>> getFields(
            @PathVariable String alias,
            @RequestParam(required = false) String indexName) {
        try {
            IndexMetadata metadata = mappingManager.getMetadata(alias, indexName);
            IndexFieldsResponse response = IndexFieldsResponse.from(metadata);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.warn("Get fields validation failed: alias={}, indexName={}, error={}", alias, indexName, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get fields failed: alias={}, indexName={}", alias, indexName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 刷新所有索引的 mapping
     */
    @PostMapping("/indices/refresh")
    public ResponseEntity<ApiResponse<String>> refreshAll() {
        try {
            mappingManager.refreshAllMetadata();
            return ResponseEntity.ok(ApiResponse.success(ApiMessages.ALL_INDEX_MAPPINGS_REFRESHED));
        } catch (Exception e) {
            log.error("Refresh all mappings failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 刷新指定索引的 mapping
     */
    @PostMapping("/indices/{alias}/refresh")
    public ResponseEntity<ApiResponse<String>> refresh(@PathVariable String alias) {
        try {
            mappingManager.refreshMetadata(alias);
            return ResponseEntity.ok(ApiResponse.success(ApiMessages.INDEX_MAPPING_REFRESHED));
        } catch (IllegalArgumentException e) {
            log.warn("Refresh mapping validation failed: alias={}, error={}", alias, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Refresh mapping failed: alias={}", alias, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}
