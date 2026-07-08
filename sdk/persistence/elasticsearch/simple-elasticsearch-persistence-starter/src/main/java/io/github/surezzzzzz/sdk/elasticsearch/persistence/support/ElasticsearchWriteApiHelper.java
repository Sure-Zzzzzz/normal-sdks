package io.github.surezzzzzz.sdk.elasticsearch.persistence.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.annotation.SimpleElasticsearchPersistenceComponent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.SimpleElasticsearchPersistenceConstant;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.ByQueryOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.ByQueryFailure;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.ByQueryTaskResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpEntity;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.*;

import java.io.InputStream;
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
        String endpoint = String.format(SimpleElasticsearchPersistenceConstant.UPDATE_BY_QUERY_PATH_TEMPLATE, request.getIndex());
        String body = buildUpdateByQueryBody(request);
        return executeByQuerySync(datasource, request.getIndex(), endpoint, body, true);
    }

    public ByQueryTaskResult deleteByQuerySync(String datasource,
                                               io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.DeleteByQueryRequest request) throws Exception {
        String endpoint = String.format(SimpleElasticsearchPersistenceConstant.DELETE_BY_QUERY_PATH_TEMPLATE, request.getIndex());
        String body = buildDeleteByQueryBody(request);
        return executeByQuerySync(datasource, request.getIndex(), endpoint, body, false);
    }

    /**
     * 提交 update-by-query 异步任务（wait_for_completion=false）。
     * 构造完整 request body，避免 ES 收不到 query/script。
     */
    public String submitUpdateByQueryTask(String datasource,
                                          io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateByQueryRequest request) throws Exception {
        String index = request.getIndex();
        String body = buildUpdateByQueryBody(request);
        String endpoint = String.format(SimpleElasticsearchPersistenceConstant.UPDATE_BY_QUERY_PATH_TEMPLATE, index);
        return submitByLowLevel(datasource, endpoint, body);
    }

    /**
     * 提交 delete-by-query 异步任务（wait_for_completion=false）。
     * 构造完整 request body，避免 ES 收不到 query。
     */
    public String submitDeleteByQueryTask(String datasource,
                                          io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.DeleteByQueryRequest request) throws Exception {
        String index = request.getIndex();
        String body = buildDeleteByQueryBody(request);
        String endpoint = String.format(SimpleElasticsearchPersistenceConstant.DELETE_BY_QUERY_PATH_TEMPLATE, index);
        return submitByLowLevel(datasource, endpoint, body);
    }

    public ByQueryTaskResult getTask(String datasource, String taskId) throws Exception {
        RestClient client = registry.getLowLevelClient(datasource);
        String endpoint = String.format(SimpleElasticsearchPersistenceConstant.TASK_PATH_TEMPLATE, taskId);
        Response response = client.performRequest(new Request(SimpleElasticsearchPersistenceConstant.HTTP_METHOD_GET, endpoint));
        Map<String, Object> body = parseBody(response);

        boolean completed = Boolean.TRUE.equals(body.get("completed"));
        Map<String, Object> task = extractMap(body, "task");
        String resolvedTaskId = resolveTaskId(taskId, task);

        ByQueryTaskResult.ByQueryTaskResultBuilder builder = ByQueryTaskResult.builder()
                .completed(completed)
                .taskId(resolvedTaskId)
                .datasource(datasource);

        Map<String, Object> status = completed ? extractMap(body, "response") : extractMap(task, "status");
        if (status != null) {
            builder.total(toLong(status.get("total")))
                    .updated(toLong(status.get("updated")))
                    .deleted(toLong(status.get("deleted")))
                    .versionConflicts(toLong(status.get("version_conflicts")));
        }

        if (completed) {
            Map<String, Object> resp = extractMap(body, "response");
            if (resp != null) {
                builder.tookMs(toLong(resp.get("took")));
                builder.failureList(extractFailures(resp.get("failures")));
            }
        }
        return builder.build();
    }

    private ByQueryTaskResult executeByQuerySync(String datasource, String index, String endpoint, String body,
                                                 boolean updateOperation) throws Exception {
        RestClient client = registry.getLowLevelClient(datasource);
        Request request = new Request(SimpleElasticsearchPersistenceConstant.HTTP_METHOD_POST, endpoint);
        request.addParameter(SimpleElasticsearchPersistenceConstant.PARAM_WAIT_FOR_COMPLETION,
                SimpleElasticsearchPersistenceConstant.PARAM_VALUE_TRUE);
        if (body != null) {
            request.setJsonEntity(body);
        }
        Response response = client.performRequest(request);
        Map<String, Object> responseBody = parseBody(response);
        ByQueryTaskResult.ByQueryTaskResultBuilder builder = ByQueryTaskResult.builder()
                .completed(true)
                .datasource(datasource)
                .index(index)
                .total(toLong(responseBody.get("total")))
                .versionConflicts(toLong(responseBody.get("version_conflicts")))
                .tookMs(toLong(responseBody.get("took")))
                .failureList(extractFailures(responseBody.get("failures")));
        if (updateOperation) {
            builder.updated(toLong(responseBody.get("updated")));
        } else {
            builder.deleted(toLong(responseBody.get("deleted")));
        }
        return builder.build();
    }

    private String submitByLowLevel(String datasource, String endpoint, String body) throws Exception {
        RestClient client = registry.getLowLevelClient(datasource);
        Request request = new Request(SimpleElasticsearchPersistenceConstant.HTTP_METHOD_POST, endpoint);
        request.addParameter(SimpleElasticsearchPersistenceConstant.PARAM_WAIT_FOR_COMPLETION,
                SimpleElasticsearchPersistenceConstant.PARAM_VALUE_FALSE);
        if (body != null) {
            request.setJsonEntity(body);
        }
        Response response = client.performRequest(request);
        Map<String, Object> responseBody = parseBody(response);
        Object task = responseBody.get("task");
        return task == null ? null : String.valueOf(task);
    }

    private String buildUpdateByQueryBody(io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateByQueryRequest request) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", buildQueryJson(request.getQuery()));
        if (request.getScriptSource() != null && !request.getScriptSource().isEmpty()) {
            Map<String, Object> script = new LinkedHashMap<>();
            script.put("source", request.getScriptSource());
            script.put("lang", SimpleElasticsearchPersistenceConstant.DEFAULT_SCRIPT_LANG);
            if (request.getScriptParamMap() != null) {
                script.put("params", request.getScriptParamMap());
            }
            body.put("script", script);
        }
        applyByQueryBodyOptions(body, request.getOptions());
        return OBJECT_MAPPER.writeValueAsString(body);
    }

    private String buildDeleteByQueryBody(io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.DeleteByQueryRequest request) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", buildQueryJson(request.getQuery()));
        applyByQueryBodyOptions(body, request.getOptions());
        return OBJECT_MAPPER.writeValueAsString(body);
    }

    private Map<String, Object> buildQueryJson(io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.query.PersistenceQuery query) {
        if (query == null) {
            Map<String, Object> matchAll = new LinkedHashMap<>();
            matchAll.put("match_all", Collections.emptyMap());
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
        bool.put("must", buildMustClauses(query));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bool", bool);
        return result;
    }

    private List<Map<String, Object>> buildMustClauses(io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.query.PersistenceQuery query) {
        List<Map<String, Object>> clauses = new ArrayList<>();
        if (query.getTermMap() != null) {
            for (Map.Entry<String, Object> entry : query.getTermMap().entrySet()) {
                Map<String, Object> term = new LinkedHashMap<>();
                term.put("term", Collections.singletonMap(entry.getKey(), entry.getValue()));
                clauses.add(term);
            }
        }
        if (query.getRangeMap() != null) {
            for (Map.Entry<String, Object> entry : query.getRangeMap().entrySet()) {
                Map<String, Object> range = new LinkedHashMap<>();
                range.put("range", Collections.singletonMap(entry.getKey(), entry.getValue()));
                clauses.add(range);
            }
        }
        if (clauses.isEmpty()) {
            Map<String, Object> matchAll = new LinkedHashMap<>();
            matchAll.put("match_all", Collections.emptyMap());
            clauses.add(matchAll);
        }
        return clauses;
    }

    private void applyByQueryBodyOptions(Map<String, Object> body, ByQueryOptions options) {
        if (options == null) {
            return;
        }
        if (options.getRefresh() != null) {
            body.put("refresh", options.getRefresh());
        }
        if (options.getTimeoutMs() != null) {
            body.put("timeout", options.getTimeoutMs() + SimpleElasticsearchPersistenceConstant.TIMEOUT_MS_SUFFIX);
        }
        if (options.getSlices() != null) {
            body.put("slices", options.getSlices());
        }
        if (options.getConflicts() != null) {
            body.put("conflicts", options.getConflicts());
        }
        if (options.getScrollSize() != null) {
            body.put("scroll_size", options.getScrollSize());
        }
    }

    private Map<String, Object> parseBody(Response response) throws Exception {
        HttpEntity entity = response.getEntity();
        if (entity == null) {
            return Collections.emptyMap();
        }
        try (InputStream is = entity.getContent()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(is, Map.class);
            return parsed == null ? Collections.emptyMap() : parsed;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> parent, String key) {
        if (parent == null) {
            return null;
        }
        Object value = parent.get(key);
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private String resolveTaskId(String taskId, Map<String, Object> task) {
        if (task == null) {
            return taskId;
        }
        Object node = task.get("node");
        Object id = task.get("id");
        if (node != null && id != null) {
            return String.valueOf(node) + ":" + String.valueOf(id);
        }
        return taskId;
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
                    .index(getAsString(failure.get("index")))
                    .id(getAsString(failure.get("id")))
                    .cause(extractCause(failure.get("cause")))
                    .status(getAsString(failure.get("status")))
                    .build());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private String extractCause(Object cause) {
        if (cause == null) {
            return null;
        }
        if (cause instanceof Map) {
            Object reason = ((Map<String, Object>) cause).get("reason");
            if (reason != null) {
                return String.valueOf(reason);
            }
        }
        return String.valueOf(cause);
    }

    private String getAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
