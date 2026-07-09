package io.github.surezzzzzz.sdk.elasticsearch.route.support;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.VersionCompatibilityErrorPattern;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.ElasticsearchCompatibilityException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.client.Response;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHits;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Elasticsearch 响应兼容 helper
 *
 * @author surezzzzzz
 */
public final class ElasticsearchResponseHelper {

    private ElasticsearchResponseHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static long extractTotalHits(SearchHits searchHits) {
        if (searchHits == null) {
            return 0L;
        }
        Method getTotalHits = ElasticsearchReflectionHelper.loadMethod(searchHits.getClass(),
                SimpleElasticsearchRouteConstant.METHOD_GET_TOTAL_HITS);
        Object totalHits = ElasticsearchReflectionHelper.invoke(getTotalHits, searchHits);
        return extractTotalHitsValue(totalHits);
    }

    public static long extractTotalHits(Map<String, Object> hitsMap) {
        if (hitsMap == null) {
            return 0L;
        }
        Object total = hitsMap.get(SimpleElasticsearchRouteConstant.JSON_FIELD_TOTAL);
        return extractTotalHitsValue(total);
    }

    public static String extractFailureReason(BulkItemResponse.Failure failure) {
        if (failure == null) {
            return null;
        }
        Throwable cause = failure.getCause();
        if (cause != null && cause.getMessage() != null) {
            return cause.getMessage();
        }
        return null;
    }

    public static String extractFailureType(BulkItemResponse.Failure failure) {
        return failure == null ? null : failure.getStatus() == null ? null : failure.getStatus().name();
    }

    public static Integer extractStatusCode(BulkItemResponse.Failure failure) {
        return failure == null ? null : toStatusCode(failure.getStatus());
    }

    public static Integer toStatusCode(RestStatus status) {
        return status == null ? null : status.getStatus();
    }

    public static String toDocWriteResultCode(DocWriteResponse.Result result) {
        return result == null ? null : result.name().toLowerCase();
    }

    public static boolean isNotFound(DocWriteResponse response) {
        return response != null && DocWriteResponse.Result.NOT_FOUND == response.getResult();
    }

