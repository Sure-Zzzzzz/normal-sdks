package io.github.surezzzzzz.sdk.elasticsearch.persistence.support;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.classifier.BulkFailureClassifier;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.configuration.SimpleElasticsearchPersistenceProperties;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.*;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.event.EsPersistenceErrorEvent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.event.EsPersistenceEvent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.PersistenceExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.*;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.query.PersistenceQuery;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.*;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.*;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.BulkConflictExecutionException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.BulkPersistenceExecutionException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceRestException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.model.request.TaskQueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentPreProcessorChain;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentProcessContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

import static io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.PersistenceProtocolConstant.*;
import static io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceRestHelper.*;
import static io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver.invalid;

/**
 * REST 写入编译器：先完整校验、路由和序列化，再执行任何业务 HTTP。
 */
@Slf4j
@RequiredArgsConstructor
public class ElasticsearchWriteApiHelper {
    private final PersistenceTargetResolver targets;
    private final PersistencePayloadCodec codec;
    private final PersistenceRestHelper rest;
    private final DocumentPreProcessorChain processors;
    private final BulkFailureClassifier classifier;
    private final SimpleElasticsearchPersistenceProperties properties;
    private final ApplicationEventPublisher publisher;

    /**
     * 复制调用方索引选项；CREATE 入口不能退化为覆盖写。
     */
    public static IndexOptions indexOptions(IndexOptions value, IndexOperationType type) {
        IndexOptions.IndexOptionsBuilder<?, ?> result = IndexOptions.builder().operationType(type);
        if (value != null) result.pipeline(value.getPipeline()).routing(value.getRouting()).refresh(value.getRefresh())
                .refreshPolicy(value.getRefreshPolicy()).timeoutMs(value.getTimeoutMs());
        return result.build();
    }

    private static UpdateOptions updateOptions(UpdateOptions value, String routing) {
        if (value != null && value.getRouting() != null && !Objects.equals(value.getRouting(), routing))
            throw invalid();
        UpdateOptions.UpdateOptionsBuilder<?, ?> result = UpdateOptions.builder().routing(routing);
        if (value != null)
            result.refresh(value.getRefresh()).refreshPolicy(value.getRefreshPolicy()).timeoutMs(value.getTimeoutMs())
                    .docAsUpsert(value.getDocAsUpsert()).detectNoop(value.getDetectNoop()).upsertDoc(value.getUpsertDoc())
                    .scriptedUpsert(value.getScriptedUpsert()).retryOnConflict(value.getRetryOnConflict()).fetchSource(value.getFetchSource());
        return result.build();
    }

    private static long optionalNumber(Map<String, Object> value, String key) {
        return value.containsKey(key) ? number(value, key) : 0;
    }

    private static long elapsed(long start) {
        return (System.nanoTime() - start) / NANOS_PER_MILLISECOND;
    }

    private static void positive(Number value) {
        if (value != null && value.longValue() <= 0) throw invalid();
    }

    private static void put(Map<String, String> values, String key, Object value) {
        if (value != null) values.put(key, String.valueOf(value));
    }

