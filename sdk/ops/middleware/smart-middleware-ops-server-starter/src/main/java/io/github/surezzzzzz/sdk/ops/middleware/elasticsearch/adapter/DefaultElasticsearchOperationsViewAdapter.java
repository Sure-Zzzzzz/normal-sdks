package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.ops.middleware.constant.SmartMiddlewareOpsServerConstant;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.catalog.ElasticsearchIndexListResponse;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.document.ElasticsearchDocumentQueryRequest;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.document.ElasticsearchDocumentQueryResponse;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.field.ElasticsearchFieldCapabilitiesRequest;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.field.ElasticsearchFieldCapabilitiesResponse;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.summary.ElasticsearchSummaryResponse;
import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.support.MiddlewareOpsLogHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.elasticsearch.client.*;
import org.springframework.http.HttpStatus;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 仅通过 Elasticsearch Route Registry 获取安全视图的适配器。
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultElasticsearchOperationsViewAdapter implements ElasticsearchOperationsViewAdapter {

    private final SimpleElasticsearchRouteRegistry registry;
    private final long deadlineMillis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建 Elasticsearch Route 适配器。
     *
     * @param registry       Elasticsearch Route Registry
     * @param deadlineMillis 单次查询截止时间
     */
    public DefaultElasticsearchOperationsViewAdapter(SimpleElasticsearchRouteRegistry registry, long deadlineMillis) {
        this.registry = registry;
        this.deadlineMillis = deadlineMillis;
    }

    @Override
    public ElasticsearchSummaryResponse getSummary(String datasourceKey) {
        ClusterInfo info = registry.getClusterInfo(datasourceKey);
        if (info == null) {
            throw new MiddlewareOpsException(HttpStatus.NOT_FOUND, "目标数据源不存在");
        }
        return ElasticsearchSummaryResponse.builder().datasourceKey(info.getDatasourceKey())
                .configuredVersion(valueOf(info.getConfiguredVersion())).detectedVersion(valueOf(info.getDetectedVersion()))
                .effectiveVersion(valueOf(info.getEffectiveVersion())).versionMismatch(info.isVersionMismatch())
                .detected(info.getDetectedAtMillis() != null).build();
    }

    @Override
    public ElasticsearchIndexListResponse listIndices(String datasourceKey) {
        if (registry.getClusterInfo(datasourceKey) == null) {
            throw new MiddlewareOpsException(HttpStatus.NOT_FOUND, "目标数据源不存在");
        }
        Request request = new Request("GET", "/_aliases");
        request.addParameter("expand_wildcards", "open");
        try {
            return parseIndexList(datasourceKey, awaitMetadata(datasourceKey, request));
        } catch (MiddlewareOpsException e) {
            logFailure(datasourceKey, request, e);
            throw e;
        }
    }

    @Override
    public ElasticsearchFieldCapabilitiesResponse getFieldCapabilities(ElasticsearchFieldCapabilitiesRequest request) {
        if (registry.getClusterInfo(request.getDatasourceKey()) == null) {
            throw new MiddlewareOpsException(HttpStatus.NOT_FOUND, "目标数据源不存在");
        }
        Request fieldCapabilitiesRequest = new Request("GET", "/" + request.getIndex() + "/_field_caps");
        fieldCapabilitiesRequest.addParameter("fields", "*");
        try {
            return parseFieldCapabilities(request.getDatasourceKey(), request.getIndex(),
                    awaitMetadata(request.getDatasourceKey(), fieldCapabilitiesRequest));
        } catch (MiddlewareOpsException e) {
            logFailure(request.getDatasourceKey(), fieldCapabilitiesRequest, e);
            throw e;
        }
    }

    @Override
    public ElasticsearchDocumentQueryResponse queryDocuments(ElasticsearchDocumentQueryRequest request) {
        if (registry.getClusterInfo(request.getDatasourceKey()) == null) {
            throw new MiddlewareOpsException(HttpStatus.NOT_FOUND, "目标数据源不存在");
        }
        Request searchRequest = new Request("POST", "/" + request.getIndex() + "/_search");
        searchRequest.addParameter("expand_wildcards", "open");
        searchRequest.setJsonEntity(buildSearchBody(request));
        try {
            return parseDocumentQueryResponse(awaitMetadata(request.getDatasourceKey(), searchRequest));
        } catch (MiddlewareOpsException e) {
            logFailure(request.getDatasourceKey(), searchRequest, e);
            throw e;
        }
    }

    private String buildSearchBody(ElasticsearchDocumentQueryRequest request) {
        return request.getDsl();
    }

    private ElasticsearchDocumentQueryResponse parseDocumentQueryResponse(Response response) {
        try (InputStream content = entityContent(response.getEntity(),
                SmartMiddlewareOpsServerConstant.MAX_ELASTICSEARCH_DOCUMENT_RESPONSE_LENGTH)) {
            JsonNode root = objectMapper.readTree(content);
            if (root == null || !root.isObject()) {
                throw new IOException("文档查询响应格式无效");
            }
            return new ElasticsearchDocumentQueryResponse(root);
        } catch (IOException | RuntimeException e) {
            throw new MiddlewareOpsException(HttpStatus.SERVICE_UNAVAILABLE, "Elasticsearch 运维查询暂不可用", e);
        }
    }

    private ElasticsearchIndexListResponse parseIndexList(String datasourceKey, Response response) {
        try {
            return parseIndexList(datasourceKey, entityContent(response.getEntity()));
        } catch (IOException e) {
            throw new MiddlewareOpsException(HttpStatus.SERVICE_UNAVAILABLE, "Elasticsearch 运维查询暂不可用", e);
        }
    }

    ElasticsearchIndexListResponse parseIndexList(String datasourceKey, InputStream input) {
        TreeSet<String> names = new TreeSet<>();
        boolean truncated = false;
        try (InputStream content = input) {
            JsonNode root = objectMapper.readTree(content);
            if (root == null || !root.isObject()) {
                throw new IOException("索引目录响应格式无效");
            }
            Iterator<String> fields = root.fieldNames();
            while (fields.hasNext()) {
                String name = fields.next();
                if (isHidden(name)) {
                    continue;
                }
                names.add(name);
                if (names.size() > SmartMiddlewareOpsServerConstant.MAX_ELASTICSEARCH_INDEX_LIST_SIZE) {
                    names.pollLast();
                    truncated = true;
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new MiddlewareOpsException(HttpStatus.SERVICE_UNAVAILABLE, "Elasticsearch 运维查询暂不可用", e);
        }
        return ElasticsearchIndexListResponse.builder().datasourceKey(datasourceKey)
                .items(Collections.unmodifiableList(new ArrayList<>(names))).truncated(truncated).build();
    }

    private ElasticsearchFieldCapabilitiesResponse parseFieldCapabilities(String datasourceKey, String index,
                                                                          Response response) {
        try {
            return parseFieldCapabilities(datasourceKey, index, entityContent(response.getEntity(),
                    SmartMiddlewareOpsServerConstant.MAX_ELASTICSEARCH_FIELD_CAPABILITIES_RESPONSE_LENGTH));
        } catch (IOException e) {
            throw new MiddlewareOpsException(HttpStatus.SERVICE_UNAVAILABLE, "Elasticsearch 字段补全暂不可用", e);
        }
    }

    ElasticsearchFieldCapabilitiesResponse parseFieldCapabilities(String datasourceKey, String index, InputStream input) {
        TreeMap<String, ElasticsearchFieldCapabilitiesResponse.Item> fields = new TreeMap<>();
        boolean truncated = false;
        try (InputStream content = input) {
            JsonNode root = objectMapper.readTree(content);
            JsonNode fieldNodes = root == null ? null : root.get("fields");
            if (fieldNodes == null || !fieldNodes.isObject()) {
                throw new IOException("字段能力响应格式无效");
            }
            Iterator<Map.Entry<String, JsonNode>> values = fieldNodes.fields();
            while (values.hasNext()) {
                Map.Entry<String, JsonNode> value = values.next();
                String name = value.getKey();
                if (isInternalField(name)) {
                    continue;
                }
                ElasticsearchFieldCapabilitiesResponse.Item item = toFieldCapability(name, value.getValue());
                if (item == null) {
                    continue;
                }
                fields.put(name, item);
                if (fields.size() > SmartMiddlewareOpsServerConstant.MAX_ELASTICSEARCH_FIELD_CAPABILITIES_SIZE) {
                    fields.pollLastEntry();
                    truncated = true;
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new MiddlewareOpsException(HttpStatus.SERVICE_UNAVAILABLE, "Elasticsearch 字段补全暂不可用", e);
        }
        return ElasticsearchFieldCapabilitiesResponse.builder().datasourceKey(datasourceKey).index(index)
                .items(Collections.unmodifiableList(new ArrayList<>(fields.values()))).truncated(truncated).build();
    }

    private ElasticsearchFieldCapabilitiesResponse.Item toFieldCapability(String name, JsonNode types) {
        if (types == null || !types.isObject()) {
            return null;
        }
        TreeSet<String> names = new TreeSet<>();
        boolean searchable = false;
        boolean aggregatable = false;
        Iterator<Map.Entry<String, JsonNode>> entries = types.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            JsonNode capability = entry.getValue();
            if (capability == null || !capability.isObject()) {
                continue;
            }
            names.add(entry.getKey());
            searchable |= capability.path("searchable").asBoolean(false);
            aggregatable |= capability.path("aggregatable").asBoolean(false);
        }
        if (names.isEmpty() || (!searchable && !aggregatable)) {
            return null;
        }
        return ElasticsearchFieldCapabilitiesResponse.Item.builder().name(name)
                .types(Collections.unmodifiableList(new ArrayList<>(names))).searchable(searchable)
                .aggregatable(aggregatable).build();
    }

    private InputStream entityContent(HttpEntity entity) throws IOException {
        return entityContent(entity, SmartMiddlewareOpsServerConstant.MAX_ELASTICSEARCH_INDEX_RESPONSE_LENGTH);
    }

    private InputStream entityContent(HttpEntity entity, int maxLength) throws IOException {
        if (entity == null || entity.getContent() == null) {
            throw new IOException("Elasticsearch 响应为空");
        }
        return new BoundedInputStream(entity.getContent(), maxLength);
    }

    private boolean isHidden(String name) {
        return name == null || name.startsWith(".");
    }

    private boolean isInternalField(String name) {
        return name == null || name.startsWith("_");
    }

    private Response awaitMetadata(String datasourceKey, Request request) {
        CompletableFuture<Response> future = new CompletableFuture<>();
        AtomicReference<Cancellable> cancellable = new AtomicReference<>();
        try {
            RestClient client = registry.getLowLevelClient(datasourceKey);
            cancellable.set(client.performRequestAsync(request, new ResponseListener() {
                @Override
                public void onSuccess(Response response) {
                    future.complete(response);
                }

                @Override
                public void onFailure(Exception exception) {
                    future.completeExceptionally(exception);
                }
            }));
            return future.get(deadlineMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            Cancellable current = cancellable.get();
            if (current != null) {
                current.cancel();
            }
            throw new MiddlewareOpsException(HttpStatus.GATEWAY_TIMEOUT, "Elasticsearch 运维查询已超时", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MiddlewareOpsException(HttpStatus.SERVICE_UNAVAILABLE, "Elasticsearch 运维查询暂不可用", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new MiddlewareOpsException(HttpStatus.SERVICE_UNAVAILABLE, "Elasticsearch 运维查询暂不可用", cause);
        } catch (RuntimeException e) {
            throw new MiddlewareOpsException(HttpStatus.SERVICE_UNAVAILABLE, "Elasticsearch 运维查询暂不可用", e);
        }
    }

    private void logFailure(String datasourceKey, Request request, MiddlewareOpsException exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        log.warn("Elasticsearch 运维查询失败，datasourceKey={}，method={}，path={}，status={}，causeType={}",
                MiddlewareOpsLogHelper.identifier(datasourceKey), request.getMethod(), request.getEndpoint(),
                downstreamStatus(cause), cause.getClass().getName(),
                MiddlewareOpsLogHelper.sanitizedThrowable(cause));
    }

    private Integer downstreamStatus(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ResponseException) {
                Response response = ((ResponseException) current).getResponse();
                return response == null || response.getStatusLine() == null ? null : response.getStatusLine().getStatusCode();
            }
            current = current.getCause();
        }
        return null;
    }

    private String valueOf(Object value) {
        return value == null ? null : value.toString();
    }

    private static class BoundedInputStream extends FilterInputStream {

        private final int maxLength;
        private int length;

        private BoundedInputStream(InputStream input, int maxLength) {
            super(input);
            this.maxLength = maxLength;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value != -1) {
                incrementLength(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int count) throws IOException {
            int read = super.read(buffer, offset, count);
            if (read > 0) {
                incrementLength(read);
            }
            return read;
        }

        private void incrementLength(int increment) throws IOException {
            length += increment;
            if (length > maxLength) {
                throw new IOException("Elasticsearch 响应超过允许大小");
            }
        }
    }
}