    public static boolean isRetryableStatus(Integer status) {
        if (status == null) {
            return false;
        }
        return status == SimpleElasticsearchRouteConstant.HTTP_STATUS_REQUEST_TIMEOUT
                || status == SimpleElasticsearchRouteConstant.HTTP_STATUS_TOO_MANY_REQUESTS
                || status == SimpleElasticsearchRouteConstant.HTTP_STATUS_INTERNAL_SERVER_ERROR
                || status == SimpleElasticsearchRouteConstant.HTTP_STATUS_BAD_GATEWAY
                || status == SimpleElasticsearchRouteConstant.HTTP_STATUS_SERVICE_UNAVAILABLE
                || status == SimpleElasticsearchRouteConstant.HTTP_STATUS_GATEWAY_TIMEOUT;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseResponseBodyAsMap(Response response) throws IOException {
        String body = ElasticsearchLowLevelRequestHelper.readResponseBody(response);
        try {
            Object mapper = ElasticsearchReflectionHelper.newInstance(
                    ElasticsearchReflectionHelper.loadConstructor(
                            ElasticsearchReflectionHelper.loadClass(SimpleElasticsearchRouteConstant.CLASS_JACKSON_OBJECT_MAPPER)));
            Method readValue = ElasticsearchReflectionHelper.loadMethod(mapper.getClass(),
                    SimpleElasticsearchRouteConstant.METHOD_READ_VALUE, String.class, Class.class);
            return (Map<String, Object>) ElasticsearchReflectionHelper.invoke(readValue, mapper, body, Map.class);
        } catch (Exception e) {
            throw new ElasticsearchCompatibilityException(ErrorCode.ROUTE_COMPAT_RESPONSE_READ_FAILED,
                    ErrorMessage.ROUTE_COMPAT_RESPONSE_READ_FAILED, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractMap(Map<String, Object> parent, String key) {
        if (parent == null || key == null) {
            return null;
        }
        Object value = parent.get(key);
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    public static String extractTaskId(String fallbackTaskId, Map<String, Object> taskMap) {
        if (taskMap == null) {
            return fallbackTaskId;
        }
        String node = getAsString(taskMap.get(SimpleElasticsearchRouteConstant.JSON_FIELD_NODE));
        String id = getAsString(taskMap.get(SimpleElasticsearchRouteConstant.JSON_FIELD_ID));
        if (node != null && id != null) {
            return node + ":" + id;
        }
        return fallbackTaskId;
    }

    public static long toLong(Object value) {
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

    public static String getAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    public static String extractFailureCause(Object cause) {
        if (cause == null) {
            return null;
        }
        if (cause instanceof Map) {
            Map<String, Object> causeMap = (Map<String, Object>) cause;
            Object reason = causeMap.get(SimpleElasticsearchRouteConstant.JSON_FIELD_REASON);
            return reason == null ? null : String.valueOf(reason);
        }
        return String.valueOf(cause);
    }

    public static Map<String, Map<String, Object>> extractMappingSources(GetMappingsResponse response) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (response == null) {
            return result;
        }
        response.mappings().forEach(indexEntry -> indexEntry.value.forEach(typeEntry ->
                result.put(indexEntry.key, extractMappingSourceAsMap(typeEntry.value))));
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractMappingSourceAsMap(Object mappingMetadata) {
        if (mappingMetadata == null) {
            return new LinkedHashMap<>();
        }
        try {
            Method method = ElasticsearchReflectionHelper.loadMethod(mappingMetadata.getClass(),
                    SimpleElasticsearchRouteConstant.METHOD_GET_SOURCE_AS_MAP);
            return (Map<String, Object>) ElasticsearchReflectionHelper.invoke(method, mappingMetadata);
        } catch (Exception e) {
            throw new ElasticsearchCompatibilityException(ErrorCode.ROUTE_COMPAT_MAPPING_SOURCE_EXTRACT_FAILED,
                    ErrorMessage.ROUTE_COMPAT_MAPPING_SOURCE_EXTRACT_FAILED, e);
        }
    }

    public static boolean isUnrecognizedParameter(Throwable e) {
        return containsMessage(e, "unrecognized parameter");
    }

    public static boolean isUnsupportedParameter(Throwable e, String... parameterNames) {
        if (parameterNames == null || parameterNames.length == 0) {
            return false;
        }
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                for (String parameterName : parameterNames) {
                    if (parameterName != null && message.contains(parameterName)) {
                        return true;
                    }
                }
            }
            current = current.getCause();
        }
        return false;
    }

    public static boolean isVersionCompatibilityException(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (VersionCompatibilityErrorPattern.isAnyMatch(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static boolean shouldFallbackToLowLevel(Throwable e) {
        return isVersionCompatibilityException(e);
    }

    @SuppressWarnings("unchecked")
    private static long extractTotalHitsValue(Object totalHits) {
        if (totalHits == null) {
            return 0L;
        }
        if (totalHits instanceof Number) {
            return ((Number) totalHits).longValue();
        }
        if (totalHits instanceof Map) {
            return toLong(((Map<String, Object>) totalHits).get(SimpleElasticsearchRouteConstant.JSON_FIELD_VALUE));
        }
        Method valueMethod = ElasticsearchReflectionHelper.findMethod(totalHits.getClass(),
                SimpleElasticsearchRouteConstant.METHOD_VALUE);
        if (valueMethod != null) {
            Object value = ElasticsearchReflectionHelper.invoke(valueMethod, totalHits);
            return toLong(value);
        }
        Object value = ElasticsearchReflectionHelper.getField(totalHits, SimpleElasticsearchRouteConstant.JSON_FIELD_VALUE);
        return toLong(value);
    }

    private static boolean containsMessage(Throwable e, String keyword) {
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(keyword)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