    private static boolean text(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static void requireId(String id) {
        if (!text(id)) throw invalid();
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace(SPACE_FORM, SPACE_ENCODED);
    }

    private static PersistenceExecutionException sanitized(Throwable error) {
        if (error instanceof PersistenceRestException)
            return new PersistenceRestException(((PersistenceRestException) error).getStatus());
        if (error instanceof PersistenceExecutionException) {
            String code = ((PersistenceExecutionException) error).getErrorCode();
            if (ErrorCode.REQUEST_VALIDATION_FAILED.equals(code)) return invalid();
            if (ErrorCode.ES_RESPONSE_PARSE_FAILED.equals(code)) return protocol();
            if (ErrorCode.ES_REQUEST_BUILD_FAILED.equals(code))
                return new PersistenceExecutionException(code, ErrorMessage.ES_REQUEST_BUILD_FAILED);
        }
        return new PersistenceExecutionException(ErrorCode.EXECUTION_FAILED, ErrorMessage.EXECUTION_FAILED);
    }

    /**
     * 冻结正文、参数与物理目标；异步排队期间不再读取调用方对象。
     */
    public Supplier<Object> prepare(PersistenceRequest request, boolean clientAsync) {
        return preflight(() -> prepareInternal(request, clientAsync), null, clientAsync);
    }

    private <T> T preflight(Supplier<T> action, PersistenceOperationType type, boolean clientAsync) {
        try {
            return action.get();
        } catch (RuntimeException error) {
            log.debug("持久化预检失败 clientAsync={} category={}", clientAsync, error.getClass().getSimpleName());
            PersistenceExecutionException failure = sanitized(error);
            emit(new EsPersistenceErrorEvent(this, null, failure, PersistenceExecutionContext.builder().operationType(type).clientAsync(clientAsync)
                    .startTimeMs(System.currentTimeMillis()).build()));
            throw failure;
        }
    }

    private Supplier<Object> prepareInternal(PersistenceRequest request, boolean clientAsync) {
        if (request == null) throw invalid();
        if (request instanceof IndexRequest) {
            IndexRequest value = (IndexRequest) request;
            boolean create = value.getOptions() != null && value.getOptions().getOperationType() == IndexOperationType.CREATE;
            return observed(index(value), create ? PersistenceOperationType.CREATE : PersistenceOperationType.INDEX, clientAsync);
        }
        if (request instanceof UpdateRequest)
            return observed(update((UpdateRequest) request), PersistenceOperationType.UPDATE, clientAsync);
        if (request instanceof DeleteRequest)
            return observed(delete((DeleteRequest) request), PersistenceOperationType.DELETE, clientAsync);
        if (request instanceof BulkRequest) {
            PreparedBulk plan = bulkPlan((BulkRequest) request, false);
            return observed(() -> executeBulk(plan), PersistenceOperationType.BULK, plan.datasource, clientAsync);
        }
        if (request instanceof UpdateByQueryRequest) {
            UpdateByQueryRequest value = (UpdateByQueryRequest) request;
            return observed(byQuery(value.getIndex(), value.getQuery(), value.getOptions(), value.getScriptSource(),
                    value.getScriptParamMap(), true), PersistenceOperationType.UPDATE_BY_QUERY, clientAsync);
        }
        if (request instanceof DeleteByQueryRequest) {
            DeleteByQueryRequest value = (DeleteByQueryRequest) request;
            return observed(byQuery(value.getIndex(), value.getQuery(), value.getOptions(), null, null, false),
                    PersistenceOperationType.DELETE_BY_QUERY, clientAsync);
        }
        if (request instanceof TaskQueryRequest) {
            TaskQueryRequest value = (TaskQueryRequest) request;
            String datasource = value.getDatasource();
            String taskId = value.getTaskId();
            if (!targets.hasDatasource(datasource) || !text(taskId) || !taskId.matches(REGEX_TASK_ID)) throw invalid();
            return observed(() -> {
                Map<String, Object> response = rest.perform(datasource, VALUE_GET, PATH_TASKS + segment(taskId), Collections.emptyMap(), null);
                if (response.containsKey(VALUE_ERROR)) throw protocol();
                if (!(response.get(VALUE_COMPLETED) instanceof Boolean)) throw protocol();
                boolean completed = Boolean.TRUE.equals(response.get(VALUE_COMPLETED));
                Map<String, Object> state = object(completed ? response.get(VALUE_RESPONSE) : object(response.get(VALUE_TASK)).get(VALUE_STATUS));
                return taskResult(state, datasource, null, taskId, completed);
            }, PersistenceOperationType.GET_TASK, datasource, clientAsync);
        }
        throw invalid();
    }

    /**
     * 编译单条写入，允许 ES 自动生成 index ID，CREATE 必须有显式 ID。
     */
    private RestWrite index(IndexRequest request) {
        if (request.getDocument() == null) throw invalid();
        String raw = DocumentMetadataHelper.resolveIndex(request.getDocument(), request.getIndex());
        String target = targets.writeIndex(raw);
        String source = targets.datasource(raw, target);
        String id = DocumentMetadataHelper.resolveId(request.getDocument(), request.getId());
        IndexOptions options = request.getOptions();
        IndexOperationType type = options == null || options.getOperationType() == null ? IndexOperationType.INDEX : options.getOperationType();
        Map<String, String> params = writeOptions(options, false);
        if (type == IndexOperationType.CREATE && !text(id)) throw invalid();
        put(params, VALUE_OP_TYPE, type.getCode());
        if (options != null) put(params, VALUE_PIPELINE, options.getPipeline());
        Object document = process(request.getDocument(), raw, target, source,
                type == IndexOperationType.CREATE ? PersistenceOperationType.CREATE : PersistenceOperationType.INDEX, false, null);
        String body = documentBody(document);
        String path = PATH_SEPARATOR + segment(target) + PATH_DOC + (text(id) ? PATH_SEPARATOR + segment(id) : EMPTY);
        return new RestWrite(source, target, id, text(id) ? VALUE_PUT : VALUE_POST, path, params, body, false);
    }

    private RestWrite update(UpdateRequest request) {
        String target = targets.physicalIndex(request.getIndex());
        requireId(request.getId());
        UpdateOptions options = request.getOptions();
        Map<String, String> params = writeOptions(options, false);
        if (options != null) {
            if (options.getRetryOnConflict() != null && options.getRetryOnConflict() < 0) throw invalid();
            put(params, VALUE_RETRY_ON_CONFLICT, options.getRetryOnConflict());
            put(params, VALUE_ES_SOURCE, options.getFetchSource());
        }
        Map<String, Object> body = updateBody(request.getFieldMap(), request.getScriptSource(), request.getScriptParamMap(),
                options == null ? null : options.getDocAsUpsert(), options == null ? null : options.getDetectNoop(),
                options == null ? null : options.getUpsertDoc(), options == null ? null : options.getScriptedUpsert());
        return new RestWrite(targets.datasource(target), target, request.getId(), VALUE_POST, PATH_SEPARATOR + segment(target)
                + PATH_UPDATE + segment(request.getId()), params, codec.encode(body), false);
    }

    private RestWrite delete(DeleteRequest request) {
        // 删除实体注解可能是逻辑索引，故不能用 documentClass 猜历史物理分片。
        String target = targets.physicalIndex(request.getIndex());
        requireId(request.getId());
        DeleteOptions options = request.getOptions();
        Map<String, String> params = writeOptions(options, false);
        if (options != null) {
            if (options.getVersion() != null && options.getVersion() <= 0) throw invalid();
            if (options.getVersionType() != null && !Arrays.asList(VALUE_INTERNAL, VALUE_EXTERNAL, VALUE_EXTERNAL_GTE).contains(options.getVersionType()))
                throw invalid();
            if (options.getVersionType() != null && options.getVersion() == null) throw invalid();
            put(params, VALUE_VERSION, options.getVersion());
            put(params, VALUE_VERSION_TYPE, options.getVersionType());
        }
        return new RestWrite(targets.datasource(target), target, request.getId(), VALUE_DELETE, PATH_SEPARATOR + segment(target)
                + PATH_DOC_ID_PREFIX + segment(request.getId()), params, null, options != null && Boolean.TRUE.equals(options.getNotFoundAsSuccess()));
    }

    private Supplier<Object> observed(RestWrite plan, PersistenceOperationType type, boolean async) {
        if (type == PersistenceOperationType.UPDATE_BY_QUERY || type == PersistenceOperationType.DELETE_BY_QUERY) {
            return observed(() -> {
                Map<String, Object> response = rest.perform(plan.datasource, plan.method, plan.endpoint, plan.parameters, plan.body);
                if (response.containsKey(VALUE_ERROR)) throw protocol();
                Object task = response.get(VALUE_TASK);
                boolean asynchronous = VALUE_FALSE.equals(plan.parameters.get(VALUE_WAIT_FOR_COMPLETION));
                if (asynchronous != (task != null)) throw protocol();
                if (task != null) {
                    if (!(task instanceof String) || !((String) task).matches(REGEX_TASK_ID)) throw protocol();
                    return ByQueryTaskResult.builder().completed(false).taskId((String) task).index(plan.index)
                            .datasource(plan.datasource).failureList(Collections.emptyList()).build();
                }
                return taskResult(response, plan.datasource, plan.index, null, true);
            }, type, plan.datasource, async);
        }
        return observed(() -> single(plan, type), type, plan.datasource, async);
    }

    private PersistenceResult single(RestWrite plan, PersistenceOperationType type) {
        long start = System.nanoTime();
        Map<String, Object> result;
        try {
            result = rest.perform(plan.datasource, plan.method, plan.endpoint, plan.parameters, plan.body);
        } catch (PersistenceRestException error) {
            if (type == PersistenceOperationType.DELETE && plan.notFoundAsSuccess && error.isDocumentMissing()) {
                return PersistenceResult.builder().success(true).id(plan.id).index(plan.index).datasource(plan.datasource)
                        .operationType(type).result(SimpleElasticsearchPersistenceCoreConstant.ES_RESULT_NOT_FOUND).tookMs(elapsed(start)).build();
            }
            throw error;
        }
        String status = result.get(VALUE_RESULT) instanceof String ? (String) result.get(VALUE_RESULT) : null;
        List<String> allowed = type == PersistenceOperationType.CREATE ? Collections.singletonList(VALUE_CREATED)
                : type == PersistenceOperationType.INDEX ? Arrays.asList(VALUE_CREATED, VALUE_UPDATED)
                : type == PersistenceOperationType.UPDATE ? Arrays.asList(VALUE_CREATED, VALUE_UPDATED, VALUE_NOOP) : Collections.singletonList(VALUE_DELETED);
        if (!allowed.contains(status)
                || !(result.get(VALUE_ES_ID) instanceof String) || !(result.get(VALUE_ES_INDEX) instanceof String))
            throw protocol();
        return PersistenceResult.builder().success(true).id((String) result.get(VALUE_ES_ID)).index((String) result.get(VALUE_ES_INDEX))
                .datasource(plan.datasource).operationType(type).result(status).tookMs(elapsed(start)).asyncRouted(false).build();
    }

    private PreparedBulk bulkPlan(BulkRequest request, boolean createOnly) {
        if (request.getItemList() == null || request.getItemList().isEmpty()) throw invalid();
        BulkOptions options = request.getOptions();
        int batchSize = options == null || options.getBatchSize() == null ? request.getItemList().size() : options.getBatchSize();
        if (batchSize <= 0) throw invalid();
        Map<String, String> params = writeOptions(options, false);
        if (options != null) put(params, VALUE_PIPELINE, options.getPipeline());
        List<PreparedItem> items = new ArrayList<>();
        List<String> indices = new ArrayList<>();
        // 全部 item（包括后续批次）先校验，绝不因后项错误留下先前批次的写副作用。
        for (BulkItem item : request.getItemList()) {
            if (item == null || item.getType() == null || (createOnly && item.getType() != BulkItemType.CREATE))
                throw invalid();
            boolean documentWrite = item.getType() == BulkItemType.INDEX || item.getType() == BulkItemType.CREATE;
            if (documentWrite && (item.getFieldMap() != null || item.getScriptSource() != null || item.getScriptParamMap() != null
                    || item.getUpsertDoc() != null || item.getDocAsUpsert() != null || item.getScriptedUpsert() != null || item.getDetectNoop() != null))
                throw invalid();
            if (!documentWrite && item.getDocument() != null) throw invalid();
            if (item.getType() == BulkItemType.DELETE && (item.getFieldMap() != null || item.getScriptSource() != null || item.getScriptParamMap() != null
                    || item.getUpsertDoc() != null || item.getDocAsUpsert() != null || item.getScriptedUpsert() != null || item.getDetectNoop() != null))
                throw invalid();
            String raw = text(item.getIndex()) ? item.getIndex() : request.getDefaultIndex();
            if (documentWrite) raw = DocumentMetadataHelper.resolveIndex(item.getDocument(), raw);
            String target = documentWrite ? targets.writeIndex(raw) : targets.physicalIndex(raw);
            String id = documentWrite ? DocumentMetadataHelper.resolveId(item.getDocument(), item.getId()) : item.getId();
            if (item.getType() != BulkItemType.INDEX) requireId(id);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put(VALUE_ES_INDEX, target);
            if (text(id)) metadata.put(VALUE_ES_ID, id);
            String routing = item.getRouting() == null && options != null ? options.getRouting() : item.getRouting();
            if (routing != null) metadata.put(VALUE_ROUTING, routing);
            if (item.getPipeline() != null) {
                if (!documentWrite) throw invalid();
                metadata.put(VALUE_PIPELINE, item.getPipeline());
            }
            if (item.getRetryOnConflict() != null) {
                if (item.getType() != BulkItemType.UPDATE || item.getRetryOnConflict() < 0) throw invalid();
                metadata.put(VALUE_RETRY_ON_CONFLICT, item.getRetryOnConflict());
            }
            indices.add(raw);
            indices.add(target);
            items.add(new PreparedItem(item.getType(), raw, target, id, routing, metadata, item, null));
        }
        String source = targets.datasource(indices.toArray(new String[0]));
        List<PreparedItem> frozen = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            PreparedItem item = items.get(i);
            Object document = null;
            String body = null;
            if (item.type == BulkItemType.INDEX || item.type == BulkItemType.CREATE) {
                document = process(item.original.getDocument(), item.rawIndex, item.index, source,
                        item.type == BulkItemType.CREATE ? PersistenceOperationType.CREATE : PersistenceOperationType.INDEX, true, i);
                body = documentBody(document);
            } else if (item.type == BulkItemType.UPDATE) {
                body = codec.encode(updateBody(item.original.getFieldMap(), item.original.getScriptSource(), item.original.getScriptParamMap(),
                        item.original.getDocAsUpsert(), item.original.getDetectNoop(), item.original.getUpsertDoc(), item.original.getScriptedUpsert()));
            }
            String ndjson = codec.encode(Collections.singletonMap(item.type.getCode(), item.metadata)) + NEWLINE + (body == null ? EMPTY : body + NEWLINE);
            BulkItem snapshot = BulkItem.builder().type(item.type).index(item.index).id(item.id).routing(item.routing)
                    .document(document == null ? null : codec.snapshot(document)).build();
            frozen.add(new PreparedItem(item.type, item.rawIndex, item.index, item.id, item.routing, Collections.emptyMap(), snapshot, ndjson));
        }
        return new PreparedBulk(source, frozen, batchSize, options == null || !Boolean.FALSE.equals(options.getContinueOnFailure()), params);
    }

