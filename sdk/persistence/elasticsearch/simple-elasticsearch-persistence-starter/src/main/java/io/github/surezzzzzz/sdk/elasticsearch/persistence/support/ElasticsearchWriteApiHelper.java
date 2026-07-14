package io.github.surezzzzzz.sdk.elasticsearch.persistence.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.annotation.SimpleElasticsearchPersistenceComponent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.SimpleElasticsearchPersistenceConstant;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.PersistenceOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.ByQueryOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.ByQueryFailure;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.ByQueryTaskResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ByQueryRequestOptions;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchEndpointHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchLowLevelRequestHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchRequestOptionHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchResponseHelper;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.*;

import java.util.*;

/**
 * Elasticsearch Write API Helper
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchPersistenceComponent
@RequiredArgsConstructor
public class ElasticsearchWriteApiHelper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SimpleElasticsearchRouteRegistry registry;

    public DocWriteResponse index(String datasource, IndexRequest request) throws Exception {
        RestHighLevelClient client = registry.getHighLevelClient(datasource);
        return client.index(request, RequestOptions.DEFAULT);
    }

    public DocWriteResponse update(String datasource, UpdateRequest request) throws Exception {
        RestHighLevelClient client = registry.getHighLevelClient(datasource);
        return client.update(request, RequestOptions.DEFAULT);
    }

    public DocWriteResponse delete(String datasource, DeleteRequest request) throws Exception {
        RestHighLevelClient client = registry.getHighLevelClient(datasource);
        return client.delete(request, RequestOptions.DEFAULT);
    }

    public BulkResponse bulk(String datasource, org.elasticsearch.action.bulk.BulkRequest request) throws Exception {
        RestHighLevelClient client = registry.getHighLevelClient(datasource);
        return client.bulk(request, RequestOptions.DEFAULT);
    }

    public ByQueryTaskResult updateByQuerySync(String datasource,
                                               io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateByQueryRequest request) throws Exception {
        String endpoint = ElasticsearchEndpointHelper.buildUpdateByQueryEndpoint(request.getIndex());
        String body = buildUpdateByQueryBody(request);
        return executeByQuerySync(datasource, request.getIndex(), endpoint, body, request.getOptions(),
                PersistenceOperationType.UPDATE_BY_QUERY);
    }

    public ByQueryTaskResult deleteByQuerySync(String datasource,
                                               io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.DeleteByQueryRequest request) throws Exception {
        String endpoint = ElasticsearchEndpointHelper.buildDeleteByQueryEndpoint(request.getIndex());
        String body = buildDeleteByQueryBody(request);
        return executeByQuerySync(datasource, request.getIndex(), endpoint, body, request.getOptions(),
                PersistenceOperationType.DELETE_BY_QUERY);
    }

    /**
     * 提交 update-by-query 异步任务（wait_for_completion=false）。
     * 构造完整 request body，避免 ES 收不到 query/script。
     */
    public String submitUpdateByQueryTask(String datasource,
                                          io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateByQueryRequest request) throws Exception {
        String index = request.getIndex();
        String body = buildUpdateByQueryBody(request);
        String endpoint = ElasticsearchEndpointHelper.buildUpdateByQueryEndpoint(index);
        return submitByLowLevel(datasource, endpoint, body, request.getOptions());
    }

    /**
     * 提交 delete-by-query 异步任务（wait_for_completion=false）。
     * 构造完整 request body，避免 ES 收不到 query。
     */
    public String submitDeleteByQueryTask(String datasource,
                                          io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.DeleteByQueryRequest request) throws Exception {
        String index = request.getIndex();
        String body = buildDeleteByQueryBody(request);
        String endpoint = ElasticsearchEndpointHelper.buildDeleteByQueryEndpoint(index);
        return submitByLowLevel(datasource, endpoint, body, request.getOptions());
    }

    public ByQueryTaskResult getTask(String datasource, String taskId) throws Exception {
        RestClient client = registry.getLowLevelClient(datasource);
        String endpoint = ElasticsearchEndpointHelper.buildTaskEndpoint(taskId);
        Response response = ElasticsearchLowLevelRequestHelper.execute(client,
                ElasticsearchLowLevelRequestHelper.newRequest(SimpleElasticsearchRouteConstant.HTTP_METHOD_GET, endpoint));
        Map<String, Object> body = parseBody(response);

        if (!body.containsKey(SimpleElasticsearchRouteConstant.JSON_FIELD_COMPLETED)
                && !body.containsKey(SimpleElasticsearchRouteConstant.JSON_FIELD_TASK)) {
            throw new PersistenceExecutionException(ErrorCode.EXECUTION_FAILED,
                    String.format(ErrorMessage.EXECUTION_FAILED,
                            String.format(SimpleElasticsearchPersistenceConstant.TEMPLATE_TASK_NOT_FOUND, taskId)), null);
        }

        boolean completed = Boolean.TRUE.equals(body.get(SimpleElasticsearchRouteConstant.JSON_FIELD_COMPLETED));
        if (completed) {
            Object error = body.get(SimpleElasticsearchPersistenceConstant.JSON_FIELD_ERROR);
            if (error != null) {
                String reason = ElasticsearchResponseHelper.extractFailureCause(error);
                throw new PersistenceExecutionException(ErrorCode.EXECUTION_FAILED,
                        String.format(ErrorMessage.EXECUTION_FAILED,
                                String.format(SimpleElasticsearchPersistenceConstant.TEMPLATE_TASK_EXECUTION_FAILED, reason)), null);
            }
        }

        Map<String, Object> task = extractMap(body, SimpleElasticsearchRouteConstant.JSON_FIELD_TASK);
        String resolvedTaskId = ElasticsearchResponseHelper.extractTaskId(taskId, task);

        ByQueryTaskResult.ByQueryTaskResultBuilder builder = ByQueryTaskResult.builder()
                .completed(completed)
                .taskId(resolvedTaskId)
                .datasource(datasource);

        Map<String, Object> status = completed
                ? extractMap(body, SimpleElasticsearchRouteConstant.JSON_FIELD_RESPONSE)
                : extractMap(task, SimpleElasticsearchRouteConstant.JSON_FIELD_STATUS);
        if (status != null) {
            builder.total(toLong(status.get(SimpleElasticsearchRouteConstant.JSON_FIELD_TOTAL)))
                    .updated(toLong(status.get(SimpleElasticsearchRouteConstant.JSON_FIELD_UPDATED)))
                    .deleted(toLong(status.get(SimpleElasticsearchRouteConstant.JSON_FIELD_DELETED)))
                    .versionConflicts(toLong(status.get(SimpleElasticsearchRouteConstant.JSON_FIELD_VERSION_CONFLICTS)));
        }

        if (completed) {
            Map<String, Object> resp = extractMap(body, SimpleElasticsearchRouteConstant.JSON_FIELD_RESPONSE);
            if (resp != null) {
                builder.tookMs(toLong(resp.get(SimpleElasticsearchRouteConstant.JSON_FIELD_TOOK)));
                builder.failureList(extractFailures(resp.get(SimpleElasticsearchRouteConstant.JSON_FIELD_FAILURES)));
            }
        }
        return builder.build();
    }

    private ByQueryTaskResult executeByQuerySync(String datasource, String index, String endpoint, String body,
                                                 ByQueryOptions options, PersistenceOperationType operationType) throws Exception {
        RestClient client = registry.getLowLevelClient(datasource);
        Request request = buildByQueryRequest(endpoint, body, options, true);
        Response response = ElasticsearchLowLevelRequestHelper.execute(client, request);
        Map<String, Object> responseBody = parseBody(response);
        ByQueryTaskResult.ByQueryTaskResultBuilder builder = ByQueryTaskResult.builder()
                .completed(true)
                .datasource(datasource)
                .index(index)
                .total(toLong(responseBody.get(SimpleElasticsearchRouteConstant.JSON_FIELD_TOTAL)))
                .versionConflicts(toLong(responseBody.get(SimpleElasticsearchRouteConstant.JSON_FIELD_VERSION_CONFLICTS)))
                .tookMs(toLong(responseBody.get(SimpleElasticsearchRouteConstant.JSON_FIELD_TOOK)))
                .failureList(extractFailures(responseBody.get(SimpleElasticsearchRouteConstant.JSON_FIELD_FAILURES)));
        if (operationType == PersistenceOperationType.UPDATE_BY_QUERY) {
            builder.updated(toLong(responseBody.get(SimpleElasticsearchRouteConstant.JSON_FIELD_UPDATED)));
        } else {
            builder.deleted(toLong(responseBody.get(SimpleElasticsearchRouteConstant.JSON_FIELD_DELETED)));
        }
        return builder.build();
    }

    private String submitByLowLevel(String datasource, String endpoint, String body, ByQueryOptions options) throws Exception {
        RestClient client = registry.getLowLevelClient(datasource);
        Request request = buildByQueryRequest(endpoint, body, options, false);
        Response response = ElasticsearchLowLevelRequestHelper.execute(client, request);
        Map<String, Object> responseBody = parseBody(response);
        Object task = responseBody.get(SimpleElasticsearchRouteConstant.JSON_FIELD_TASK);
        if (task == null) {
            throw new PersistenceExecutionException(ErrorCode.EXECUTION_FAILED,
                    String.format(ErrorMessage.EXECUTION_FAILED, "异步 by-query 任务提交未返回 task 标识"), null);
        }
        return String.valueOf(task);
    }

    private String buildUpdateByQueryBody(io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateByQueryRequest request) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(SimpleElasticsearchRouteConstant.JSON_FIELD_QUERY, buildQueryJson(request.getQuery()));
        if (request.getScriptSource() != null && !request.getScriptSource().isEmpty()) {
            Map<String, Object> script = new LinkedHashMap<>();
            script.put(SimpleElasticsearchPersistenceConstant.JSON_FIELD_SCRIPT_SOURCE, request.getScriptSource());
            script.put(SimpleElasticsearchRouteConstant.JSON_FIELD_LANG, SimpleElasticsearchPersistenceConstant.DEFAULT_SCRIPT_LANG);
            if (request.getScriptParamMap() != null) {
                script.put(SimpleElasticsearchRouteConstant.JSON_FIELD_PARAMS, request.getScriptParamMap());
            }
            body.put(SimpleElasticsearchRouteConstant.JSON_FIELD_SCRIPT, script);
        }
        return OBJECT_MAPPER.writeValueAsString(body);
    }

    private String buildDeleteByQueryBody(io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.DeleteByQueryRequest request) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(SimpleElasticsearchRouteConstant.JSON_FIELD_QUERY, buildQueryJson(request.getQuery()));
        return OBJECT_MAPPER.writeValueAsString(body);
    }

    /**
     * 构造 by-query 的 query JSON。
     * <p>query 为 null 时返回 match_all，会匹配索引全部文档。异步场景下若误用会批量更新/删除全索引，
     * 调用方应显式传入限定范围的 query。</p>
     */
    private Map<String, Object> buildQueryJson(io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.query.PersistenceQuery query) {
        if (query == null) {
            Map<String, Object> matchAll = new LinkedHashMap<>();
            matchAll.put(SimpleElasticsearchPersistenceConstant.JSON_FIELD_MATCH_ALL, Collections.emptyMap());
            return matchAll;
        }
        if (query.getRawJson() != null && !query.getRawJson().isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = OBJECT_MAPPER.readValue(query.getRawJson(), Map.class);
                return parsed;
            } catch (Exception e) {
                throw new PersistenceExecutionException(ErrorCode.ES_REQUEST_BUILD_FAILED,
                        String.format(ErrorMessage.ES_REQUEST_BUILD_FAILED, query.getRawJson()), e);
            }
        }
        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put(SimpleElasticsearchPersistenceConstant.JSON_FIELD_MUST, buildMustClauses(query));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(SimpleElasticsearchPersistenceConstant.JSON_FIELD_BOOL, bool);
        return result;
    }

    private List<Map<String, Object>> buildMustClauses(io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.query.PersistenceQuery query) {
        List<Map<String, Object>> clauses = new ArrayList<>();
        if (query.getTermMap() != null) {
            for (Map.Entry<String, Object> entry : query.getTermMap().entrySet()) {
                Map<String, Object> term = new LinkedHashMap<>();
                term.put(SimpleElasticsearchPersistenceConstant.JSON_FIELD_TERM, Collections.singletonMap(entry.getKey(), entry.getValue()));
                clauses.add(term);
            }
        }
        if (query.getRangeMap() != null) {
            for (Map.Entry<String, Object> entry : query.getRangeMap().entrySet()) {
                Map<String, Object> range = new LinkedHashMap<>();
                range.put(SimpleElasticsearchPersistenceConstant.JSON_FIELD_RANGE, Collections.singletonMap(entry.getKey(), entry.getValue()));
                clauses.add(range);
            }
        }
        if (clauses.isEmpty()) {
            Map<String, Object> matchAll = new LinkedHashMap<>();
            matchAll.put(SimpleElasticsearchPersistenceConstant.JSON_FIELD_MATCH_ALL, Collections.emptyMap());
            clauses.add(matchAll);
        }
        return clauses;
    }

    /**
     * 解析有效 scroll_size：scrollSize 优先，否则取 batchSize 作为兼容别名。
     *
     * @param options by-query 选项，可为 null
     * @return 有效 scroll_size，两者都为 null 时返回 null
     */
    public static Integer resolveScrollSize(ByQueryOptions options) {
        if (options == null) {
            return null;
        }
        return options.getScrollSize() != null
                ? options.getScrollSize()
                : options.getBatchSize();
    }

    /**
     * 统一构造同步/异步 by-query low-level 请求。body 仅承载 query/script，
     * 执行选项全部写入 URL query parameter，避免 ES 解析 body 时触发 parsing exception。
     * <p>public 以便单元测试覆盖 URL 参数映射与 scrollSize 优先级。</p>
     *
     * @param endpoint          by-query 端点
     * @param body              JSON body（仅 query/script）
     * @param options           by-query 选项，可为 null
     * @param waitForCompletion 是否等待完成：同步 true，异步 false
     * @return low-level Request
     */
    public static Request buildByQueryRequest(String endpoint, String body, ByQueryOptions options, boolean waitForCompletion) {
        Request request = ElasticsearchLowLevelRequestHelper.newJsonRequest(
                SimpleElasticsearchRouteConstant.HTTP_METHOD_POST, endpoint, body);
        ByQueryOptions effectiveOptions = options != null ? options : ByQueryOptions.builder().build();
        Integer effectiveScrollSize = resolveScrollSize(effectiveOptions);
        ByQueryRequestOptions requestOptions = ByQueryRequestOptions.builder()
                .waitForCompletion(waitForCompletion)
                .refresh(effectiveOptions.getRefresh())
                .timeoutMs(effectiveOptions.getTimeoutMs())
                .slices(effectiveOptions.getSlices())
                .conflicts(effectiveOptions.getConflicts())
                .scrollSize(effectiveScrollSize)
                .requestsPerSecond(effectiveOptions.getRequestsPerSecond())
                .maxDocs(effectiveOptions.getMaxDocs())
                .waitForActiveShards(effectiveOptions.getWaitForActiveShards())
                .routing(effectiveOptions.getRouting())
                .build();
        ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(request, requestOptions);
        return request;
    }

    private Map<String, Object> parseBody(Response response) throws Exception {
        return ElasticsearchResponseHelper.parseResponseBodyAsMap(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> parent, String key) {
        if (parent == null) {
            return null;
        }
        Object value = parent.get(key);
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @SuppressWarnings("unchecked")
    private List<ByQueryFailure> extractFailures(Object failures) {
        if (!(failures instanceof List)) {
            return Collections.emptyList();
        }
        List<ByQueryFailure> result = new ArrayList<>();
        for (Object item : (List<Object>) failures) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> failure = (Map<String, Object>) item;
            result.add(ByQueryFailure.builder()
                    .index(getAsString(failure.get(SimpleElasticsearchRouteConstant.JSON_FIELD_INDEX)))
                    .id(getAsString(failure.get(SimpleElasticsearchRouteConstant.JSON_FIELD_ID)))
                    .cause(ElasticsearchResponseHelper.extractFailureCause(
                            failure.get(SimpleElasticsearchRouteConstant.JSON_FIELD_CAUSE)))
                    .status(getAsString(failure.get(SimpleElasticsearchRouteConstant.JSON_FIELD_STATUS)))
                    .build());
        }
        return result;
    }

    private String getAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
