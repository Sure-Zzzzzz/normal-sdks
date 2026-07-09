package io.github.surezzzzzz.sdk.elasticsearch.route.support;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.support.replication.ReplicationRequest;
import org.elasticsearch.action.support.single.instance.InstanceShardOperationRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.index.reindex.AbstractBulkByScrollRequest;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;

import java.util.Map;

/**
 * Elasticsearch 请求选项 helper
 *
 * @author surezzzzzz
 */
public final class ElasticsearchRequestOptionHelper {

    private ElasticsearchRequestOptionHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void applyIndicesOptions(Request request, IndicesOptions options) {
        if (request == null || options == null) {
            return;
        }
        if (options.ignoreUnavailable()) {
            request.addParameter(SimpleElasticsearchRouteConstant.PARAM_IGNORE_UNAVAILABLE,
                    SimpleElasticsearchRouteConstant.PARAM_VALUE_TRUE);
        }
        if (options.allowNoIndices()) {
            request.addParameter(SimpleElasticsearchRouteConstant.PARAM_ALLOW_NO_INDICES,
                    SimpleElasticsearchRouteConstant.PARAM_VALUE_TRUE);
        }
        String expandWildcards = toExpandWildcards(options);
        if (expandWildcards != null && !expandWildcards.isEmpty()) {
            request.addParameter(SimpleElasticsearchRouteConstant.PARAM_EXPAND_WILDCARDS, expandWildcards);
        }
    }

    public static String toExpandWildcards(IndicesOptions options) {
        if (options == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        if (options.expandWildcardsOpen()) {
            builder.append(SimpleElasticsearchRouteConstant.PARAM_VALUE_OPEN);
        }
        if (options.expandWildcardsClosed()) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(SimpleElasticsearchRouteConstant.PARAM_VALUE_CLOSED);
        }
        return builder.toString();
    }

    public static String toBooleanParam(Boolean value) {
        if (value == null) {
            return null;
        }
        return Boolean.TRUE.equals(value)
                ? SimpleElasticsearchRouteConstant.PARAM_VALUE_TRUE
                : SimpleElasticsearchRouteConstant.PARAM_VALUE_FALSE;
    }

    public static String toTimeoutMs(Long timeoutMs) {
        if (timeoutMs == null) {
            return null;
        }
        return timeoutMs + SimpleElasticsearchRouteConstant.TIMEOUT_MS_SUFFIX;
    }

    public static WriteRequest.RefreshPolicy toRefreshPolicy(String refreshPolicy) {
        if (refreshPolicy == null || refreshPolicy.trim().isEmpty()) {
            return null;
        }
        if (SimpleElasticsearchRouteConstant.REFRESH_POLICY_TRUE.equals(refreshPolicy)) {
            return WriteRequest.RefreshPolicy.IMMEDIATE;
        }
        if (SimpleElasticsearchRouteConstant.REFRESH_POLICY_FALSE.equals(refreshPolicy)) {
            return WriteRequest.RefreshPolicy.NONE;
        }
        if (SimpleElasticsearchRouteConstant.REFRESH_POLICY_WAIT_FOR.equals(refreshPolicy)) {
            return WriteRequest.RefreshPolicy.WAIT_UNTIL;
        }
        return WriteRequest.RefreshPolicy.parse(refreshPolicy);
    }

    public static WriteRequest.RefreshPolicy toRefreshPolicy(Boolean refresh) {
        if (refresh == null) {
            return null;
        }
        return Boolean.TRUE.equals(refresh) ? WriteRequest.RefreshPolicy.IMMEDIATE : WriteRequest.RefreshPolicy.NONE;
    }

    public static void applyRefreshPolicy(WriteRequest<?> request, String refreshPolicy, Boolean refresh) {
        if (request == null) {
            return;
        }
        WriteRequest.RefreshPolicy policy = toRefreshPolicy(refreshPolicy);
        if (policy == null) {
            policy = toRefreshPolicy(refresh);
        }
        if (policy != null) {
            request.setRefreshPolicy(policy);
        }
    }

    public static void applyTimeout(ReplicationRequest<?> request, Long timeoutMs) {
        String timeout = toTimeoutMs(timeoutMs);
        if (request != null && timeout != null) {
            request.timeout(timeout);
        }
    }

    public static void applyTimeout(InstanceShardOperationRequest<?> request, Long timeoutMs) {
        String timeout = toTimeoutMs(timeoutMs);
        if (request != null && timeout != null) {
            request.timeout(timeout);
        }
    }

    public static void applyTimeout(BulkRequest request, Long timeoutMs) {
        String timeout = toTimeoutMs(timeoutMs);
        if (request != null && timeout != null) {
            request.timeout(timeout);
        }
    }

    public static void applyByQueryOptions(UpdateByQueryRequest request,
                                           Integer batchSize, Integer slices, String conflicts,
                                           Boolean refresh, Long timeoutMs) {
        if (request == null) {
            return;
        }
        if (batchSize != null) {
            request.setBatchSize(batchSize);
        }
        applyCommonByQueryOptions(request, slices, conflicts, refresh, timeoutMs);
    }

    public static void applyByQueryOptions(DeleteByQueryRequest request,
                                           Integer batchSize, Integer slices, String conflicts,
                                           Boolean refresh, Long timeoutMs) {
        if (request == null) {
            return;
        }
        if (batchSize != null) {
            request.setBatchSize(batchSize);
        }
        applyCommonByQueryOptions(request, slices, conflicts, refresh, timeoutMs);
    }

    public static void applyCommonByQueryOptions(AbstractBulkByScrollRequest<?> request,
                                                 Integer slices, String conflicts,
                                                 Boolean refresh, Long timeoutMs) {
        if (request == null) {
            return;
        }
        if (slices != null) {
            request.setSlices(slices);
        }
        if (conflicts != null && !conflicts.trim().isEmpty()) {
            request.setConflicts(conflicts);
        }
        if (refresh != null) {
            request.setRefresh(refresh);
        }
        String timeout = toTimeoutMs(timeoutMs);
        if (timeout != null) {
            request.setTimeout(timeout);
        }
    }

    public static void applyByQueryBodyOptions(Map<String, Object> body,
                                               Boolean refresh, Long timeoutMs,
                                               Integer slices, String conflicts,
                                               Integer scrollSize) {
        if (body == null) {
            return;
        }
        if (refresh != null) {
            body.put(SimpleElasticsearchRouteConstant.PARAM_REFRESH, refresh);
        }
        String timeout = toTimeoutMs(timeoutMs);
        if (timeout != null) {
            body.put(SimpleElasticsearchRouteConstant.PARAM_TIMEOUT, timeout);
        }
        if (slices != null) {
            body.put(SimpleElasticsearchRouteConstant.PARAM_SLICES, slices);
        }
        if (conflicts != null && !conflicts.trim().isEmpty()) {
            body.put(SimpleElasticsearchRouteConstant.PARAM_CONFLICTS, conflicts);
        }
        if (scrollSize != null) {
            body.put(SimpleElasticsearchRouteConstant.PARAM_SCROLL_SIZE, scrollSize);
        }
    }
}