    private BulkResult executeBulk(PreparedBulk plan) {
        long start = System.nanoTime();
        BulkResult result = BulkResult.builder().success(true).datasource(plan.datasource).total(plan.items.size()).failureList(new ArrayList<>())
                .batchTotal(0).batchSucceeded(0).batchFailed(0).stoppedOnFailure(false).partial(false).build();
        for (int from = 0; from < plan.items.size(); from += Math.min(plan.batchSize, plan.items.size() - from)) {
            int to = from + Math.min(plan.batchSize, plan.items.size() - from);
            StringBuilder ndjson = new StringBuilder();
            for (int i = from; i < to; i++) ndjson.append(plan.items.get(i).ndjson);
            try {
                Map<String, Object> response = rest.perform(plan.datasource, VALUE_POST, PATH_BULK, plan.parameters, ndjson.toString());
                Object values = response.get(VALUE_ITEMS);
                if (!(values instanceof List) || ((List<?>) values).size() != to - from) throw protocol();
                List<BulkItemFailure> failures = new ArrayList<>();
                for (int i = from; i < to; i++) {
                    PreparedItem item = plan.items.get(i);
                    Map<String, Object> envelope = object(((List<?>) values).get(i - from));
                    if (envelope.size() != 1 || !envelope.containsKey(item.type.getCode())) throw protocol();
                    Map<String, Object> state = object(envelope.get(item.type.getCode()));
                    int status = Math.toIntExact(number(state, VALUE_STATUS));
                    if (status < HTTP_STATUS_MIN || status > HTTP_STATUS_MAX) throw protocol();
                    if (status < HTTP_SUCCESS_MIN || status >= HTTP_SUCCESS_LIMIT || state.containsKey(VALUE_ERROR)) {
                        failures.add(BulkItemFailure.builder().itemIndex(i).type(item.type).index(item.index).id(item.id).datasource(plan.datasource)
                                .status(status).errorCode(ErrorCode.EXECUTION_FAILED).errorMessage(ErrorMessage.EXECUTION_FAILED)
                                .errorReason(ErrorMessage.EXECUTION_FAILED).retryable(classifier.retryable(status, null, null)).build());
                    }
                }
                result.setBatchTotal(result.getBatchTotal() + 1);
                result.getFailureList().addAll(failures);
                result.setSucceeded(result.getSucceeded() + to - from - failures.size());
                result.setFailed(result.getFailed() + failures.size());
                if (failures.isEmpty()) result.setBatchSucceeded(result.getBatchSucceeded() + 1);
                else result.setBatchFailed(result.getBatchFailed() + 1);
                log.debug("持久化 bulk 批次 datasource={} count={} failed={}", plan.datasource, to - from, failures.size());
                if (!failures.isEmpty() && !plan.continueOnFailure && to < plan.items.size()) {
                    result.setStoppedOnFailure(true);
                    break;
                }
            } catch (RuntimeException error) {
                result.setSuccess(false);
                result.setHasFailure(true);
                result.setPartial(true);
                result.setTookMs(elapsed(start));
                // 当前批次的 HTTP/解析失败也可能已经写入，不能声明该批次可安全重试。
                throw new BulkPersistenceExecutionException(result, sanitized(error));
            }
        }
        result.setHasFailure(result.getFailed() > 0 || result.getSucceeded() + result.getFailed() < result.getTotal());
        result.setSuccess(!result.isHasFailure());
        result.setTookMs(elapsed(start));
        return result;
    }

