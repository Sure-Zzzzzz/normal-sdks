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
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
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
        return executeByQuerySync(datasource, request.getIndex(), endpoint, body, true);
    }

    public ByQueryTaskResult deleteByQuerySync(String datasource,
                                               io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.DeleteByQueryRequest request) throws Exception {
        String endpoint = ElasticsearchEndpointHelper.buildDeleteByQueryEndpoint(request.getIndex());
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
        String endpoint = ElasticsearchEndpointHelper.buildUpdateByQueryEndpoint(index);
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
        String endpoint = ElasticsearchEndpointHelper.buildDeleteByQueryEndpoint(index);
        return submitByLowLevel(datasource, endpoint, body);
    }

    public ByQueryTaskResult getTask(String datasource, String taskId) throws Exception {
        RestClient client = registry.getLowLevelClient(datasource);
        String endpoint = ElasticsearchEndpointHelper.buildTaskEndpoint(taskId);
        Response response = ElasticsearchLowLevelRequestHelper.execute(client,
                ElasticsearchLowLevelRequestHelper.newRequest(SimpleElasticsearchRouteConstant.HTTP_METHOD_GET, endpoint));
        Map<String, Object> body = parseBody(response);

        boolean completed = Boolean.TRUE.equals(body.get(SimpleElasticsearchRouteConstant.JSON_FIELD_COMPLETED));
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
                                                 boolean updateOperation) throws Exception {
        RestClient client = registry.getLowLevelClient(datasource);
        Request request = ElasticsearchLowLevelRequestHelper.newJsonRequest(
                SimpleElasticsearchRouteConstant.HTTP_METHOD_POST, endpoint, body);
        ElasticsearchLowLevelRequestHelper.addParameter(request,
                SimpleElasticsearchRouteConstant.PARAM_WAIT_FOR_COMPLETION,
                SimpleElasticsearchRouteConstant.PARAM_VALUE_TRUE);
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
        if (updateOperation) {
            builder.updated(toLong(responseBody.get(SimpleElasticsearchRouteConstant.JSON_FIELD_UPDATED)));
        } else {
            builder.deleted(toLong(responseBody.get(SimpleElasticsearchRouteConstant.JSON_FIELD_DELETED)));
        }
        return builder.build();
    }

    private String submitByLowLevel(String datasource, String endpoint, String body) throws Exception {
        RestClient client = registry.getLowLevelClient(datasource);
        Request request = ElasticsearchLowLevelRequestHelper.newJsonRequest(
                SimpleElasticsearchRouteConstant.HTTP_METHOD_POST, endpoint, body);
        ElasticsearchLowLevelRequestHelper.addParameter(request,
                SimpleElasticsearchRouteConstant.PARAM_WAIT_FOR_COMPLETION,
                SimpleElasticsearchRouteConstant.PARAM_VALUE_FALSE);
        Response response = ElasticsearchLowLevelRequestHelper.execute(client, request);
        Map<String, Object> responseBody = parseBody(response);
        Object task = responseBody.get(SimpleElasticsearchRouteConstant.JSON_FIELD_TASK);
        return task == null ? null : String.valueOf(task);
    }

    private String buildUpdateByQueryBody(io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateByQueryRequest request) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", buildQueryJson(request.getQuery()));
        if (request.getScriptSource() != null && !request.getScriptSource().isEmpty()) {
            Map<String, Object> script = new LinkedHashMap<>();
            script.put("source", request.getScriptSource());
            script.put(SimpleElasticsearchRouteConstant.JSON_FIELD_LANG, SimpleElasticsearchPersistenceConstant.DEFAULT_SCRIPT_LANG);
            if (request.getScriptParamMap() != null) {
                script.put(SimpleElasticsearchRouteConstant.JSON_FIELD_PARAMS, request.getScriptParamMap());
            }
            body.put(SimpleElasticsearchRouteConstant.JSON_FIELD_SCRIPT, script);
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
        ElasticsearchRequestOptionHelper.applyByQueryBodyOptions(body, options.getRefresh(), options.getTimeoutMs(),
                options.getSlices(), options.getConflicts(), options.getScrollSize());
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
