package io.github.surezzzzzz.sdk.elasticsearch.route.support;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;

/**
 * Elasticsearch 端点构造 helper
 *
 * @author surezzzzzz
 */
public final class ElasticsearchEndpointHelper {

    private ElasticsearchEndpointHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String joinIndices(String[] indices) {
        if (indices == null || indices.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String index : indices) {
            if (index == null || index.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(index.trim());
        }
        return builder.toString();
    }

    public static String buildIndexEndpoint(String index) {
        return SimpleElasticsearchRouteConstant.ENDPOINT_ROOT + index.trim();
    }

    public static String buildSearchEndpoint(String[] indices) {
        String indexPath = joinIndices(indices);
        if (indexPath.isEmpty()) {
            return SimpleElasticsearchRouteConstant.ENDPOINT_SEARCH;
        }
        return SimpleElasticsearchRouteConstant.ENDPOINT_ROOT + indexPath
                + SimpleElasticsearchRouteConstant.ENDPOINT_SEARCH;
    }

    public static String buildSearchEndpoint(String[] indices, String scrollKeepAlive) {
        String endpoint = buildSearchEndpoint(indices);
        if (scrollKeepAlive == null || scrollKeepAlive.trim().isEmpty()) {
            return endpoint;
        }
        return endpoint + "?" + SimpleElasticsearchRouteConstant.PARAM_SCROLL + "=" + scrollKeepAlive.trim();
    }

    public static String buildCountEndpoint(String[] indices) {
        String indexPath = joinIndices(indices);
        if (indexPath.isEmpty()) {
            return SimpleElasticsearchRouteConstant.ENDPOINT_COUNT;
        }
        return SimpleElasticsearchRouteConstant.ENDPOINT_ROOT + indexPath
                + SimpleElasticsearchRouteConstant.ENDPOINT_COUNT;
    }

    public static String buildMappingEndpoint(String index) {
        if (index == null || index.trim().isEmpty()) {
            return SimpleElasticsearchRouteConstant.ENDPOINT_MAPPING;
        }
        return SimpleElasticsearchRouteConstant.ENDPOINT_ROOT + index.trim()
                + SimpleElasticsearchRouteConstant.ENDPOINT_MAPPING;
    }

    public static String buildDocEndpoint(String index, String id) {
        return SimpleElasticsearchRouteConstant.ENDPOINT_ROOT + index.trim()
                + SimpleElasticsearchRouteConstant.ENDPOINT_DOC_TYPE + id;
    }

    public static String buildRefreshEndpoint(String index) {
        if (index == null || index.trim().isEmpty()) {
            return SimpleElasticsearchRouteConstant.ENDPOINT_REFRESH;
        }
        return SimpleElasticsearchRouteConstant.ENDPOINT_ROOT + index.trim()
                + SimpleElasticsearchRouteConstant.ENDPOINT_REFRESH;
    }

    public static String buildUpdateByQueryEndpoint(String index) {
        return SimpleElasticsearchRouteConstant.ENDPOINT_ROOT + index
                + SimpleElasticsearchRouteConstant.ENDPOINT_UPDATE_BY_QUERY;
    }

    public static String buildDeleteByQueryEndpoint(String index) {
        return SimpleElasticsearchRouteConstant.ENDPOINT_ROOT + index
                + SimpleElasticsearchRouteConstant.ENDPOINT_DELETE_BY_QUERY;
    }

    public static String buildTaskEndpoint(String taskId) {
        return String.format(SimpleElasticsearchRouteConstant.ENDPOINT_TASKS_TEMPLATE, taskId);
    }

    public static String buildOpenPitEndpoint(String index, String keepAlive) {
        StringBuilder endpoint = new StringBuilder();
        endpoint.append(SimpleElasticsearchRouteConstant.ENDPOINT_ROOT)
                .append(index)
                .append(SimpleElasticsearchRouteConstant.ENDPOINT_OPEN_PIT);
        if (keepAlive != null && !keepAlive.trim().isEmpty()) {
            endpoint.append('?')
                    .append(SimpleElasticsearchRouteConstant.PARAM_KEEP_ALIVE)
                    .append('=')
                    .append(keepAlive.trim());
        }
        return endpoint.toString();
    }

    public static String buildClosePitEndpoint() {
        return SimpleElasticsearchRouteConstant.ENDPOINT_PIT;
    }

    public static String buildScrollEndpoint() {
        return SimpleElasticsearchRouteConstant.ENDPOINT_SCROLL;
    }

    public static String buildScrollContinueBody(String scrollId, String keepAlive) {
        return "{\"scroll\":\"" + escapeJson(keepAlive) + "\",\"scroll_id\":\"" + escapeJson(scrollId) + "\"}";
    }

    public static String buildScrollClearBody(String scrollId) {
        return "{\"scroll_id\":\"" + escapeJson(scrollId) + "\"}";
    }

    public static String buildClosePitBody(String pitId) {
        return "{\"id\":\"" + escapeJson(pitId) + "\"}";
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