    /**
     * 预编译双阶段单写，补偿始终固定在 CREATE 的物理目标和 routing。
     */
    public Supplier<PersistenceResult> prepareConflict(IndexRequest create, UpdateRequest fallback, boolean async) {
        return preflight(() -> prepareConflictInternal(create, fallback, async), PersistenceOperationType.CREATE, async);
    }

    private Supplier<PersistenceResult> prepareConflictInternal(IndexRequest create, UpdateRequest fallback, boolean async) {
        if (create == null || fallback == null) throw invalid();
        IndexRequest copy = IndexRequest.builder().document(create.getDocument()).index(create.getIndex()).id(create.getId())
                .options(indexOptions(create.getOptions(), IndexOperationType.CREATE)).build();
        String rawIndex = DocumentMetadataHelper.resolveIndex(copy.getDocument(), copy.getIndex());
        RestWrite first = index(copy);
        UpdateRequest pinned = UpdateRequest.builder().index(first.index).id(first.id).fieldMap(fallback.getFieldMap())
                .scriptSource(fallback.getScriptSource()).scriptParamMap(fallback.getScriptParamMap())
                .options(updateOptions(fallback.getOptions(), first.parameters.get(VALUE_ROUTING))).build();
        if (text(fallback.getIndex()) && !fallback.getIndex().equals(rawIndex) && !fallback.getIndex().equals(first.index))
            throw invalid();
        if (text(fallback.getId()) && !fallback.getId().equals(first.id)) throw invalid();
        RestWrite second = update(pinned);
        if (!first.datasource.equals(second.datasource) || !Objects.equals(first.parameters.get(VALUE_ROUTING), second.parameters.get(VALUE_ROUTING)))
            throw invalid();
        Supplier<Object> result = observed(() -> {
            try {
                return single(first, PersistenceOperationType.CREATE);
            } catch (PersistenceRestException error) {
                if (error.getStatus() != HTTP_CONFLICT) throw error;
                log.debug("持久化 CREATE 冲突补偿 datasource={}", first.datasource);
                return single(second, PersistenceOperationType.UPDATE);
            }
        }, PersistenceOperationType.CREATE, first.datasource, async);
        return () -> (PersistenceResult) result.get();
    }

