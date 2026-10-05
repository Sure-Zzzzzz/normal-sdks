package io.github.surezzzzzz.sdk.elasticsearch.search.engine;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.AggregationDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.AggregationResponseParser;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.PaginationType;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SearchAfterMode;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsAggErrorEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsAggEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsQueryErrorEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsQueryEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.model.AggExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.model.QueryExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.SimpleElasticsearchSearchException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.SearchIndexResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.ResolvedIndex;
import io.github.surezzzzzz.sdk.elasticsearch.search.pagination.CursorTokenCodec;
import io.github.surezzzzzz.sdk.elasticsearch.search.pagination.model.CursorState;
import io.github.surezzzzzz.sdk.elasticsearch.search.processor.SensitiveFieldProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.SearchDslBuilder;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.SearchResponseParser;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchPayloadCodec;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchRestHelper;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import static io.github.surezzzzzz.sdk.elasticsearch.search.constant.SearchProtocolConstant.*;
import static io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant.*;
import static io.github.surezzzzzz.sdk.elasticsearch.search.support.SearchValidationHelper.*;

/**
 * REST 查询门面；所有输入冻结、归属和敏感检查都先于业务查询 HTTP。
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultSearchEngine implements SearchEngine {
    private final SimpleElasticsearchSearchProperties properties;
    private final SearchPayloadCodec codec;
    private final SearchRestHelper rest;
    private final SearchIndexResolver indices;
    private final MappingManager mappings;
    private final SensitiveFieldProcessor sensitive;
    private final SearchDslBuilder queries;
    private final AggregationDslBuilder aggregations;
    private final SearchResponseParser queryParser;
    private final AggregationResponseParser aggParser;
    private final CursorTokenCodec cursors;
    private final ApplicationEventPublisher publisher;

    /**
     * 执行结构化查询，返回受字段保护的分页结果。
     */
    public QueryResponse query(QueryRequest request) {
        return executeQuery(request, false);
    }

    /**
     * 仅统计精确总数，不加载命中文档。
     */
    public QueryResponse count(QueryRequest request) {
        return executeQuery(request, true);
    }

    private QueryResponse executeQuery(QueryRequest original, boolean forceCount) {
        ResolvedIndex target = null;
        boolean count = forceCount || original != null && Boolean.TRUE.equals(original.getCountOnly());
        CursorState active = null;
        String rawResource = null, kind = null;
        try {
            QueryRequest request = codec.copy(original, QueryRequest.class);
            PaginationInfo pagination = count || request.getPagination() == null ? PaginationInfo.builder().build() : request.getPagination();
            PaginationType type;
            SearchAfterMode mode;
            try {
                type = pagination.getTypeEnum();
                mode = pagination.getSearchAfterModeEnum();
            } catch (RuntimeException error) {
                throw invalid();
            }
            boolean pit = !count && type == PaginationType.SEARCH_AFTER && mode == SearchAfterMode.PIT;
            boolean scroll = !count && type == PaginationType.SCROLL;
            kind = pit ? VALUE_PIT : scroll ? VALUE_SCROLL : null;
            String token = pit ? pagination.getPitId() : scroll ? pagination.getScrollId() : null;
            if (!count && ((!pit && text(pagination.getPitId())) || (!scroll && text(pagination.getScrollId()))
                    || (type == PaginationType.OFFSET && pagination.getSearchAfter() != null && !pagination.getSearchAfter().isEmpty())))
                throw invalid();
            if (kind != null) {
                cursors.validate();
                if (text(token)) active = cursors.decode(token, kind, false);
            }
            if (active == null) target = indices.resolve(request.getIndex(), request.getDateRange());
            else {
                target = indices.resume(request.getIndex(), active.getIndices(), active.getDatasource(), active.getFrom(), active.getTo(), active.getDowngradeLevel());
                if (!target.getIdentifier().equals(active.getIdentifier())) throw cursor();
            }
            Map<String, FieldMetadata> fields = mappings.fields(target);
            Map<String, Object> query = queries.query(request.getQuery(), target, fields);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put(VALUE_QUERY, query);
            Map<String, String> parameters = parameters();
            if (count) {
                long start = System.nanoTime();
                parameters.remove(VALUE_ALLOW_PARTIAL_SEARCH_RESULTS);
                Map<String, Object> response = rest.perform(target.getDatasource(), VALUE_POST, indexPath(target) + PATH_COUNT, parameters, body);
                queryParser.complete(response);
                QueryResponse result = QueryResponse.builder().total(number(response, VALUE_COUNT)).items(null).page(null).size(null).pagination(null)
                        .took((System.nanoTime() - start) / NANOS_PER_MILLISECOND).build();
                queryEvent(result, target, true);
                return result;
            }
            int size = pagination.getSize() == null ? properties.getQueryLimits().getDefaultSize() : pagination.getSize();
            if (size <= 0 || size > properties.getQueryLimits().getMaxSize()) throw invalid();
            int page = pagination.getPage() == null ? 1 : pagination.getPage();
            if (page <= 0 || (type != PaginationType.OFFSET && page != 1)) throw invalid();
            long from = Math.multiplyExact((long) page - 1, size);
            if (type == PaginationType.OFFSET && from + size > properties.getQueryLimits().getMaxOffset())
                throw invalid();
            body.put(VALUE_SIZE, size);
            body.put(VALUE_TRACK_TOTAL_HITS, true);
            if (type == PaginationType.OFFSET) body.put(VALUE_FROM, from);
            if (request.getFields() != null && !request.getFields().isEmpty()) {
                for (String field : request.getFields()) {
                    sensitive.require(target.getConfig(), field, false);
                    if (!fields.containsKey(field) || fields.get(field).isConflict())
                        throw SearchValidationHelper.field();
                }
                body.put(VALUE_ES_SOURCE, request.getFields());
            }
            if (request.getCollapse() != null) {
                if (type != PaginationType.OFFSET) throw invalid();
                String collapse = queries.field(request.getCollapse().getField(), target, fields, true);
                Map<String, Object> config = new LinkedHashMap<>();
                config.put(VALUE_FIELD, collapse);
                Integer groups = request.getCollapse().getMaxConcurrentGroupSearches();
                if (groups != null) {
                    if (groups <= 0) throw invalid();
                    config.put(VALUE_MAX_CONCURRENT_GROUP_SEARCHES, groups);
                }
                body.put(VALUE_COLLAPSE, config);
            }
            List<Object> sort = sort(pagination, target, fields, pit, scroll);
            if (!sort.isEmpty()) body.put(VALUE_SORT, sort);
            List<Object> after = pagination.getSearchAfter();
            if (type == PaginationType.SEARCH_AFTER && after != null && !after.isEmpty()) {
                if (after.size() != sort.size()) throw invalid();
                body.put(VALUE_SEARCH_AFTER, after);
            } else if (active != null && pit) throw cursor();
            if (type != PaginationType.SEARCH_AFTER && after != null && !after.isEmpty()) throw invalid();
            String hash = fingerprint(request, size, target, sort, kind);
            if (active != null) {
                if (!hash.equals(active.getRequestHash()) || (pit && !codec.encode(after).equals(codec.encode(active.getSortValues()))))
                    throw cursor();
                rawResource = active.getRawId();
            } else if (pit && after != null && !after.isEmpty()) throw cursor();
            String keepAlive = kind == null ? null : (pit ? pagination.getPitKeepAlive() : pagination.getScrollTtl());
            if (keepAlive == null && kind != null) keepAlive = properties.getCursor().getKeepAlive();
            long ttl = kind == null ? 0 : SearchIndexResolver.durationMillis(keepAlive, MAX_CURSOR_SECONDS * MILLIS_PER_SECOND_LONG);
            String endpoint = indexPath(target) + PATH_SEARCH;
            if (pit) {
                if (rawResource == null) {
                    Map<String, String> openParameters = new LinkedHashMap<>();
                    openParameters.put(VALUE_KEEP_ALIVE, keepAlive);
                    openParameters.put(VALUE_IGNORE_UNAVAILABLE, String.valueOf(properties.getQueryLimits().isIgnoreUnavailableIndices()));
                    Map<String, Object> opened = rest.perform(target.getDatasource(), VALUE_POST, indexPath(target) + PATH_PIT, openParameters, null);
                    if (!(opened.get(VALUE_ID) instanceof String) || !text((String) opened.get(VALUE_ID)))
                        throw protocol();
                    rawResource = (String) opened.get(VALUE_ID);
                }
                Map<String, Object> config = new LinkedHashMap<>();
                config.put(VALUE_ID, rawResource);
                config.put(VALUE_KEEP_ALIVE, keepAlive);
                body.put(VALUE_PIT, config);
                endpoint = PATH_SEARCH;
                parameters.remove(VALUE_IGNORE_UNAVAILABLE);
            }
            Map<String, Object> response;
            if (scroll && active != null) {
                Map<String, Object> continuation = new LinkedHashMap<>();
                continuation.put(VALUE_SCROLL_ID, rawResource);
                continuation.put(VALUE_SCROLL, keepAlive);
                response = rest.perform(target.getDatasource(), VALUE_POST, PATH_SEARCH_SCROLL, Collections.emptyMap(), continuation);
            } else {
                if (scroll) parameters.put(VALUE_SCROLL, keepAlive);
                response = rest.perform(target.getDatasource(), VALUE_POST, endpoint, parameters, body);
            }
            if (kind != null) {
                Object latest = response.get(pit ? VALUE_PIT_ID : VALUE_ES_SCROLL_ID);
                if (latest instanceof String && text((String) latest)) rawResource = (String) latest;
                if (rawResource == null) throw protocol();
            }
            QueryResponse result = queryParser.parse(response, target, type == PaginationType.OFFSET ? page : null, size);
            long seen = active == null ? 0 : active.getSeen();
            boolean more = type == PaginationType.OFFSET ? from + result.getItems().size() < result.getTotal()
                    : kind == null ? result.getItems().size() == size : seen + result.getItems().size() < result.getTotal();
            if (result.getItems().isEmpty()) more = false;
            if (request.getCollapse() != null) more = result.getItems().size() == size;
            QueryResponse.PaginationResult pageResult = QueryResponse.PaginationResult.builder().type(type.getType()).hasMore(more).build();
            List<Object> next = null;
            if (type == PaginationType.SEARCH_AFTER && more) {
                List<?> hits = (List<?>) object(response.get(VALUE_HITS)).get(VALUE_HITS);
                Object values = object(hits.get(hits.size() - 1)).get(VALUE_SORT);
                if (!(values instanceof List) || ((List<?>) values).size() != sort.size()) throw protocol();
                next = new ArrayList<>((List<?>) values);
                pageResult.setNextSearchAfter(next);
            }
            if (kind != null) {
                if (more) {
                    String nextToken = cursors.encode(CursorState.builder().kind(kind).datasource(target.getDatasource()).identifier(target.getIdentifier())
                            .indices(target.getIndices()).from(target.getFrom()).to(target.getTo()).downgradeLevel(target.getDowngradeLevel())
                            .rawId(rawResource).expiresAt(Math.addExact(System.currentTimeMillis(), ttl)).seen(seen + result.getItems().size())
                            .requestHash(hash).sortValues(next).build());
                    if (pit) pageResult.setPitId(nextToken);
                    else pageResult.setScrollId(nextToken);
                } else cleanup(kind, target.getDatasource(), rawResource);
            }
            result.setPagination(pageResult);
            queryEvent(result, target, false);
            return result;
        } catch (RuntimeException error) {
            if (kind != null && rawResource != null && target != null)
                cleanup(kind, target.getDatasource(), rawResource);
            emit(new EsQueryErrorEvent(this, QueryRequest.builder().sourceType(SOURCE_STRUCTURED).countOnly(count).build(),
                    safe(error), target == null ? null : target.getDatasource(), target == null ? 0 : target.getDowngradeLevel(), count,
                    new QueryExecutionContext(null, target == null ? null : target.getDatasource(), target == null ? 0 : target.getDowngradeLevel(), SOURCE_STRUCTURED)));
            throw error instanceof SimpleElasticsearchSearchException ? error : invalid();
        }
    }

    /**
     * 执行结构化聚合，返回统一指标或桶结果。
     */
    public AggResponse aggregate(AggRequest original) {
        ResolvedIndex target = null;
        try {
            AggRequest request = codec.copy(original, AggRequest.class);
            target = indices.resolve(request.getIndex(), request.getDateRange());
            Map<String, FieldMetadata> fields = mappings.fields(target);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put(VALUE_SIZE, 0);
            body.put(VALUE_QUERY, queries.query(request.getQuery(), target, fields));
            body.put(VALUE_AGGS, aggregations.build(request.getAggs(), request.getAfter(), target, fields));
            Map<String, Object> response = rest.perform(target.getDatasource(), VALUE_POST, indexPath(target) + PATH_SEARCH, parameters(), body);
            queryParser.complete(response);
            AggResponse result = aggParser.parse(response, request.getAggs());
            emit(new EsAggEvent(this, AggRequest.builder().sourceType(SOURCE_STRUCTURED).build(), AggResponse.builder().aggregations(null).took(result.getTook()).build(),
                    new AggExecutionContext(null, target.getDatasource(), target.getDowngradeLevel(), SOURCE_STRUCTURED)));
            return result;
        } catch (RuntimeException error) {
            emit(new EsAggErrorEvent(this, AggRequest.builder().sourceType(SOURCE_STRUCTURED).build(), safe(error), target == null ? null : target.getDatasource(),
                    target == null ? 0 : target.getDowngradeLevel(), new AggExecutionContext(null, target == null ? null : target.getDatasource(), target == null ? 0 : target.getDowngradeLevel(), SOURCE_STRUCTURED)));
            throw error instanceof SimpleElasticsearchSearchException ? error : invalid();
        }
    }

    /**
     * 认证游标后在原数据源关闭查询快照。
     */
    public void closePit(String token) {
        close(token, VALUE_PIT);
    }

    /**
     * 认证游标后在原数据源释放遍历上下文。
     */
    public void clearScroll(String token) {
        close(token, VALUE_SCROLL);
    }

    private void close(String token, String kind) {
        // 已过期 token 仍可认证后关闭；不能把过期误当作无需释放 ES 上下文。
        CursorState state = cursors.decode(token, kind, true);
        release(kind, state.getDatasource(), state.getRawId());
    }

    private void release(String kind, String source, String rawId) {
        Map<String, Object> response = rest.perform(source, VALUE_DELETE, VALUE_PIT.equals(kind) ? PATH_PIT : PATH_SEARCH_SCROLL, Collections.emptyMap(),
                node(VALUE_PIT.equals(kind) ? VALUE_ID : VALUE_SCROLL_ID, VALUE_PIT.equals(kind) ? rawId : Collections.singletonList(rawId)));
        if (!Boolean.TRUE.equals(response.get(VALUE_SUCCEEDED))) throw protocol();
        log.debug("查询上下文已释放 datasource={} kind={}", source, kind);
    }

    private void cleanup(String kind, String source, String id) {
        try {
            release(kind, source, id);
        } catch (RuntimeException error) {
            log.debug("查询上下文释放失败 datasource={} kind={} category={}", source, kind, error.getClass().getSimpleName());
        }
    }

    private List<Object> sort(PaginationInfo pagination, ResolvedIndex target, Map<String, FieldMetadata> fields, boolean pit, boolean scroll) {
        List<Object> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        if (pagination.getSort() != null) for (PaginationInfo.SortField sort : pagination.getSort()) {
            if (sort == null || !text(sort.getField())) throw invalid();
            String order = sort.getOrder() == null ? VALUE_ASC : sort.getOrder();
            if (!Arrays.asList(VALUE_ASC, VALUE_DESC).contains(order)) throw invalid();
            String name = sort.getField();
            if (!VALUE_ES_SCORE.equals(name) && !(pit && VALUE_ES_SHARD_DOC.equals(name)) && !(scroll && VALUE_ES_DOC.equals(name)))
                name = queries.field(name, target, fields, true);
            if (!names.add(name)) throw invalid();
            result.add(node(name, order));
        }
        if (pit && !names.contains(VALUE_ES_SHARD_DOC)) result.add(node(VALUE_ES_SHARD_DOC, VALUE_ASC));
        else if (scroll && result.isEmpty()) result.add(node(VALUE_ES_DOC, VALUE_ASC));
        else if (pagination.getTypeEnum() == PaginationType.SEARCH_AFTER) {
            if (pagination.getSearchAfterModeEnum() == SearchAfterMode.TIEBREAKER) {
                String tie = queries.field(target.getConfig().getTiebreakerField(), target, fields, true);
                if (!names.contains(tie)) result.add(node(tie, VALUE_ASC));
            }
            if (result.isEmpty()) throw invalid();
        }
        return result;
    }

    private Map<String, String> parameters() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put(VALUE_IGNORE_UNAVAILABLE, String.valueOf(properties.getQueryLimits().isIgnoreUnavailableIndices()));
        params.put(VALUE_ALLOW_PARTIAL_SEARCH_RESULTS, VALUE_FALSE);
        return params;
    }

    private String indexPath(ResolvedIndex target) {
        return PATH_SEPARATOR + String.join(INDEX_SEPARATOR, target.getIndices());
    }

    private String fingerprint(QueryRequest request, int size, ResolvedIndex target, List<Object> sort, String kind) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put(VALUE_INDEX, request.getIndex());
        facts.put(VALUE_QUERY, request.getQuery());
        facts.put(FIELD_DATE_RANGE, request.getDateRange());
        facts.put(VALUE_FIELDS, request.getFields());
        facts.put(VALUE_COLLAPSE, request.getCollapse());
        facts.put(VALUE_SIZE, size);
        facts.put(VALUE_SORT, sort);
        facts.put(VALUE_KIND, kind);
        facts.put(VALUE_CONFIG, target.getConfig());
        facts.put(FIELD_STRICT_DATE_FILTER, properties.getQueryLimits().isStrictDateFilter());
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance(HASH_ALGORITHM)
                    .digest(codec.encode(facts).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw cursor();
        }
    }

    private void queryEvent(QueryResponse result, ResolvedIndex target, boolean count) {
        emit(new EsQueryEvent(this, QueryRequest.builder().sourceType(SOURCE_STRUCTURED).countOnly(count).build(),
                QueryResponse.builder().total(result.getTotal()).page(result.getPage()).size(result.getSize()).items(null).pagination(null).took(result.getTook()).build(),
                new QueryExecutionContext(null, target.getDatasource(), target.getDowngradeLevel(), SOURCE_STRUCTURED)));
        log.debug("查询终态 datasource={} countOnly={} returned={} took={}", target.getDatasource(), count,
                result.getItems() == null ? 0 : result.getItems().size(), result.getTook());
    }

    private Throwable safe(Throwable error) {
        if (error instanceof SimpleElasticsearchSearchException) {
            SimpleElasticsearchSearchException value = (SimpleElasticsearchSearchException) error;
            return new SimpleElasticsearchSearchException(value.getErrorCode(), value.getMessage(), value.getStatus());
        }
        return invalid();
    }

    private void emit(Object event) {
        try {
            publisher.publishEvent(event);
        } catch (RuntimeException error) {
            log.debug("查询事件监听器失败 category={}", error.getClass().getSimpleName());
        }
    }
}
