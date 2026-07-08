package io.github.surezzzzzz.sdk.elasticsearch.persistence.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.SimpleElasticsearchPersistenceConstant;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.*;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.*;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.query.PersistenceQuery;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkItem;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.ActiveShardCount;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.support.replication.ReplicationRequest;
import org.elasticsearch.action.support.single.instance.InstanceShardOperationRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.index.VersionType;
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
        IndexRequest esRequest = new IndexRequest(index, SimpleElasticsearchPersistenceConstant.ES_DEFAULT_TYPE);
        if (StringUtils.hasText(id)) {
            esRequest.id(id);
        }
        Map<String, Object> sourceMap = toMap(request.getDocument());
        if (sourceMap != null) {
            esRequest.source(sourceMap);
        }
        IndexOptions options = request.getOptions();
        if (options != null) {
            applyWriteOptions(esRequest, options);
            if (StringUtils.hasText(options.getRouting())) {
                esRequest.routing(options.getRouting());
            }
            if (StringUtils.hasText(options.getPipeline())) {
                esRequest.setPipeline(options.getPipeline());
            }
            IndexOperationType operationType = options.getOperationType();
            if (IndexOperationType.CREATE == operationType) {
                esRequest.opType(DocWriteRequest.OpType.CREATE);
            }
        }
        return esRequest;
    }

    public static UpdateRequest buildUpdateRequest(io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateRequest request,
                                                   String index) {
        UpdateRequest esRequest = new UpdateRequest(index, SimpleElasticsearchPersistenceConstant.ES_DEFAULT_TYPE, request.getId());
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
            if (StringUtils.hasText(options.getRouting())) {
                esRequest.routing(options.getRouting());
            }
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
            if (options.getRetryOnConflict() != null) {
                esRequest.retryOnConflict(options.getRetryOnConflict());
            }
            if (options.getDetectNoop() != null) {
                esRequest.detectNoop(options.getDetectNoop());
            }
        }
        return esRequest;
    }

    public static DeleteRequest buildDeleteRequest(io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.DeleteRequest request,
                                                   String index) {
        DeleteRequest esRequest = new DeleteRequest(index, SimpleElasticsearchPersistenceConstant.ES_DEFAULT_TYPE, request.getId());
        DeleteOptions options = request.getOptions();
        if (options != null) {
            applyWriteOptions(esRequest, options);
            if (StringUtils.hasText(options.getRouting())) {
                esRequest.routing(options.getRouting());
            }
            if (options.getVersion() != null) {
                esRequest.version(options.getVersion());
            }
            if (StringUtils.hasText(options.getVersionType())) {
                esRequest.versionType(VersionType.fromString(options.getVersionType()));
            }
        }
        return esRequest;
    }

    public static BulkRequest buildBulkRequest(io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkRequest request,
                                               java.util.List<String> renderedIndices) {
        BulkRequest esRequest = new BulkRequest();
        boolean hasItemPipeline = hasItemPipeline(request);
        if (request.getOptions() != null) {
            applyWriteOptions(esRequest, request.getOptions());
            if (!hasItemPipeline && StringUtils.hasText(request.getOptions().getPipeline())) {
                esRequest.pipeline(request.getOptions().getPipeline());
            }
        }
        int i = 0;
        for (BulkItem item : request.getItemList()) {
            String index = i < renderedIndices.size() ? renderedIndices.get(i) : (StringUtils.hasText(item.getIndex()) ? item.getIndex() : request.getDefaultIndex());
            addBulkItem(esRequest, item, index, request.getOptions(), i++);
        }
        return esRequest;
    }

    public static UpdateByQueryRequest buildUpdateByQueryRequest(io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateByQueryRequest request) {
        UpdateByQueryRequest esRequest = new UpdateByQueryRequest(request.getIndex());
        esRequest.setQuery(buildQuery(request.getQuery()));
        Map<String, Object> params = request.getScriptParamMap() == null ? Collections.emptyMap() : request.getScriptParamMap();
        esRequest.setScript(new Script(ScriptType.INLINE, SimpleElasticsearchPersistenceConstant.DEFAULT_SCRIPT_LANG,
                request.getScriptSource(), params));
        applyByQueryOptions(esRequest, request.getOptions());
        return esRequest;
    }

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
            DeleteRequest deleteRequest = new DeleteRequest(index, SimpleElasticsearchPersistenceConstant.ES_DEFAULT_TYPE, item.getId());
            applyBulkRouting(deleteRequest, item, options);
            esRequest.add(deleteRequest);
            return;
        }
        if (BulkItemType.UPDATE == item.getType()) {
            UpdateRequest updateRequest = new UpdateRequest(index, SimpleElasticsearchPersistenceConstant.ES_DEFAULT_TYPE, item.getId());
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
            if (item.getRetryOnConflict() != null) {
                updateRequest.retryOnConflict(item.getRetryOnConflict());
            }
            if (item.getDetectNoop() != null) {
                updateRequest.detectNoop(item.getDetectNoop());
            }
            applyBulkRouting(updateRequest, item, options);
            esRequest.add(updateRequest);
            return;
        }
        IndexRequest indexRequest = new IndexRequest(index, SimpleElasticsearchPersistenceConstant.ES_DEFAULT_TYPE);
        String id = DocumentMetadataHelper.resolveId(item.getDocument(), item.getId());
        if (StringUtils.hasText(id)) {
            indexRequest.id(id);
        }
        if (BulkItemType.CREATE == item.getType()) {
            indexRequest.opType(DocWriteRequest.OpType.CREATE);
        }
        String pipeline = resolveBulkPipeline(item, options);
        if (StringUtils.hasText(pipeline)) {
            indexRequest.setPipeline(pipeline);
        }
        Map<String, Object> itemSourceMap = toMap(item.getDocument());
        if (itemSourceMap != null) {
            indexRequest.source(itemSourceMap);
        }
        applyBulkRouting(indexRequest, item, options);
        esRequest.add(indexRequest);
    }

    private static void applyWriteOptions(ReplicationRequest<?> request, WriteOptions options) {
        if (options.getTimeoutMs() != null) {
            request.timeout(options.getTimeoutMs() + SimpleElasticsearchPersistenceConstant.TIMEOUT_MS_SUFFIX);
        }
        applyRefreshPolicy((WriteRequest<?>) request, options);
    }

    private static void applyWriteOptions(InstanceShardOperationRequest<?> request, WriteOptions options) {
        if (options.getTimeoutMs() != null) {
            request.timeout(options.getTimeoutMs() + SimpleElasticsearchPersistenceConstant.TIMEOUT_MS_SUFFIX);
        }
        applyRefreshPolicy((WriteRequest<?>) request, options);
    }

    private static void applyWriteOptions(BulkRequest request, BulkOptions options) {
        if (options.getTimeoutMs() != null) {
            request.timeout(options.getTimeoutMs() + SimpleElasticsearchPersistenceConstant.TIMEOUT_MS_SUFFIX);
        }
        applyRefreshPolicy(request, options);
        request.waitForActiveShards(ActiveShardCount.DEFAULT);
    }

    private static void applyRefreshPolicy(WriteRequest<?> request, WriteOptions options) {
        if (StringUtils.hasText(options.getRefreshPolicy())) {
            request.setRefreshPolicy(toRefreshPolicy(options.getRefreshPolicy()));
            return;
        }
        if (options.getRefresh() != null) {
            request.setRefreshPolicy(toRefreshPolicy(options.getRefresh()));
        }
    }

    private static WriteRequest.RefreshPolicy toRefreshPolicy(String refreshPolicy) {
        if (SimpleElasticsearchPersistenceCoreConstant.REFRESH_POLICY_TRUE.equals(refreshPolicy)) {
            return WriteRequest.RefreshPolicy.IMMEDIATE;
        }
        if (SimpleElasticsearchPersistenceCoreConstant.REFRESH_POLICY_FALSE.equals(refreshPolicy)) {
            return WriteRequest.RefreshPolicy.NONE;
        }
        if (SimpleElasticsearchPersistenceCoreConstant.REFRESH_POLICY_WAIT_FOR.equals(refreshPolicy)) {
            return WriteRequest.RefreshPolicy.WAIT_UNTIL;
        }
        return WriteRequest.RefreshPolicy.parse(refreshPolicy);
    }

    private static void applyBulkRouting(org.elasticsearch.action.DocWriteRequest<?> request, BulkItem item, BulkOptions options) {
        String routing = StringUtils.hasText(item.getRouting()) ? item.getRouting()
                : options == null ? null : options.getRouting();
        if (StringUtils.hasText(routing)) {
            request.routing(routing);
        }
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

    private static void applyByQueryOptions(UpdateByQueryRequest request, ByQueryOptions options) {
        if (options == null) {
            return;
        }
        if (options.getBatchSize() != null) {
            request.setBatchSize(options.getBatchSize());
        }
        applyCommonByQueryOptions(request, options);
    }

    private static void applyByQueryOptions(DeleteByQueryRequest request, ByQueryOptions options) {
        if (options == null) {
            return;
        }
        if (options.getBatchSize() != null) {
            request.setBatchSize(options.getBatchSize());
        }
        applyCommonByQueryOptions(request, options);
    }

    // scrollSize 不在此设置：7.17 HighLevelClient 未暴露 setScrollSize，仅异步 low-level body 路径设置 scroll_size。
    private static void applyCommonByQueryOptions(org.elasticsearch.index.reindex.AbstractBulkByScrollRequest<?> request,
                                                  ByQueryOptions options) {
        if (options.getSlices() != null) {
            request.setSlices(options.getSlices());
        }
        if (StringUtils.hasText(options.getConflicts())) {
            request.setConflicts(options.getConflicts());
        }
        if (options.getRefresh() != null) {
            request.setRefresh(options.getRefresh());
        }
        if (options.getTimeoutMs() != null) {
            request.setTimeout(options.getTimeoutMs() + SimpleElasticsearchPersistenceConstant.TIMEOUT_MS_SUFFIX);
        }
    }

    private static WriteRequest.RefreshPolicy toRefreshPolicy(Boolean refresh) {
        return Boolean.TRUE.equals(refresh) ? WriteRequest.RefreshPolicy.IMMEDIATE : WriteRequest.RefreshPolicy.NONE;
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