    /**
     * 仅补偿 CREATE 的 409 item；二阶段失败保留第一阶段结果。
     */
    public Supplier<BulkResult> prepareBulkConflict(BulkRequest request, ConflictUpdateResolver resolver, boolean async) {
        return preflight(() -> prepareBulkConflictInternal(request, resolver, async), PersistenceOperationType.BULK, async);
    }

    private Supplier<BulkResult> prepareBulkConflictInternal(BulkRequest request, ConflictUpdateResolver resolver, boolean async) {
        if (request == null) throw invalid();
        if (resolver == null) throw invalid();
        PreparedBulk first = bulkPlan(request, true);
        Supplier<Object> result = observed(() -> {
            BulkResult initial = executeBulk(first);
            List<BulkItem> updates = new ArrayList<>();
            List<Integer> offsets = new ArrayList<>();
            List<BulkItemFailure> remaining = new ArrayList<>();
            try {
                for (BulkItemFailure failure : initial.getFailureList()) {
                    if (!Integer.valueOf(HTTP_CONFLICT).equals(failure.getStatus())) {
                        remaining.add(failure);
                        continue;
                    }
                    int originalOffset = failure.getItemIndex();
                    PreparedItem original = first.items.get(originalOffset);
                    // 回调不能改写阶段结果或原 item 下标，否则异常恢复会指向错误文档。
                    BulkItemFailure view = BulkItemFailure.builder().itemIndex(originalOffset).type(failure.getType())
                            .index(failure.getIndex()).id(failure.getId()).datasource(failure.getDatasource()).status(failure.getStatus())
                            .errorCode(failure.getErrorCode()).errorMessage(failure.getErrorMessage()).errorType(failure.getErrorType())
                            .errorReason(failure.getErrorReason()).retryable(failure.getRetryable()).build();
                    BulkItem update = resolver.resolve(original.original, view);
                    if (update == null || update.getType() != BulkItemType.UPDATE) throw invalid();
                    if (text(update.getIndex()) && !original.index.equals(update.getIndex())) throw invalid();
                    if (text(update.getId()) && !original.id.equals(update.getId())) throw invalid();
                    if (update.getRouting() != null && !Objects.equals(original.routing, update.getRouting()))
                        throw invalid();
                    updates.add(BulkItem.builder().type(BulkItemType.UPDATE).index(original.index).id(original.id).routing(original.routing)
                            .fieldMap(update.getFieldMap()).scriptSource(update.getScriptSource()).scriptParamMap(update.getScriptParamMap())
                            .docAsUpsert(update.getDocAsUpsert()).detectNoop(update.getDetectNoop()).upsertDoc(update.getUpsertDoc())
                            .scriptedUpsert(update.getScriptedUpsert()).retryOnConflict(update.getRetryOnConflict()).build());
                    offsets.add(originalOffset);
                }
                if (updates.isEmpty()) return initial;
                // 请求级 pipeline 只属于 CREATE 阶段，不能再给 UPDATE 使用。
                BulkRequest secondRequest = BulkRequest.builder().itemList(updates).options(BulkOptions.builder()
                        .batchSize(first.batchSize).continueOnFailure(first.continueOnFailure)
                        .refreshPolicy(first.parameters.get(VALUE_REFRESH)).build()).build();
                BulkResult second = executeBulk(bulkPlan(secondRequest, false));
                for (BulkItemFailure failure : second.getFailureList()) {
                    failure.setItemIndex(offsets.get(failure.getItemIndex()));
                    remaining.add(failure);
                }
                // 停止后的补偿 item 未执行，仍保留原 CREATE 冲突，不能从失败清单中消失。
                int processed = Math.toIntExact(second.getSucceeded() + second.getFailed());
                for (int i = processed; i < offsets.size(); i++) {
                    int skipped = offsets.get(i);
                    for (BulkItemFailure failure : initial.getFailureList())
                        if (failure.getItemIndex() == skipped) remaining.add(failure);
                }
                initial.setSucceeded(initial.getSucceeded() + second.getSucceeded());
                initial.setFailureList(remaining);
                initial.setFailed(remaining.size());
                initial.setStoppedOnFailure(Boolean.TRUE.equals(initial.getStoppedOnFailure()) || Boolean.TRUE.equals(second.getStoppedOnFailure()));
                initial.setHasFailure(!remaining.isEmpty() || Boolean.TRUE.equals(initial.getStoppedOnFailure())
                        || initial.getSucceeded() + initial.getFailed() < initial.getTotal());
                initial.setSuccess(!initial.isHasFailure());
                initial.setBatchTotal(initial.getBatchTotal() + second.getBatchTotal());
                initial.setBatchSucceeded(initial.getBatchSucceeded() + second.getBatchSucceeded());
                initial.setBatchFailed(initial.getBatchFailed() + second.getBatchFailed());
                initial.setTookMs(initial.getTookMs() + second.getTookMs());
                return initial;
            } catch (RuntimeException error) {
                initial.setPartial(true);
                initial.setSuccess(false);
                initial.setHasFailure(true);
                BulkResult compensation = error instanceof BulkPersistenceExecutionException
                        ? ((BulkPersistenceExecutionException) error).getPartialResult() : null;
                throw new BulkConflictExecutionException(initial, compensation, offsets, sanitized(error));
            }
        }, PersistenceOperationType.BULK, first.datasource, async);
        return () -> (BulkResult) result.get();
    }

