package io.github.surezzzzzz.sdk.elasticsearch.search.query.pagination;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.QueryException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.TimeRangeHelper;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * search_after + PIT 分页策略
 *
 * <p>使用 Point In Time 快照翻页，不追加 _id，适合内存敏感场景。
 * PIT 生命周期由策略自动管理：首次请求自动 open，最后一页自动 close，中途放弃由 keepAlive 超时兜底。
 * 需要 ES 7.10+，版本校验在 {@code validate} 中完成。
 *
 * <p>兼容性：{@code PointInTimeBuilder} 是 ES 7.10+ 才有的类，6.8.x 中不存在。
 * 通过静态反射初始化检测其是否可用，6.8.x 下 PIT 分页在 validate 阶段即被拒绝，
 * 不会走到 {@code applyToRequest}，此处反射仅保证类加载安全。
 *
 * <p>alias 支持：PIT API 要求物理索引名，本策略通过 {@link #resolvePhysicalIndex(String)} 将 alias
 * 解析为 {@link IndexMetadata#indexName}，调用方无需关心索引名类型。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class PitPaginationStrategy implements PaginationStrategy {

    /**
     * PointInTimeBuilder 构造器（ES 7.10+），6.8.x 下为 null
     */
    private static final Constructor<?> PIT_BUILDER_CTOR;

    /**
     * PointInTimeBuilder.setKeepAlive 方法（ES 7.10+），6.8.x 下为 null
     */
    private static final Method PIT_SET_KEEP_ALIVE_METHOD;

    /**
     * SearchSourceBuilder.pointInTimeBuilder 方法（ES 7.10+），6.8.x 下为 null
     * 通过反射调用，避免字节码中出现对 PointInTimeBuilder 类型的硬引用
     */
    private static final Method SOURCE_BUILDER_PIT_METHOD;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        Constructor<?> ctor = null;
        Method setKeepAlive = null;
        Method pitMethod = null;
        try {
            Class<?> pitClass = Class.forName("org.elasticsearch.search.builder.PointInTimeBuilder");
            ctor = pitClass.getConstructor(String.class);
            setKeepAlive = pitClass.getMethod("setKeepAlive", String.class);
            pitMethod = SearchSourceBuilder.class.getMethod("pointInTimeBuilder", pitClass);
        } catch (Exception ignored) {
            log.debug("PointInTimeBuilder not available (ES < 7.10), PIT pagination will be disabled");
        }
        PIT_BUILDER_CTOR = ctor;
        PIT_SET_KEEP_ALIVE_METHOD = setKeepAlive;
        SOURCE_BUILDER_PIT_METHOD = pitMethod;
    }

    @Autowired
    private SimpleElasticsearchRouteRegistry registry;

    @Autowired
    private RouteResolver routeResolver;

    @Autowired
    private SimpleElasticsearchSearchProperties properties;

    @Autowired
    private MappingManager mappingManager;

    @Override
    public void validate(QueryRequest request, PaginationInfo pagination) {
        // 1. ES 版本校验（不暴露版本号，防止信息泄露）
        String datasourceKey = routeResolver.resolveDataSource(request.getIndex());
        ClusterInfo clusterInfo = registry.getClusterInfo(datasourceKey);
        if (clusterInfo == null || clusterInfo.getEffectiveVersion() == null) {
            throw new QueryException(ErrorCode.PIT_VERSION_NOT_READY, ErrorMessage.PIT_VERSION_NOT_READY);
        }
        int major = clusterInfo.getEffectiveVersion().getMajor();
        int minor = clusterInfo.getEffectiveVersion().getMinor();
        // PIT 需要 ES 7.10+（minor == -1 表示未解析到 minor，保守拒绝）
        boolean supported = major > 7 || (major == 7 && minor >= 10);
        if (!supported) {
            throw new QueryException(ErrorCode.PIT_NOT_SUPPORTED, ErrorMessage.PIT_NOT_SUPPORTED);
        }

        // 2. pitKeepAlive 必填
        if (!StringUtils.hasText(pagination.getPitKeepAlive())) {
            throw new QueryException(ErrorCode.PIT_KEEP_ALIVE_REQUIRED, ErrorMessage.PIT_KEEP_ALIVE_REQUIRED);
        }

        // 3. pitKeepAlive 不能超过服务端上限
        String maxKeepAlive = properties.getPit().getMaxKeepAlive();
        long userMs = parseKeepAliveToMillis(pagination.getPitKeepAlive());
        long maxMs = parseKeepAliveToMillis(maxKeepAlive);
        if (userMs > maxMs) {
            throw new QueryException(ErrorCode.PIT_KEEP_ALIVE_EXCEEDED,
                    String.format(ErrorMessage.PIT_KEEP_ALIVE_EXCEEDED, pagination.getPitKeepAlive(), maxKeepAlive));
        }
    }

    @Override
    public void applyToRequest(SearchSourceBuilder sourceBuilder,
                               SearchRequest searchRequest,
                               PaginationInfo pagination,
                               QueryRequest request) {
        sourceBuilder.size(pagination.getSize());
        if (pagination.getSearchAfter() != null) {
            sourceBuilder.searchAfter(pagination.getSearchAfter().toArray());
        }
        applySortFields(sourceBuilder, pagination);

        // 复用已有 PIT（续期 keepAlive）
        if (StringUtils.hasText(pagination.getPitId())) {
            applyPointInTime(searchRequest, pagination.getPitId(), pagination.getPitKeepAlive());
        }
    }

    @Override
    public QueryResponse.PaginationResult buildResult(SearchResponse searchResponse,
                                                      PaginationInfo pagination,
                                                      QueryRequest request) {
        SearchHit[] hits = searchResponse.getHits().getHits();
        boolean hasMore = hits.length == pagination.getSize();

        QueryResponse.PaginationResult.PaginationResultBuilder builder = QueryResponse.PaginationResult.builder()
                .type(pagination.getType())
                .hasMore(hasMore);

        if (hasMore) {
            // 有更多数据：open（首次）或复用 PIT，将 pitId 写入响应
            String pitId = openOrRenewPit(request, pagination);
            builder.pitId(pitId);
            if (hits.length > 0 && hits[hits.length - 1].getSortValues().length > 0) {
                builder.nextSearchAfter(Arrays.asList(hits[hits.length - 1].getSortValues()));
            }
        } else {
            // 最后一页：自动 close PIT
            closePitQuietly(pagination.getPitId(), request.getIndex());
        }

        return builder.build();
    }

    /**
     * 解析 keepAlive 字符串为毫秒，委托给 TimeRangeHelper
     */
    private long parseKeepAliveToMillis(String keepAlive) {
        if (!StringUtils.hasText(keepAlive)) {
            throw new QueryException(ErrorCode.PIT_KEEP_ALIVE_REQUIRED, ErrorMessage.PIT_KEEP_ALIVE_REQUIRED);
        }
        return TimeRangeHelper.parseToMillis(keepAlive);
    }

    /**
     * 通过反射将 PointInTimeBuilder 设置到 SearchRequest
     * ES 6.8.x 下 PIT_BUILDER_CTOR 为 null，此方法不会被调用（validate 阶段已拒绝）
     */
    private void applyPointInTime(SearchRequest searchRequest, String pitId, String keepAlive) {
        if (PIT_BUILDER_CTOR == null || PIT_SET_KEEP_ALIVE_METHOD == null || SOURCE_BUILDER_PIT_METHOD == null) {
            log.warn("PointInTimeBuilder not available, skipping PIT setup");
            return;
        }
        try {
            Object pitBuilder = PIT_BUILDER_CTOR.newInstance(pitId);
            PIT_SET_KEEP_ALIVE_METHOD.invoke(pitBuilder, keepAlive);
            SOURCE_BUILDER_PIT_METHOD.invoke(searchRequest.source(), pitBuilder);
        } catch (Exception e) {
            log.warn("Failed to apply PointInTimeBuilder via reflection: {}", e.getMessage());
        }
    }

    /**
     * 解析物理索引名
     * PIT API 要求物理索引名/模式，需将 alias 转换为 {@link IndexMetadata#indexName}
     */
    private String resolvePhysicalIndex(String indexAlias) {
        try {
            IndexMetadata metadata = mappingManager.getMetadata(indexAlias);
            if (metadata != null && StringUtils.hasText(metadata.getIndexName())) {
                return metadata.getIndexName();
            }
        } catch (Exception e) {
            log.warn("Failed to resolve physical index for alias [{}]: {}", indexAlias, e.getMessage());
        }
        // fallback：无法解析时使用原始值
        return indexAlias;
    }

    private String openOrRenewPit(QueryRequest request, PaginationInfo pagination) {
        if (StringUtils.hasText(pagination.getPitId())) {
            return pagination.getPitId();
        }
        try {
            String datasourceKey = routeResolver.resolveDataSource(request.getIndex());
            RestHighLevelClient client = registry.getHighLevelClient(datasourceKey);
            String keepAlive = pagination.getPitKeepAlive();
            // 解析物理索引名：alias -> IndexMetadata.indexName
            String physicalIndex = resolvePhysicalIndex(request.getIndex());
            String endpoint = SimpleElasticsearchRouteConstant.ENDPOINT_ROOT + physicalIndex
                    + SimpleElasticsearchSearchConstant.ES_API_PIT
                    + SimpleElasticsearchSearchConstant.ES_PIT_KEEP_ALIVE_PARAM + keepAlive;
            Request pitRequest = new Request(
                    SimpleElasticsearchRouteConstant.HTTP_METHOD_POST, endpoint);
            Response pitResponse = client.getLowLevelClient().performRequest(pitRequest);
            InputStream inputStream = pitResponse.getEntity().getContent();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int len;
            while ((len = inputStream.read(chunk)) != -1) {
                buffer.write(chunk, 0, len);
            }
            String body = buffer.toString(StandardCharsets.UTF_8.name());
            return OBJECT_MAPPER.readTree(body).path("id").asText();
        } catch (Exception e) {
            log.warn("Failed to open PIT for index [{}]: {}", request.getIndex(), e.getMessage());
            return null;
        }
    }

    private void closePitQuietly(String pitId, String indexAlias) {
        if (!StringUtils.hasText(pitId)) {
            return;
        }
        try {
            // close PIT 使用全局端点 /_pit，index 仅用于路由到正确的数据源
            // 这里用 alias 做 route resolution 即可，与 openOrRenewPit 中物理名路由结果一致
            String datasourceKey = routeResolver.resolveDataSource(indexAlias);
            RestHighLevelClient client = registry.getHighLevelClient(datasourceKey);
            Request closeRequest = new Request(
                    SimpleElasticsearchRouteConstant.HTTP_METHOD_DELETE,
                    SimpleElasticsearchSearchConstant.ES_API_PIT);
            closeRequest.setJsonEntity(String.format(
                    SimpleElasticsearchSearchConstant.ES_PIT_CLOSE_TEMPLATE, pitId));
            client.getLowLevelClient().performRequest(closeRequest);
            log.debug("PIT closed: index={}", indexAlias);
        } catch (Exception e) {
            log.warn("Failed to close PIT [{}] for index [{}]: {}", pitId, indexAlias, e.getMessage());
        }
    }
}
