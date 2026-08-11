package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchReflectionHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.XContentCompatibilityHelper;
import io.github.surezzzzzz.sdk.ops.middleware.constant.SmartMiddlewareOpsServerConstant;
import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import org.apache.http.HttpEntity;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.http.HttpStatus;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
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
public class DefaultElasticsearchOperationsViewAdapter implements ElasticsearchOperationsViewAdapter {

    private final SimpleElasticsearchRouteRegistry registry;
    private final long deadlineMillis;
    private final int maxResponseLength;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建 Elasticsearch Route 适配器。
     *
     * @param registry          Elasticsearch Route Registry
     * @param deadlineMillis    单次查询截止时间
     * @param maxResponseLength 单条命中 source 最大字符数
     */
    public DefaultElasticsearchOperationsViewAdapter(SimpleElasticsearchRouteRegistry registry, long deadlineMillis,
                                                     int maxResponseLength) {
        this.registry = registry;
        this.deadlineMillis = deadlineMillis;
        this.maxResponseLength = maxResponseLength;
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
        Response response = awaitIndexList(datasourceKey, request);
        return parseIndexList(datasourceKey, response);
    }

    @Override
    public ElasticsearchDocumentQueryResponse queryDocuments(ElasticsearchDocumentQueryRequest request) {
        if (registry.getClusterInfo(request.getDatasourceKey()) == null) {
            throw new MiddlewareOpsException(HttpStatus.NOT_FOUND, "目标数据源不存在");
        }
        SearchSourceBuilder source = parseSource(request.getDsl());
        source.from((request.getPage() - 1) * request.getSize());
        source.size(request.getSize() + 1);
        SearchResponse response = awaitSearch(request.getDatasourceKey(), new SearchRequest(request.getIndex()).source(source));
        List<ElasticsearchDocumentQueryResponse.Hit> items = new ArrayList<>();
        SearchHit[] hits = response.getHits().getHits();
        int limit = Math.min(request.getSize(), hits.length);
        for (int index = 0; index < limit; index++) {
            SearchHit hit = hits[index];
            items.add(ElasticsearchDocumentQueryResponse.Hit.builder().index(hit.getIndex()).id(hit.getId())
                    .source(limitSource(hit.getSourceAsMap())).build());
        }
        return ElasticsearchDocumentQueryResponse.builder().page(request.getPage()).size(request.getSize())
                .items(items).hasMore(hits.length > request.getSize()).build();
    }

    private ElasticsearchIndexListResponse parseIndexList(String datasourceKey, Response response) {
        try {
            return parseIndexList(datasourceKey, entityContent(response.getEntity()));
        } catch (IOException e) {
            throw new MiddlewareOpsException(HttpStatus.SERVICE_UNAVAILABLE, "Elasticsearch 运维查询暂不可用");
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
            throw new MiddlewareOpsException(HttpStatus.SERVICE_UNAVAILABLE, "Elasticsearch 运维查询暂不可用");
        }
        return ElasticsearchIndexListResponse.builder().datasourceKey(datasourceKey)
                .items(Collections.unmodifiableList(new ArrayList<>(names))).truncated(truncated).build();
    }

    private InputStream entityContent(HttpEntity entity) throws IOException {
        if (entity == null || entity.getContent() == null) {
            throw new IOException("索引目录响应为空");
        }
        return new BoundedInputStream(entity.getContent(),
                SmartMiddlewareOpsServerConstant.MAX_ELASTICSEARCH_INDEX_RESPONSE_LENGTH);
    }

    private boolean isHidden(String name) {
        return name == null || name.startsWith(".");
    }

    private Response awaitIndexList(String datasourceKey, Request request) {
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
            throw new MiddlewareOpsException(HttpStatus.GATEWAY_TIMEOUT, "Elasticsearch 运维查询已超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MiddlewareOpsException(HttpStatus.SERVICE_UNAVAILABLE, "Elasticsearch 运维查询暂不可用");
        } catch (ExecutionException | RuntimeException e) {
            throw new MiddlewareOpsException(HttpStatus.SERVICE_UNAVAILABLE, "Elasticsearch 运维查询暂不可用");
        }
    }

    private SearchResponse awaitSearch(String datasourceKey, SearchRequest request) {
        CompletableFuture<SearchResponse> future = new CompletableFuture<>();
        AtomicReference<Cancellable> cancellable = new AtomicReference<>();
        try {
            cancellable.set(registry.getHighLevelClient(datasourceKey).searchAsync(request,
                    org.elasticsearch.client.RequestOptions.DEFAULT, new ActionListener<SearchResponse>() {
                        @Override
                        public void onResponse(SearchResponse response) {
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
            throw new MiddlewareOpsException(HttpStatus.GATEWAY_TIMEOUT, "Elasticsearch 运维查询已超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MiddlewareOpsException(HttpStatus.SERVICE_UNAVAILABLE, "Elasticsearch 运维查询暂不可用");
        } catch (ExecutionException | RuntimeException e) {
            throw new MiddlewareOpsException(HttpStatus.SERVICE_UNAVAILABLE, "Elasticsearch 运维查询暂不可用");
        }
    }

    private SearchSourceBuilder parseSource(String dsl) {
        Object parser = null;
        try {
            parser = XContentCompatibilityHelper.createParser(dsl.getBytes(StandardCharsets.UTF_8));
            SearchSourceBuilder source = new SearchSourceBuilder();
            ElasticsearchReflectionHelper.invoke(parseXContentMethod(parser), source, parser);
            return source;
        } catch (RuntimeException e) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "JSON DSL 不符合 Elasticsearch 查询规范");
        } finally {
            XContentCompatibilityHelper.closeParser(parser);
        }
    }

    private Method parseXContentMethod(Object parser) {
        for (Method method : SearchSourceBuilder.class.getMethods()) {
            if ("parseXContent".equals(method.getName()) && method.getParameterTypes().length == 1
                    && method.getParameterTypes()[0].isAssignableFrom(parser.getClass())) {
                return method;
            }
        }
        throw new IllegalStateException("Elasticsearch DSL 解析器不可用");
    }

    private Map<String, Object> limitSource(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        int remaining = maxResponseLength;
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (remaining <= 0) {
                break;
            }
            String value = String.valueOf(entry.getValue());
            int length = Math.min(value.length(), remaining);
            result.put(entry.getKey(), value.length() == length ? entry.getValue() : value.substring(0, length));
            remaining -= entry.getKey().length() + length;
        }
        return result;
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
                throw new IOException("索引目录响应过大");
            }
        }
    }
}
