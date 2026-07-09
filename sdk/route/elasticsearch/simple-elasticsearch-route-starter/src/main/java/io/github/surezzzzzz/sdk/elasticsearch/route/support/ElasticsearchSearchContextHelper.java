package io.github.surezzzzzz.sdk.elasticsearch.route.support;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;
import org.elasticsearch.client.Request;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Elasticsearch 搜索上下文 helper
 *
 * @author surezzzzzz
 */
public final class ElasticsearchSearchContextHelper {

    private ElasticsearchSearchContextHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean isPointInTimeBuilderAvailable() {
        return ElasticsearchReflectionHelper.isClassPresent(SimpleElasticsearchRouteConstant.CLASS_POINT_IN_TIME_BUILDER);
    }

    public static boolean supportsPointInTime(ClusterInfo clusterInfo) {
        return ElasticsearchVersionHelper.supportsPointInTime(clusterInfo);
    }

    public static boolean applyPointInTime(SearchSourceBuilder sourceBuilder, String pitId, String keepAlive) {
        if (sourceBuilder == null || pitId == null || pitId.trim().isEmpty()) {
            return false;
        }
        Class<?> pitClass = ElasticsearchReflectionHelper.findClass(SimpleElasticsearchRouteConstant.CLASS_POINT_IN_TIME_BUILDER);
        if (pitClass == null) {
            return false;
        }
        Constructor<?> constructor = ElasticsearchReflectionHelper.findConstructor(pitClass, String.class);
        Method setKeepAlive = ElasticsearchReflectionHelper.findMethod(pitClass,
                SimpleElasticsearchRouteConstant.METHOD_SET_KEEP_ALIVE, String.class);
        Method pitMethod = ElasticsearchReflectionHelper.findMethod(SearchSourceBuilder.class,
                SimpleElasticsearchRouteConstant.METHOD_POINT_IN_TIME_BUILDER, pitClass);
        if (constructor == null || setKeepAlive == null || pitMethod == null) {
            return false;
        }
        Object pitBuilder = ElasticsearchReflectionHelper.newInstance(constructor, pitId);
        if (keepAlive != null && !keepAlive.trim().isEmpty()) {
            ElasticsearchReflectionHelper.invoke(setKeepAlive, pitBuilder, keepAlive);
        }
        ElasticsearchReflectionHelper.invoke(pitMethod, sourceBuilder, pitBuilder);
        return true;
    }

    public static Request buildOpenPitRequest(String index, String keepAlive) {
        return ElasticsearchLowLevelRequestHelper.newRequest(SimpleElasticsearchRouteConstant.HTTP_METHOD_POST,
                ElasticsearchEndpointHelper.buildOpenPitEndpoint(index, keepAlive));
    }

    public static Request buildClosePitRequest(String pitId) {
        return ElasticsearchLowLevelRequestHelper.newJsonRequest(SimpleElasticsearchRouteConstant.HTTP_METHOD_DELETE,
                ElasticsearchEndpointHelper.buildClosePitEndpoint(), ElasticsearchEndpointHelper.buildClosePitBody(pitId));
    }

    public static Request buildInitialScrollSearchRequest(String[] indices, String jsonBody, String keepAlive) {
        return ElasticsearchLowLevelRequestHelper.newSearchRequest(indices, jsonBody, keepAlive);
    }

    public static Request buildScrollContinueRequest(String scrollId, String keepAlive) {
        return ElasticsearchLowLevelRequestHelper.newJsonRequest(SimpleElasticsearchRouteConstant.HTTP_METHOD_POST,
                ElasticsearchEndpointHelper.buildScrollEndpoint(),
                ElasticsearchEndpointHelper.buildScrollContinueBody(scrollId, keepAlive));
    }

    public static Request buildScrollClearRequest(String scrollId) {
        return ElasticsearchLowLevelRequestHelper.newJsonRequest(SimpleElasticsearchRouteConstant.HTTP_METHOD_DELETE,
                ElasticsearchEndpointHelper.buildScrollEndpoint(), ElasticsearchEndpointHelper.buildScrollClearBody(scrollId));
    }

    public static String extractPitId(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = parseJsonMap(responseBody);
            Object id = map.get(SimpleElasticsearchRouteConstant.JSON_FIELD_ID);
            return id == null ? null : String.valueOf(id);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJsonMap(String json) {
        Object mapper = ElasticsearchReflectionHelper.newInstance(
                ElasticsearchReflectionHelper.loadConstructor(
                        ElasticsearchReflectionHelper.loadClass(SimpleElasticsearchRouteConstant.CLASS_JACKSON_OBJECT_MAPPER)));
        Method readValue = ElasticsearchReflectionHelper.loadMethod(mapper.getClass(),
                SimpleElasticsearchRouteConstant.METHOD_READ_VALUE, String.class, Class.class);
        return (Map<String, Object>) ElasticsearchReflectionHelper.invoke(readValue, mapper, json, Map.class);
    }
}
