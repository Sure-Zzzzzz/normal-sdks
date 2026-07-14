package io.github.surezzzzzz.sdk.elasticsearch.persistence.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.SimpleElasticsearchPersistenceConstant;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.BulkItemType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.IndexOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.*;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.query.PersistenceQuery;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkItem;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchRequestOptionHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchWriteRequestHelper;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.ActiveShardCount;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.support.replication.ReplicationRequest;
import org.elasticsearch.action.support.single.instance.InstanceShardOperationRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;

/**
 * Persistence ES Request Helper
 *
 * @author surezzzzzz
 */
public final class PersistenceEsRequestHelper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private PersistenceEsRequestHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static IndexRequest buildIndexRequest(io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.IndexRequest request,
                                                 String index) {
        String id = DocumentMetadataHelper.resolveId(request.getDocument(), request.getId());
        IndexRequest esRequest = ElasticsearchWriteRequestHelper.newTypedIndexRequest(index, id);
        Map<String, Object> sourceMap = toMap(request.getDocument());
        if (sourceMap != null) {
            esRequest.source(sourceMap);
        }
        IndexOptions options = request.getOptions();
        if (options != null) {
            applyWriteOptions(esRequest, options);
            ElasticsearchWriteRequestHelper.applyRouting(esRequest, options.getRouting());
            ElasticsearchWriteRequestHelper.applyPipeline(esRequest, options.getPipeline());
            ElasticsearchWriteRequestHelper.applyCreateOpType(esRequest, IndexOperationType.CREATE == options.getOperationType());
        }
        return esRequest;
    }

    public static UpdateRequest buildUpdateRequest(io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateRequest request,
                                                   String index) {
        UpdateRequest esRequest = ElasticsearchWriteRequestHelper.newTypedUpdateRequest(index, request.getId());
        if (!CollectionUtils.isEmpty(request.getFieldMap())) {
            esRequest.doc(request.getFieldMap());
        } else if (StringUtils.hasText(request.getScriptSource())) {
            Map<String, Object> params = request.getScriptParamMap() == null
                    ? Collections.emptyMap() : request.getScriptParamMap();
            esRequest.script(new Script(ScriptType.INLINE, SimpleElasticsearchPersistenceConstant.DEFAULT_SCRIPT_LANG,
                    request.getScriptSource(), params));
        }
        UpdateOptions options = request.getOptions();
        if (options != null) {
            applyWriteOptions(esRequest, options);
            ElasticsearchWriteRequestHelper.applyRouting(esRequest, options.getRouting());
            if (options.getDocAsUpsert() != null) {
                esRequest.docAsUpsert(options.getDocAsUpsert());
            }
            if (options.getFetchSource() != null) {
                esRequest.fetchSource(options.getFetchSource());
            }
            if (options.getUpsertDoc() != null) {
                Map<String, Object> upsertMap = toMap(options.getUpsertDoc());
                if (upsertMap != null) {
                    esRequest.upsert(upsertMap);
                }
            }
            if (Boolean.TRUE.equals(options.getScriptedUpsert())) {
                esRequest.scriptedUpsert(true);
            }
            ElasticsearchWriteRequestHelper.applyRetryOnConflict(esRequest, options.getRetryOnConflict());
            ElasticsearchWriteRequestHelper.applyDetectNoop(esRequest, options.getDetectNoop());
        }
        return esRequest;
    }

    public static DeleteRequest buildDeleteRequest(io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.DeleteRequest request,
                                                   String index) {
        DeleteRequest esRequest = ElasticsearchWriteRequestHelper.newTypedDeleteRequest(index, request.getId());
        DeleteOptions options = request.getOptions();
        if (options != null) {
            applyWriteOptions(esRequest, options);
            ElasticsearchWriteRequestHelper.applyRouting(esRequest, options.getRouting());
            ElasticsearchWriteRequestHelper.applyVersion(esRequest, options.getVersion());
            ElasticsearchWriteRequestHelper.applyVersionType(esRequest, options.getVersionType());
        }
        return esRequest;
    }

    public static BulkRequest buildBulkRequest(io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkRequest request,
                                               java.util.List<String> renderedIndices) {
        BulkRequest esRequest = new BulkRequest();
        boolean hasItemPipeline = hasItemPipeline(request);
        if (request.getOptions() != null) {
            applyWriteOptions(esRequest, request.getOptions());
            if (!hasItemPipeline) {
                ElasticsearchWriteRequestHelper.applyPipeline(esRequest, request.getOptions().getPipeline());
            }
        }
        int i = 0;
        for (BulkItem item : request.getItemList()) {
            String index = i < renderedIndices.size() ? renderedIndices.get(i) : (StringUtils.hasText(item.getIndex()) ? item.getIndex() : request.getDefaultIndex());
            addBulkItem(esRequest, item, index, request.getOptions(), i++);
        }
        return esRequest;
    }

    /**
     * 构造 High Level Client 的 update-by-query 请求。
     * <p>非实际执行路径：当前 by-query 同步/异步执行走 low-level REST（见 {@link ElasticsearchWriteApiHelper}），
     * 本方法无调用方，且未透传 scrollSize，仅为 HL Client 兼容保留。新代码勿用。</p>
     *
     * @deprecated by-query 执行路径已切换到 low-level REST，请使用 {@link ElasticsearchWriteApiHelper#updateByQuerySync}
     */
    @Deprecated
    public static UpdateByQueryRequest buildUpdateByQueryRequest(io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateByQueryRequest request) {
        UpdateByQueryRequest esRequest = new UpdateByQueryRequest(request.getIndex());
        esRequest.setQuery(buildQuery(request.getQuery()));
        Map<String, Object> params = request.getScriptParamMap() == null ? Collections.emptyMap() : request.getScriptParamMap();
        esRequest.setScript(new Script(ScriptType.INLINE, SimpleElasticsearchPersistenceConstant.DEFAULT_SCRIPT_LANG,
                request.getScriptSource(), params));
        applyByQueryOptions(esRequest, request.getOptions());
        return esRequest;
    }

    /**
     * 构造 High Level Client 的 delete-by-query 请求。
     * <p>非实际执行路径：当前 by-query 同步/异步执行走 low-level REST（见 {@link ElasticsearchWriteApiHelper}），
     * 本方法无调用方，仅为 HL Client 兼容保留。新代码勿用。</p>
     *
     * @deprecated by-query 执行路径已切换到 low-level REST，请使用 {@link ElasticsearchWriteApiHelper#deleteByQuerySync}
     */
    @Deprecated
    public static DeleteByQueryRequest buildDeleteByQueryRequest(io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.DeleteByQueryRequest request) {
        DeleteByQueryRequest esRequest = new DeleteByQueryRequest(request.getIndex());
        esRequest.setQuery(buildQuery(request.getQuery()));
        applyByQueryOptions(esRequest, request.getOptions());
        return esRequest;
    }

    public static QueryBuilder buildQuery(PersistenceQuery query) {
        if (query == null) {
            return QueryBuilders.matchAllQuery();
        }
        if (StringUtils.hasText(query.getRawJson())) {
            return QueryBuilders.wrapperQuery(query.getRawJson());
        }
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        if (!CollectionUtils.isEmpty(query.getTermMap())) {
            for (Map.Entry<String, Object> entry : query.getTermMap().entrySet()) {
                boolQueryBuilder.must(QueryBuilders.termQuery(entry.getKey(), entry.getValue()));
            }
        }
        if (!CollectionUtils.isEmpty(query.getRangeMap())) {
            for (Map.Entry<String, Object> entry : query.getRangeMap().entrySet()) {
                boolQueryBuilder.must(QueryBuilders.rangeQuery(entry.getKey()).gte(entry.getValue()));
            }
        }
        return boolQueryBuilder;
    }

    private static void addBulkItem(BulkRequest esRequest, BulkItem item, String index, BulkOptions options, int itemIndex) {
        if (BulkItemType.DELETE == item.getType()) {
            DeleteRequest deleteRequest = ElasticsearchWriteRequestHelper.newTypedDeleteRequest(index, item.getId());
            applyBulkRouting(deleteRequest, item, options);
            esRequest.add(deleteRequest);
            return;
        }
        if (BulkItemType.UPDATE == item.getType()) {
            UpdateRequest updateRequest = ElasticsearchWriteRequestHelper.newTypedUpdateRequest(index, item.getId());
            if (!CollectionUtils.isEmpty(item.getFieldMap())) {
                updateRequest.doc(item.getFieldMap());
            } else if (StringUtils.hasText(item.getScriptSource())) {
                Map<String, Object> params = item.getScriptParamMap() == null ? Collections.emptyMap() : item.getScriptParamMap();
                updateRequest.script(new Script(ScriptType.INLINE, SimpleElasticsearchPersistenceConstant.DEFAULT_SCRIPT_LANG,
                        item.getScriptSource(), params));
            }
            if (item.getDocAsUpsert() != null) {
                updateRequest.docAsUpsert(item.getDocAsUpsert());
            }
            if (item.getUpsertDoc() != null) {
                Map<String, Object> upsertMap = toMap(item.getUpsertDoc());
                if (upsertMap != null) {
                    updateRequest.upsert(upsertMap);
                }
            }
            if (Boolean.TRUE.equals(item.getScriptedUpsert())) {
                updateRequest.scriptedUpsert(true);
            }
            ElasticsearchWriteRequestHelper.applyRetryOnConflict(updateRequest, item.getRetryOnConflict());
            ElasticsearchWriteRequestHelper.applyDetectNoop(updateRequest, item.getDetectNoop());
            applyBulkRouting(updateRequest, item, options);
            esRequest.add(updateRequest);
            return;
        }
        String id = DocumentMetadataHelper.resolveId(item.getDocument(), item.getId());
        IndexRequest indexRequest = ElasticsearchWriteRequestHelper.newTypedIndexRequest(index, id);
        ElasticsearchWriteRequestHelper.applyCreateOpType(indexRequest, BulkItemType.CREATE == item.getType());
        ElasticsearchWriteRequestHelper.applyPipeline(indexRequest, resolveBulkPipeline(item, options));
        Map<String, Object> itemSourceMap = toMap(item.getDocument());
        if (itemSourceMap != null) {
            indexRequest.source(itemSourceMap);
        }
        applyBulkRouting(indexRequest, item, options);
        esRequest.add(indexRequest);
    }

    private static void applyWriteOptions(ReplicationRequest<?> request, WriteOptions options) {
        ElasticsearchRequestOptionHelper.applyTimeout(request, options.getTimeoutMs());
        ElasticsearchRequestOptionHelper.applyRefreshPolicy((WriteRequest<?>) request,
                options.getRefreshPolicy(), options.getRefresh());
    }

    private static void applyWriteOptions(InstanceShardOperationRequest<?> request, WriteOptions options) {
        ElasticsearchRequestOptionHelper.applyTimeout(request, options.getTimeoutMs());
        ElasticsearchRequestOptionHelper.applyRefreshPolicy((WriteRequest<?>) request,
                options.getRefreshPolicy(), options.getRefresh());
    }

    private static void applyWriteOptions(BulkRequest request, BulkOptions options) {
        ElasticsearchRequestOptionHelper.applyTimeout(request, options.getTimeoutMs());
        ElasticsearchRequestOptionHelper.applyRefreshPolicy(request, options.getRefreshPolicy(), options.getRefresh());
        request.waitForActiveShards(ActiveShardCount.DEFAULT);
    }

    private static void applyBulkRouting(org.elasticsearch.action.DocWriteRequest<?> request, BulkItem item, BulkOptions options) {
        String routing = StringUtils.hasText(item.getRouting()) ? item.getRouting()
                : options == null ? null : options.getRouting();
        ElasticsearchWriteRequestHelper.applyRouting(request, routing);
    }

    private static boolean hasItemPipeline(io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkRequest request) {
        if (request == null || CollectionUtils.isEmpty(request.getItemList())) {
            return false;
        }
        for (BulkItem item : request.getItemList()) {
            if (isIndexLike(item) && StringUtils.hasText(item.getPipeline())) {
                return true;
            }
        }
        return false;
    }

    private static String resolveBulkPipeline(BulkItem item, BulkOptions options) {
        if (StringUtils.hasText(item.getPipeline())) {
            return item.getPipeline();
        }
        return options == null ? null : options.getPipeline();
    }

    private static boolean isIndexLike(BulkItem item) {
        return item != null && (BulkItemType.INDEX == item.getType() || BulkItemType.CREATE == item.getType());
    }

    /**
     * 将 by-query 选项应用到 High Level Client 请求。
     * <p>非实际执行路径：当前 by-query 同步/异步执行走 low-level REST（见 {@link ElasticsearchWriteApiHelper}），
     * 本方法不在此执行链路上，仅为 HL Client 兼容保留。</p>
     */
    private static void applyByQueryOptions(UpdateByQueryRequest request, ByQueryOptions options) {
        if (options == null) {
            return;
        }
        ElasticsearchRequestOptionHelper.applyByQueryOptions(request, options.getBatchSize(), options.getSlices(),
                options.getConflicts(), options.getRefresh(), options.getTimeoutMs());
    }

    /**
     * 将 by-query 选项应用到 High Level Client 请求。
     * <p>非实际执行路径：当前 by-query 同步/异步执行走 low-level REST（见 {@link ElasticsearchWriteApiHelper}），
     * 本方法不在此执行链路上，仅为 HL Client 兼容保留。</p>
     */
    private static void applyByQueryOptions(DeleteByQueryRequest request, ByQueryOptions options) {
        if (options == null) {
            return;
        }
        ElasticsearchRequestOptionHelper.applyByQueryOptions(request, options.getBatchSize(), options.getSlices(),
                options.getConflicts(), options.getRefresh(), options.getTimeoutMs());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object document) {
        if (document == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.convertValue(document, Map.class);
        } catch (Exception e) {
            throw new PersistenceExecutionException(ErrorCode.ES_REQUEST_BUILD_FAILED,
                    String.format(ErrorMessage.ES_REQUEST_BUILD_FAILED, document), e);
        }
    }
}
