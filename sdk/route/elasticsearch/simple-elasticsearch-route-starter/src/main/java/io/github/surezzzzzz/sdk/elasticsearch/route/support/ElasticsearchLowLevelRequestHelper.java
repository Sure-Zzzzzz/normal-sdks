package io.github.surezzzzzz.sdk.elasticsearch.route.support;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.ElasticsearchLowLevelRequestException;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.LowLevelSearchResult;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ServerVersion;
import org.apache.http.HttpEntity;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.temporal.TemporalAccessor;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Elasticsearch 底层请求 helper
 *
 * @author surezzzzzz
 */
public final class ElasticsearchLowLevelRequestHelper {

    private ElasticsearchLowLevelRequestHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Request newRequest(String method, String endpoint) {
        return new Request(method, endpoint);
    }

    public static Request newJsonRequest(String method, String endpoint, String jsonBody) {
        Request request = newRequest(method, endpoint);
        setJsonEntity(request, jsonBody);
        return request;
    }

    public static Request newSearchRequest(String[] indices, String jsonBody, String scrollKeepAlive) {
        return newJsonRequest(SimpleElasticsearchRouteConstant.HTTP_METHOD_POST,
                ElasticsearchEndpointHelper.buildSearchEndpoint(indices, scrollKeepAlive), jsonBody);
    }

    public static void addParameter(Request request, String name, Object value) {
        if (request == null || name == null || name.trim().isEmpty() || value == null) {
            return;
        }
        request.addParameter(name, String.valueOf(value));
    }

    public static void addParameters(Request request, Map<String, String> params) {
        if (request == null || params == null || params.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : params.entrySet()) {
            addParameter(request, entry.getKey(), entry.getValue());
        }
    }

    public static void setJsonEntity(Request request, String jsonBody) {
        if (request != null && jsonBody != null && !jsonBody.trim().isEmpty()) {
            request.setJsonEntity(jsonBody);
        }
    }

    public static void applyInitialScrollParam(Request request, String keepAlive) {
        addParameter(request, SimpleElasticsearchRouteConstant.PARAM_SCROLL, keepAlive);
    }

    public static Request buildIndexExistsRequest(String index) {
        return newRequest(SimpleElasticsearchRouteConstant.HTTP_METHOD_HEAD,
                ElasticsearchEndpointHelper.buildIndexEndpoint(index));
    }

    public static Request buildCreateIndexRequest(String index) {
        return newRequest(SimpleElasticsearchRouteConstant.HTTP_METHOD_PUT,
                ElasticsearchEndpointHelper.buildIndexEndpoint(index));
    }

    public static Request buildCreateIndexRequest(String index, String mappingJson, ClusterInfo clusterInfo) {
        Request request = buildCreateIndexRequest(index);
        setJsonEntity(request, adaptCreateIndexBody(mappingJson, clusterInfo));
        return request;
    }

    public static Request buildCreateIndexRequest(String index, Map<String, Object> properties, ClusterInfo clusterInfo) {
        return buildCreateIndexRequest(index, buildMappingBody(properties), clusterInfo);
    }

    public static String buildMappingBody(Map<String, Object> properties) {
        return "{\"properties\":" + toJson(properties == null ? new LinkedHashMap<String, Object>() : properties) + "}";
    }

    public static Request buildIndexDocRequest(String index, String id, Map<String, Object> source) {
        return buildIndexDocRequest(index, id, source, true);
    }

    public static Request buildIndexDocRequest(String index, String id, Map<String, Object> source, boolean refresh) {
        Request request = newJsonRequest(SimpleElasticsearchRouteConstant.HTTP_METHOD_PUT,
                ElasticsearchEndpointHelper.buildDocEndpoint(index, id), toJson(normalizeSource(source)));
        if (refresh) {
            addParameter(request, SimpleElasticsearchRouteConstant.PARAM_REFRESH,
                    SimpleElasticsearchRouteConstant.PARAM_VALUE_TRUE);
        }
        return request;
    }

    public static Request buildDeleteDocRequest(String index, String id) {
        return buildDeleteDocRequest(index, id, true);
    }

