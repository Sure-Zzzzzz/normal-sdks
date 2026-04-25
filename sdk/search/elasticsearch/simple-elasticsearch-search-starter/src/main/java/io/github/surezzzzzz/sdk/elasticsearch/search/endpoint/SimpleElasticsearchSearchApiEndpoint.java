package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.executor.AggExecutor;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ApiMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request.ExpressionAggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request.ExpressionQueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.ExpressionHintsResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.ExpressionValidationResult;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.IndexFieldsResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.IndexInfoResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.NLDslTranslationException;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.SimpleElasticsearchSearchException;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.service.ExpressionService;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.nl.service.NLDslService;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.executor.QueryExecutor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import io.github.surezzzzzz.sdk.log.truncate.support.LogTruncator;
import io.github.surezzzzzz.sdk.naturallanguage.parser.exception.NLParseException;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@RequestMapping("${io.github.surezzzzzz.sdk.elasticsearch.search.api.base-path:/api}")
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.elasticsearch.search.api", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimpleElasticsearchSearchApiEndpoint {

    private final QueryExecutor queryExecutor;
    private final AggExecutor aggExecutor;
    private final MappingManager mappingManager;
    private final SimpleElasticsearchSearchProperties properties;

    @Autowired(required = false)
    private NLDslService nlDslService;

    @Autowired(required = false)
    private ExpressionService expressionService;

    @Autowired(required = false)
    @Qualifier("logTruncator")
    private LogTruncator logTruncator;

    // ==================== 查询 ====================

    /**
     * 查询数据
     */
    @PostMapping("/query")
    public ResponseEntity<ApiResponse<QueryResponse>> query(@RequestBody QueryRequest request) {
        try {
            log.debug("Received query request: index={}", request.getIndex());
            return ResponseEntity.ok(ApiResponse.success(queryExecutor.execute(request)));
        } catch (SimpleElasticsearchSearchException e) {
            log.warn("Query validation failed: index={}, error={}", request.getIndex(), e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Query failed: index={}", request.getIndex(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 聚合查询
     */
    @PostMapping("/agg")
    public ResponseEntity<ApiResponse<AggResponse>> aggregate(@RequestBody AggRequest request) {
        try {
            log.debug("Received aggregation request: index={}", request.getIndex());
            return ResponseEntity.ok(ApiResponse.success(aggExecutor.execute(request)));
        } catch (SimpleElasticsearchSearchException e) {
            log.warn("Aggregation validation failed: index={}, error={}", request.getIndex(), e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Aggregation failed: index={}", request.getIndex(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== 索引管理 ====================

    /**
     * 获取所有索引列表
     */
    @GetMapping("/indices")
    public ResponseEntity<ApiResponse<List<IndexInfoResponse>>> getIndices() {
        try {
            List<IndexInfoResponse> result = mappingManager.getAllIndices().stream()
                    .map(config -> IndexInfoResponse.from(config, mappingManager))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Get indices failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(e.getMessage()));
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
            return ResponseEntity.ok(ApiResponse.success(IndexFieldsResponse.from(metadata)));
        } catch (SimpleElasticsearchSearchException e) {
            log.warn("Get fields failed: alias={}, indexName={}, error={}", alias, indexName, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get fields failed: alias={}, indexName={}", alias, indexName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(e.getMessage()));
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(e.getMessage()));
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
            log.warn("Refresh mapping failed: alias={}, error={}", alias, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Refresh mapping failed: alias={}", alias, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== 自然语言 ====================

    /**
     * 自然语言转 DSL
     *
     * @param text  自然语言查询文本
     * @param index 索引别名（可选）
     */
    @GetMapping(value = "/nl/dsl", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<?>> translateNLToDsl(
            @RequestParam String text,
            @RequestParam(required = false) String index) {
        if (nlDslService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("自然语言查询功能未启用，请引入 nl-dsl-starter 依赖"));
        }
        if (!StringUtils.hasText(text)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("text 不能为空"));
        }
        log.info("Received NL-to-DSL request: text='{}', index='{}'", truncate(text), index);
        try {
            Object dsl = nlDslService.translateToRequest(text, index);
            log.debug("NL-to-DSL succeeded: dsl={}", truncate(dsl));
            return ResponseEntity.ok(ApiResponse.success(dsl));
        } catch (NLParseException e) {
            log.warn("NL-to-DSL failed: text='{}', error={}", truncate(text), truncate(e.getMessage()));
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (NLDslTranslationException e) {
            log.warn("NL-to-DSL failed: text='{}', error={}", truncate(text), truncate(e.getMessage()));
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("NL-to-DSL unexpected error: text='{}'", truncate(text), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("翻译失败: " + e.getMessage()));
        }
    }

    // ==================== 条件表达式 ====================

    /**
     * 条件表达式查询
     */
    @PostMapping("/query/expression")
    public ResponseEntity<ApiResponse<QueryResponse>> queryByExpression(@RequestBody ExpressionQueryRequest request) {
        if (expressionService == null) {
            return unavailableExpression();
        }
        if (!StringUtils.hasText(request.getExpression())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("expression 不能为空"));
        }
        try {
            log.debug("Received expression query request: index={}", request.getIndex());
            QueryCondition condition = expressionService.translate(request.getExpression(), request.getIndex());
            QueryRequest queryRequest = QueryRequest.builder()
                    .index(request.getIndex())
                    .query(condition)
                    .pagination(request.getPagination())
                    .fields(request.getFields())
                    .dateRange(request.getDateRange())
                    .build();
            return ResponseEntity.ok(ApiResponse.success(queryExecutor.execute(queryRequest)));
        } catch (SimpleElasticsearchSearchException e) {
            log.warn("Expression query failed: index={}, error={}", request.getIndex(), e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Expression query failed: index={}", request.getIndex(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 获取表达式提示信息（供前端自动补全）
     *
     * @param index 索引别名（可选）
     */
    @GetMapping("/expression/hints")
    public ResponseEntity<ApiResponse<ExpressionHintsResponse>> expressionHints(
            @RequestParam(required = false) String index) {
        if (expressionService == null) {
            return unavailableExpression();
        }
        try {
            return ResponseEntity.ok(ApiResponse.success(expressionService.getHints(index)));
        } catch (Exception e) {
            log.error("Get expression hints failed: index={}", index, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 校验条件表达式语法
     *
     * @param expression 表达式字符串
     */
    @GetMapping("/expression/validate")
    public ResponseEntity<ApiResponse<ExpressionValidationResult>> validateExpression(
            @RequestParam String expression) {
        if (expressionService == null) {
            return unavailableExpression();
        }
        return ResponseEntity.ok(ApiResponse.success(expressionService.validate(expression)));
    }

    /**
     * 条件表达式聚合查询
     *
     * @param request 表达式聚合请求
     */
    @PostMapping("/agg/expression")
    public ResponseEntity<ApiResponse<AggResponse>> aggByExpression(
            @RequestBody ExpressionAggRequest request) {
        if (expressionService == null) {
            return unavailableExpression();
        }
        if (!StringUtils.hasText(request.getExpression())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("expression 不能为空"));
        }
        try {
            log.debug("Received expression agg request: index={}", request.getIndex());
            QueryCondition condition = expressionService.translate(
                    request.getExpression(), request.getIndex());
            AggRequest aggRequest = AggRequest.builder()
                    .index(request.getIndex())
                    .query(condition)
                    .aggs(request.getAggs())
                    .after(request.getAfter())
                    .build();
            return ResponseEntity.ok(ApiResponse.success(aggExecutor.execute(aggRequest)));
        } catch (SimpleElasticsearchSearchException e) {
            log.warn("Expression agg failed: index={}, error={}", request.getIndex(), e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Expression agg failed: index={}", request.getIndex(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== 私有工具方法 ====================

    private <T> ResponseEntity<ApiResponse<T>> unavailableExpression() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("表达式查询功能未启用，请引入 condition-expression-parser-starter 依赖"));
    }

    private String truncate(Object obj) {
        if (obj == null) {
            return null;
        }
        return logTruncator != null ? logTruncator.truncateRaw(obj.toString()) : obj.toString();
    }
}
