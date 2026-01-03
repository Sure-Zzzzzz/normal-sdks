package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.executor.AggExecutor;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ApiMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.IndexFieldsResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.IndexInfoResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.NLDslTranslationException;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.SimpleElasticsearchSearchException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.nl.service.NLDslService;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.executor.QueryExecutor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import io.github.surezzzzzz.sdk.log.truncate.support.LogTruncator;
import io.github.surezzzzzz.sdk.naturallanguage.parser.exception.NLParseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Elasticsearch Search API Endpoint
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
@RestController
@RequestMapping("${io.github.surezzzzzz.sdk.elasticsearch.search.api.base-path:/api}")
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.elasticsearch.search.api", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimpleElasticsearchSearchApiEndpoint {

    @Autowired
    private QueryExecutor queryExecutor;

    @Autowired
    private AggExecutor aggExecutor;

    @Autowired
    private MappingManager mappingManager;

    @Autowired
    private SimpleElasticsearchSearchProperties properties;

    @Autowired(required = false)
    private NLDslService nlDslService;

    @Autowired(required = false)
    @Qualifier("logTruncator")
    private LogTruncator logTruncator;

    /**
     * 查询数据
     */
    @PostMapping("/query")
    public ResponseEntity<ApiResponse<QueryResponse>> query(@RequestBody QueryRequest request) {
        try {
            log.debug("Received query request: index={}", request.getIndex());
            QueryResponse response = queryExecutor.execute(request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (SimpleElasticsearchSearchException e) {
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
        } catch (SimpleElasticsearchSearchException e) {
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
            List<SimpleElasticsearchSearchProperties.IndexConfig> indices = mappingManager.getAllIndices();

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
        } catch (SimpleElasticsearchSearchException e) {
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
            return ResponseEntity.ok(ApiResponse.success(ApiMessage.ALL_INDEX_MAPPINGS_REFRESHED));
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
            return ResponseEntity.ok(ApiResponse.success(ApiMessage.INDEX_MAPPING_REFRESHED));
        } catch (SimpleElasticsearchSearchException e) {
            log.warn("Refresh mapping validation failed: alias={}, error={}", alias, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Refresh mapping failed: alias={}", alias, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 自然语言转DSL
     *
     * @param text  自然语言查询文本（必填）
     * @param index 索引别名（可选，优先级高于NL中的提示）
     * @return DSL查询对象（QueryRequest或AggRequest）
     */
    @GetMapping(value = "/nl/dsl", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> translateNLToDsl(
            @RequestParam("text") String text,
            @RequestParam(value = "index", required = false) String index
    ) {
        // 参数验证
        if (!StringUtils.hasText(text)) {
            log.warn("NL翻译请求参数无效: text为空");
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("查询文本不能为空"));
        }

        // 使用LogTruncator记录日志
        if (logTruncator != null) {
            log.info("收到NL转DSL请求 - text: '{}', index: '{}'",
                    logTruncator.truncateRaw(text), index);
        } else {
            log.info("收到NL转DSL请求 - text: '{}', index: '{}'", text, index);
        }

        try {
            // 执行翻译
            Object dsl = nlDslService.translateToRequest(text, index);

            // 记录生成的DSL
            if (log.isDebugEnabled() && logTruncator != null) {
                log.debug("生成DSL: {}", logTruncator.truncate(dsl));
            }

            if (logTruncator != null) {
                log.info("NL转DSL成功 - text: '{}'", logTruncator.truncateRaw(text));
            } else {
                log.info("NL转DSL成功 - text: '{}'", text);
            }

            // 直接返回DSL对象，不包装
            return ResponseEntity.ok(dsl);

        } catch (NLParseException e) {
            if (logTruncator != null) {
                log.warn("NL解析失败 - text: '{}', error: {}",
                        logTruncator.truncateRaw(text),
                        logTruncator.truncateRaw(e.getMessage()));
            } else {
                log.warn("NL解析失败 - text: '{}', error: {}", text, e.getMessage());
            }
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));

        } catch (NLDslTranslationException e) {
            if (logTruncator != null) {
                log.warn("DSL翻译失败 - text: '{}', error: {}",
                        logTruncator.truncateRaw(text),
                        logTruncator.truncateRaw(e.getMessage()));
            } else {
                log.warn("DSL翻译失败 - text: '{}', error: {}", text, e.getMessage());
            }
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            if (logTruncator != null) {
                log.error("翻译过程发生未预期错误 - text: '{}', exception: {}",
                        logTruncator.truncateRaw(text),
                        logTruncator.truncate(e));
            } else {
                log.error("翻译过程发生未预期错误 - text: '{}'", text, e);
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("翻译失败: " + e.getMessage()));
        }
    }
}