    public static Request buildDeleteDocRequest(String index, String id, boolean refresh) {
        Request request = newRequest(SimpleElasticsearchRouteConstant.HTTP_METHOD_DELETE,
                ElasticsearchEndpointHelper.buildDocEndpoint(index, id));
        if (refresh) {
            addParameter(request, SimpleElasticsearchRouteConstant.PARAM_REFRESH,
                    SimpleElasticsearchRouteConstant.PARAM_VALUE_TRUE);
        }
        return request;
    }

    public static Request buildDocExistsRequest(String index, String id) {
        return newRequest(SimpleElasticsearchRouteConstant.HTTP_METHOD_HEAD,
                ElasticsearchEndpointHelper.buildDocEndpoint(index, id));
    }

    public static Request buildGetDocRequest(String index, String id) {
        return newRequest(SimpleElasticsearchRouteConstant.HTTP_METHOD_GET,
                ElasticsearchEndpointHelper.buildDocEndpoint(index, id));
    }

    public static Request buildRefreshRequest(String index) {
        return newRequest(SimpleElasticsearchRouteConstant.HTTP_METHOD_POST,
                ElasticsearchEndpointHelper.buildRefreshEndpoint(index));
    }

    public static String adaptCreateIndexBody(String mappingJson, ClusterInfo clusterInfo) {
        if (mappingJson == null || mappingJson.trim().isEmpty()) {
            return null;
        }
        ServerVersion version = clusterInfo == null ? null : clusterInfo.getEffectiveVersion();
        if (version != null && version.isEs6()) {
            return "{\"mappings\":{\"" + SimpleElasticsearchRouteConstant.MAPPING_TYPE_DOC + "\":"
                    + mappingJson + "}}";
        }
        return "{\"mappings\":" + mappingJson + "}";
    }

    public static boolean indexExists(RestClient client, String index) {
        Request request = buildIndexExistsRequest(index);
        try {
            Response response = client.performRequest(request);
            return response.getStatusLine().getStatusCode() == SimpleElasticsearchRouteConstant.HTTP_STATUS_OK;
        } catch (ResponseException e) {
            if (isNotFound(e)) {
                return false;
            }
            throw lowLevelRequestException(request, e);
        } catch (IOException e) {
            throw lowLevelRequestException(request, e);
        }
    }

    public static void createIndex(RestClient client, String index) {
        executeQuietly(client, buildCreateIndexRequest(index));
    }

    public static void createIndex(RestClient client, String index, String mappingJson, ClusterInfo clusterInfo) {
        executeQuietly(client, buildCreateIndexRequest(index, mappingJson, clusterInfo));
    }

    public static void createIndex(RestClient client, String index, Map<String, Object> properties, ClusterInfo clusterInfo) {
        executeQuietly(client, buildCreateIndexRequest(index, properties, clusterInfo));
    }

    public static void recreateIndex(RestClient client, String index, String mappingJson, ClusterInfo clusterInfo) {
        if (indexExists(client, index)) {
            deleteIndex(client, index);
        }
        createIndex(client, index, mappingJson, clusterInfo);
    }

    public static void recreateIndex(RestClient client, String index, Map<String, Object> properties, ClusterInfo clusterInfo) {
        recreateIndex(client, index, buildMappingBody(properties), clusterInfo);
    }

    public static void deleteIndex(RestClient client, String index) {
        executeIgnoringNotFound(client, newRequest(SimpleElasticsearchRouteConstant.HTTP_METHOD_DELETE,
                ElasticsearchEndpointHelper.buildIndexEndpoint(index)));
    }

    public static void refreshIndex(RestClient client, String index) {
        executeQuietly(client, buildRefreshRequest(index));
    }

    public static void refresh(RestClient client, String index) {
        refreshIndex(client, index);
    }

    public static void indexDoc(RestClient client, String index, String id, Map<String, Object> source) {
        executeQuietly(client, buildIndexDocRequest(index, id, source));
    }

    public static void deleteDoc(RestClient client, String index, String id) {
        executeIgnoringNotFound(client, buildDeleteDocRequest(index, id));
    }