    private RestWrite byQuery(String index, PersistenceQuery query, ByQueryOptions options, String script,
                              Map<String, Object> scriptParams, boolean update) {
        String[] indices = index == null ? new String[0] : index.split(INDEX_SEPARATOR, -1);
        if (indices.length == 0) throw invalid();
        String source = targets.datasource(indices);
        Map<String, Object> dsl = query(query);
        boolean matchAll = !restricted(dsl, 0);
        if (matchAll && (!properties.getByQuery().isAllowMatchAll() || indices.length != 1 || PersistenceTargetResolver.wildcard(index)
                || (!update && (options == null || options.getMaxDocs() == null || options.getMaxDocs() <= 0))))
            throw invalid();
        Map<String, String> params = writeOptions(options, true);
        if (options != null) {
            positive(options.getMaxDocs());
            positive(options.getSlices());
            positive(options.getScrollSize());
            positive(options.getBatchSize());
            if (options.getWaitForActiveShards() != null && options.getWaitForActiveShards() < 0) throw invalid();
            if (options.getConflicts() != null && !Arrays.asList(VALUE_ABORT, VALUE_PROCEED).contains(options.getConflicts()))
                throw invalid();
            if (options.getRequestsPerSecond() != null && (!Float.isFinite(options.getRequestsPerSecond())
                    || (options.getRequestsPerSecond() <= 0 && options.getRequestsPerSecond() != REQUESTS_PER_SECOND_UNLIMITED)))
                throw invalid();
            put(params, VALUE_WAIT_FOR_COMPLETION, options.getWaitForCompletion());
            put(params, VALUE_SLICES, options.getSlices());
            put(params, VALUE_SCROLL_SIZE, options.getScrollSize() == null ? options.getBatchSize() : options.getScrollSize());
            put(params, VALUE_CONFLICTS, options.getConflicts());
            put(params, VALUE_REQUESTS_PER_SECOND, options.getRequestsPerSecond());
            put(params, VALUE_MAX_DOCS, options.getMaxDocs());
            put(params, VALUE_WAIT_FOR_ACTIVE_SHARDS, options.getWaitForActiveShards());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(VALUE_QUERY, dsl);
        if (update) body.put(VALUE_SCRIPT, script(script, scriptParams));
        return new RestWrite(source, index, null, VALUE_POST, PATH_SEPARATOR + index + (update ? PATH_UPDATE_BY_QUERY : PATH_DELETE_BY_QUERY),
                params, codec.encode(body), false);
    }

    private Map<String, Object> query(PersistenceQuery value) {
        if (value == null) return Collections.singletonMap(VALUE_MATCH_ALL, Collections.emptyMap());
        if (text(value.getRawJson())) {
            if ((value.getTermMap() != null && !value.getTermMap().isEmpty()) || (value.getRangeMap() != null && !value.getRangeMap().isEmpty()))
                throw invalid();
            return codec.decode(value.getRawJson());
        }
        List<Object> filter = new ArrayList<>();
        if (value.getTermMap() != null) value.getTermMap().forEach((key, item) -> {
            if (!text(key) || item == null) throw invalid();
            filter.add(Collections.singletonMap(VALUE_TERM, Collections.singletonMap(key, item)));
        });
        if (value.getRangeMap() != null) value.getRangeMap().forEach((key, item) -> {
            if (!text(key) || !(item instanceof Map) || object(item).isEmpty()) throw invalid();
            filter.add(Collections.singletonMap(VALUE_RANGE, Collections.singletonMap(key, item)));
        });
        return filter.isEmpty() ? Collections.singletonMap(VALUE_MATCH_ALL, Collections.emptyMap())
                : Collections.singletonMap(VALUE_BOOL, Collections.singletonMap(VALUE_FILTER, filter));
    }

    private boolean restricted(Map<String, Object> query, int depth) {
        if (depth > MAX_QUERY_DEPTH || query.size() != 1) return false;
        if (query.containsKey(VALUE_MATCH_NONE)) return true;
        for (String leaf : Arrays.asList(VALUE_TERM, VALUE_TERMS, VALUE_RANGE, VALUE_IDS, VALUE_EXISTS, VALUE_MATCH, VALUE_MATCH_PHRASE, VALUE_PREFIX, VALUE_WILDCARD, VALUE_REGEXP)) {
            if (query.containsKey(leaf)) return query.get(leaf) instanceof Map && !object(query.get(leaf)).isEmpty();
        }
        if (!query.containsKey(VALUE_BOOL) || !(query.get(VALUE_BOOL) instanceof Map)) return false;
        Map<String, Object> bool = object(query.get(VALUE_BOOL));
        for (String key : Arrays.asList(VALUE_MUST, VALUE_FILTER)) {
            Object clauses = bool.get(key);
            List<?> values = clauses instanceof List ? (List<?>) clauses : clauses == null ? Collections.emptyList() : Collections.singletonList(clauses);
            for (Object clause : values)
                if (clause instanceof Map && restricted(object(clause), depth + 1)) return true;
        }
        Object should = bool.get(VALUE_SHOULD);
        if (!(should instanceof List) || ((List<?>) should).isEmpty()) return false;
        Object minimum = bool.get(VALUE_MINIMUM_SHOULD_MATCH);
        boolean required = minimum instanceof Number && ((Number) minimum).intValue() > 0
                || minimum == null && !bool.containsKey(VALUE_MUST) && !bool.containsKey(VALUE_FILTER);
        if (!required) return false;
        for (Object clause : (List<?>) should)
            if (!(clause instanceof Map) || !restricted(object(clause), depth + 1)) return false;
        return true;
    }

    private Map<String, Object> updateBody(Map<String, Object> fields, String source, Map<String, Object> params,
                                           Boolean docAsUpsert, Boolean detectNoop, Object upsert, Boolean scripted) {
        boolean hasDoc = fields != null && !fields.isEmpty();
        boolean hasScript = text(source);
        if (hasDoc == hasScript || (!hasScript && params != null && !params.isEmpty())
                || (Boolean.TRUE.equals(scripted) && (!hasScript || upsert == null))
                || (Boolean.TRUE.equals(docAsUpsert) && (!hasDoc || upsert != null))) throw invalid();
        Map<String, Object> body = new LinkedHashMap<>();
        if (hasDoc) body.put(VALUE_DOC, fields);
        else body.put(VALUE_SCRIPT, script(source, params));
        if (docAsUpsert != null) body.put(VALUE_DOC_AS_UPSERT, docAsUpsert);
        if (detectNoop != null) body.put(VALUE_DETECT_NOOP, detectNoop);
        if (upsert != null) body.put(VALUE_UPSERT, codec.decode(documentBody(upsert)));
        if (scripted != null) body.put(VALUE_SCRIPTED_UPSERT, scripted);
        return body;
    }

    private Map<String, Object> script(String source, Map<String, Object> params) {
        if (!text(source)) throw invalid();
        Map<String, Object> script = new LinkedHashMap<>();
        script.put(VALUE_LANG, SimpleElasticsearchPersistenceCoreConstant.SCRIPT_LANG_PAINLESS);
        script.put(VALUE_SOURCE, source);
        if (params != null) script.put(VALUE_PARAMS, params);
        return script;
    }

    private Map<String, String> writeOptions(WriteOptions options, boolean byQuery) {
        Map<String, String> result = new LinkedHashMap<>();
        if (options == null) return result;
        String refresh = options.getRefreshPolicy();
        if (refresh != null && !Arrays.asList(VALUE_TRUE, VALUE_FALSE, VALUE_WAIT_FOR).contains(refresh))
            throw invalid();
        if (refresh != null && options.getRefresh() != null && !refresh.equals(options.getRefresh().toString()))
            throw invalid();
        if (byQuery && VALUE_WAIT_FOR.equals(refresh)) throw invalid();
        put(result, VALUE_REFRESH, refresh == null ? options.getRefresh() : refresh);
        put(result, VALUE_ROUTING, options.getRouting());
        positive(options.getTimeoutMs());
        if (options.getTimeoutMs() != null) result.put(VALUE_TIMEOUT, options.getTimeoutMs() + UNIT_MILLISECOND);
        return result;
    }

    private Object process(Object doc, String raw, String target, String source, PersistenceOperationType type, boolean bulk, Integer offset) {
        if (doc == null) throw invalid();
        return processors.process(doc, DocumentProcessContext.builder().operationType(type).rawIndex(raw)
                .renderedIndex(target).datasource(source).bulk(bulk).bulkItemIndex(offset).build());
    }

    private String documentBody(Object doc) {
        String body = codec.encode(doc);
        codec.decode(body);
        return body;
    }

    private Supplier<Object> observed(Supplier<Object> operation, PersistenceOperationType type, String source, boolean async) {
        return () -> {
            long start = System.currentTimeMillis();
            PersistenceExecutionContext context = PersistenceExecutionContext.builder().operationType(type).datasource(source)
                    .startTimeMs(start).clientAsync(async).routeAsyncWrite(false).build();
            log.debug("持久化入口 operation={} datasource={} clientAsync={}", type, source, async);
            try {
                Object result = operation.get();
                context.setTookMs(System.currentTimeMillis() - start);
                if (result instanceof ByQueryTaskResult)
                    context.setServerAsyncTask(!((ByQueryTaskResult) result).isCompleted());
                emit(new EsPersistenceEvent(this, null, eventResult(result), context));
                return result;
            } catch (RuntimeException error) {
                context.setTookMs(System.currentTimeMillis() - start);
                emit(new EsPersistenceErrorEvent(this, null, sanitized(error), context));
                throw error;
            }
        };
    }

    private Object eventResult(Object result) {
        // Core 原事件携带请求/响应，必须另建不含 ID、index、正文、脚本或 task 的快照。
        if (result instanceof PersistenceResult) {
            PersistenceResult value = (PersistenceResult) result;
            return PersistenceResult.builder().success(value.isSuccess()).datasource(value.getDatasource())
                    .operationType(value.getOperationType()).result(value.getResult()).tookMs(value.getTookMs()).build();
        }
        if (result instanceof BulkResult) {
            BulkResult value = (BulkResult) result;
            return BulkResult.builder().success(value.isSuccess()).hasFailure(value.isHasFailure()).total(value.getTotal())
                    .succeeded(value.getSucceeded()).failed(value.getFailed()).datasource(value.getDatasource()).tookMs(value.getTookMs())
                    .batchTotal(value.getBatchTotal()).batchSucceeded(value.getBatchSucceeded()).batchFailed(value.getBatchFailed())
                    .stoppedOnFailure(value.getStoppedOnFailure()).partial(value.getPartial()).build();
        }
        ByQueryTaskResult value = (ByQueryTaskResult) result;
        return ByQueryTaskResult.builder().completed(value.isCompleted()).datasource(value.getDatasource()).total(value.getTotal())
                .updated(value.getUpdated()).deleted(value.getDeleted()).versionConflicts(value.getVersionConflicts()).tookMs(value.getTookMs()).build();
    }

    private void emit(Object event) {
        try {
            publisher.publishEvent(event);
        } catch (RuntimeException error) {
            log.debug("持久化事件监听器失败 category={}", error.getClass().getSimpleName());
        }
    }

    /**
     * 队列拒绝发生在 HTTP 之前，发布独立脱敏失败事件，不伪装为 ES 写失败。
     */
    public void reportAsyncRejection() {
        log.debug("持久化客户端异步提交被拒绝");
        emit(new EsPersistenceErrorEvent(this, null, new PersistenceExecutionException(ErrorCode.EXECUTION_FAILED, ErrorMessage.EXECUTION_FAILED),
                PersistenceExecutionContext.builder().clientAsync(true).startTimeMs(System.currentTimeMillis()).build()));
    }

    private ByQueryTaskResult taskResult(Map<String, Object> state, String source, String index, String taskId, boolean completed) {
        List<ByQueryFailure> failures = new ArrayList<>();
        Object values = state.get(VALUE_FAILURES);
        if (Boolean.TRUE.equals(state.get(VALUE_TIMED_OUT))) failures.add(ByQueryFailure.builder()
                .cause(ErrorMessage.EXECUTION_FAILED).status(String.valueOf(SimpleElasticsearchPersistenceCoreConstant.HTTP_STATUS_REQUEST_TIMEOUT)).build());
        if (values != null) {
            if (!(values instanceof List)) throw protocol();
            for (Object value : (List<?>) values) {
                Map<String, Object> failure = object(value);
                failures.add(ByQueryFailure.builder().cause(ErrorMessage.EXECUTION_FAILED)
                        .status(String.valueOf(number(failure, VALUE_STATUS))).build());
            }
        }
        return ByQueryTaskResult.builder().completed(completed).datasource(source).index(index).taskId(taskId)
                .total(number(state, VALUE_TOTAL)).updated(optionalNumber(state, VALUE_UPDATED)).deleted(optionalNumber(state, VALUE_DELETED))
                .versionConflicts(optionalNumber(state, VALUE_VERSION_CONFLICTS)).tookMs(optionalNumber(state, VALUE_TOOK)).failureList(failures).build();
    }

    @Getter
    @RequiredArgsConstructor
    private static class RestWrite {
        private final String datasource, index, id, method, endpoint;
        private final Map<String, String> parameters;
        private final String body;
        private final boolean notFoundAsSuccess;
    }

    @RequiredArgsConstructor
    private static class PreparedItem {
        private final BulkItemType type;
        private final String rawIndex, index, id, routing;
        private final Map<String, Object> metadata;
        private final BulkItem original;
        private final String ndjson;
    }

    @RequiredArgsConstructor
    private static class PreparedBulk {
        private final String datasource;
        private final List<PreparedItem> items;
        private final int batchSize;
        private final boolean continueOnFailure;
        private final Map<String, String> parameters;
    }
}