    public static boolean docExists(RestClient client, String index, String id) {
        Request request = buildDocExistsRequest(index, id);
        try {
            Response response = client.performRequest(request);
            return response.getStatusLine().getStatusCode() == SimpleElasticsearchRouteConstant.HTTP_STATUS_OK;
        } catch (ResponseException e) {
            if (isNotFound(e)) {
                return false;
            }
            throw lowLevelRequestException(request, e);
        } catch (IOException e) {
            throw lowLevelRequestException(request, e);
        }
    }

    public static Map<String, Object> getDoc(RestClient client, String index, String id) {
        Request request = buildGetDocRequest(index, id);
        try {
            Response response = client.performRequest(request);
            if (response.getStatusLine().getStatusCode() == SimpleElasticsearchRouteConstant.HTTP_STATUS_NOT_FOUND) {
                return null;
            }
            return ElasticsearchResponseHelper.extractMap(ElasticsearchResponseHelper.parseResponseBodyAsMap(response),
                    SimpleElasticsearchRouteConstant.JSON_FIELD_SOURCE);
        } catch (ResponseException e) {
            if (isNotFound(e)) {
                return null;
            }
            throw lowLevelRequestException(request, e);
        } catch (IOException e) {
            throw lowLevelRequestException(request, e);
        }
    }

    public static Response execute(RestClient client, Request request) throws IOException {
        try {
            return client.performRequest(request);
        } catch (IOException e) {
            throw lowLevelRequestException(request, e);
        }
    }

    public static Response execute(RestClient client, String method, String endpoint,
                                   String jsonBody, Map<String, String> params) throws IOException {
        Request request = newJsonRequest(method, endpoint, jsonBody);
        addParameters(request, params);
        return execute(client, request);
    }

    public static LowLevelSearchResult executeSearch(RestClient client,
                                                     SearchRequest searchRequest,
                                                     boolean rawAggregationResponseWhenAggregationsPresent) throws IOException {
        String scrollKeepAlive = null;
        if (searchRequest != null && searchRequest.scroll() != null && searchRequest.scroll().keepAlive() != null) {
            scrollKeepAlive = searchRequest.scroll().keepAlive().getStringRep();
        }
        String jsonBody = searchRequest != null && searchRequest.source() != null ? searchRequest.source().toString() : null;
        String[] indices = searchRequest == null ? null : searchRequest.indices();
        Request request = newSearchRequest(indices, jsonBody, scrollKeepAlive);
        if (searchRequest != null) {
            ElasticsearchRequestOptionHelper.applyIndicesOptions(request, searchRequest.indicesOptions());
        }

        Response response = execute(client, request);
        byte[] responseBytes = readResponseBytes(response);
        String responseBody = new String(responseBytes, StandardCharsets.UTF_8);
        boolean containsAggregations = containsAggregations(responseBody);
        if (containsAggregations && rawAggregationResponseWhenAggregationsPresent) {
            return LowLevelSearchResult.builder()
                    .rawResponseBody(responseBody)
                    .containsAggregations(true)
                    .rawAggregationResponse(true)
                    .build();
        }
        return LowLevelSearchResult.builder()
                .searchResponse(XContentCompatibilityHelper.parseSearchResponse(responseBytes))
                .rawResponseBody(responseBody)
                .containsAggregations(containsAggregations)
                .rawAggregationResponse(false)
                .build();
    }

    public static long executeCount(RestClient client,
                                    String[] indices,
                                    String queryJson,
                                    IndicesOptions indicesOptions) throws IOException {
        Request request = newJsonRequest(SimpleElasticsearchRouteConstant.HTTP_METHOD_POST,
                ElasticsearchEndpointHelper.buildCountEndpoint(indices), queryJson);
        ElasticsearchRequestOptionHelper.applyIndicesOptions(request, indicesOptions);
        Response response = execute(client, request);
        return XContentCompatibilityHelper.parseCountResponse(response);
    }

    public static <T> T executeMapping(RestClient client,
                                       String index,
                                       IndicesOptions indicesOptions,
                                       Class<T> responseClass) throws IOException {
        Request request = newRequest(SimpleElasticsearchRouteConstant.HTTP_METHOD_GET,
                ElasticsearchEndpointHelper.buildMappingEndpoint(index));
        ElasticsearchRequestOptionHelper.applyIndicesOptions(request, indicesOptions);
        Response response = execute(client, request);
        return XContentCompatibilityHelper.parseResponse(response, responseClass);
    }

    public static boolean containsAggregations(String responseBody) {
        return responseBody != null && responseBody.contains("\""
                + SimpleElasticsearchRouteConstant.JSON_FIELD_AGGREGATIONS + "\"");
    }

    public static byte[] readResponseBytes(Response response) throws IOException {
        if (response == null || response.getEntity() == null) {
            return new byte[0];
        }
        try {
            return readEntityBytes(response.getEntity());
        } catch (IOException e) {
            throw new ElasticsearchLowLevelRequestException(ErrorCode.ROUTE_COMPAT_RESPONSE_READ_FAILED,
                    ErrorMessage.ROUTE_COMPAT_RESPONSE_READ_FAILED, e);
        }
    }

    public static String readResponseBody(Response response) throws IOException {
        return new String(readResponseBytes(response), StandardCharsets.UTF_8);
    }

    public static String readEntity(HttpEntity entity) throws IOException {
        return new String(readEntityBytes(entity), StandardCharsets.UTF_8);
    }

    private static void executeQuietly(RestClient client, Request request) {
        try {
            client.performRequest(request);
        } catch (IOException e) {
            throw lowLevelRequestException(request, e);
        }
    }

    private static void executeIgnoringNotFound(RestClient client, Request request) {
        try {
            client.performRequest(request);
        } catch (ResponseException e) {
            if (isNotFound(e)) {
                return;
            }
            throw lowLevelRequestException(request, e);
        } catch (IOException e) {
            throw lowLevelRequestException(request, e);
        }
    }

    private static boolean isNotFound(ResponseException e) {
        return e != null && e.getResponse() != null
                && e.getResponse().getStatusLine().getStatusCode()
                == SimpleElasticsearchRouteConstant.HTTP_STATUS_NOT_FOUND;
    }

    private static ElasticsearchLowLevelRequestException lowLevelRequestException(Request request, IOException e) {
        String method = request == null ? "" : request.getMethod();
        String endpoint = request == null ? "" : request.getEndpoint();
        return new ElasticsearchLowLevelRequestException(ErrorCode.ROUTE_COMPAT_LOW_LEVEL_REQUEST_FAILED,
                String.format(ErrorMessage.ROUTE_COMPAT_LOW_LEVEL_REQUEST_FAILED, method, endpoint), e);
    }

    private static Map<String, Object> normalizeSource(Map<String, Object> source) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (source == null) {
            return normalized;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof TemporalAccessor) {
                normalized.put(entry.getKey(), value.toString());
            } else {
                normalized.put(entry.getKey(), value);
            }
        }
        return normalized;
    }

    private static String toJson(Object value) {
        try {
            Object mapper = ElasticsearchReflectionHelper.newInstance(
                    ElasticsearchReflectionHelper.loadConstructor(
                            ElasticsearchReflectionHelper.loadClass(SimpleElasticsearchRouteConstant.CLASS_JACKSON_OBJECT_MAPPER)));
            Method writeValueAsString = ElasticsearchReflectionHelper.loadMethod(mapper.getClass(),
                    SimpleElasticsearchRouteConstant.METHOD_WRITE_VALUE_AS_STRING, Object.class);
            return (String) ElasticsearchReflectionHelper.invoke(writeValueAsString, mapper, value);
        } catch (Exception e) {
            throw new ElasticsearchLowLevelRequestException(ErrorCode.ROUTE_COMPAT_JSON_SERIALIZE_FAILED,
                    ErrorMessage.ROUTE_COMPAT_JSON_SERIALIZE_FAILED, e);
        }
    }

    private static byte[] readEntityBytes(HttpEntity entity) throws IOException {
        if (entity == null) {
            return new byte[0];
        }
        try (InputStream inputStream = entity.getContent();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] data = new byte[SimpleElasticsearchRouteConstant.HTTP_READ_BUFFER_SIZE];
            int read;
            while ((read = inputStream.read(data)) != -1) {
                buffer.write(data, 0, read);
            }
            return buffer.toByteArray();
        }
    }
}
