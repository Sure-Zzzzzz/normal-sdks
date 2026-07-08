package io.github.surezzzzzz.sdk.elasticsearch.persistence.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.BulkItemType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.IndexOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.SimpleElasticsearchPersistenceCoreConstant;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.BulkOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.DeleteOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.IndexOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.UpdateOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.query.PersistenceQuery;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.*;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceEsRequestHelper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.index.query.QueryBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PersistenceEsRequestHelper 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
class PersistenceEsRequestHelperTest {

    private static final String TEST_INDEX = "test_persistence_helper";

    @Data
    @Document(indexName = "test_persistence_helper")
    static class HelperDoc {
        @Id
        private String id;
        private String name;
        private long ts;
    }

    @Test
    @DisplayName("buildIndexRequest：文档转 ES IndexRequest，index/id/source 正确")
    void buildIndexRequestBasic() {
        HelperDoc doc = new HelperDoc();
        doc.setId("id-1");
        doc.setName("name-1");
        doc.setTs(123L);
        IndexRequest request = IndexRequest.builder().document(doc).id("id-1").build();
        org.elasticsearch.action.index.IndexRequest esRequest =
                PersistenceEsRequestHelper.buildIndexRequest(request, TEST_INDEX);
        log.info("esRequest index={}, id={}, source={}", esRequest.index(), esRequest.id(), esRequest.sourceAsMap());
        assertEquals(TEST_INDEX, esRequest.index(), "索引名应与传入一致");
        assertEquals("id-1", esRequest.id(), "id 应与传入一致");
        assertEquals("name-1", esRequest.sourceAsMap().get("name"), "source 应包含文档字段");
    }

    @Test
    @DisplayName("buildIndexRequest：CREATE opType 透传")
    void buildIndexRequestCreateOpType() {
        HelperDoc doc = new HelperDoc();
        doc.setId("id-2");
        IndexRequest request = IndexRequest.builder().document(doc).id("id-2")
                .options(IndexOptions.builder().operationType(IndexOperationType.CREATE).build())
                .build();
        org.elasticsearch.action.index.IndexRequest esRequest =
                PersistenceEsRequestHelper.buildIndexRequest(request, TEST_INDEX);
        log.info("esRequest opType={}", esRequest.opType());
        assertEquals(DocWriteRequest.OpType.CREATE, esRequest.opType(), "CREATE opType 应被设置");
    }

    @Test
    @DisplayName("buildIndexRequest：timeout + refresh 选项透传不报错")
    void buildIndexRequestTimeoutAndRefresh() {
        HelperDoc doc = new HelperDoc();
        doc.setId("id-3");
        IndexRequest request = IndexRequest.builder().document(doc).id("id-3")
                .options(IndexOptions.builder().timeoutMs(5000L).refresh(true).build())
                .build();
        org.elasticsearch.action.index.IndexRequest esRequest =
                PersistenceEsRequestHelper.buildIndexRequest(request, TEST_INDEX);
        log.info("esRequest timeout={}, refreshPolicy={}", esRequest.timeout(), esRequest.getRefreshPolicy());
        assertNotNull(esRequest, "带选项的 IndexRequest 应构建成功");
    }

    @Test
    @DisplayName("buildUpdateRequest：doc 字段更新")
    void buildUpdateRequestDocFields() {
        Map<String, Object> fieldMap = new LinkedHashMap<>();
        fieldMap.put("status", "done");
        UpdateRequest request = UpdateRequest.builder().id("id-4").fieldMap(fieldMap).build();
        org.elasticsearch.action.update.UpdateRequest esRequest =
                PersistenceEsRequestHelper.buildUpdateRequest(request, TEST_INDEX);
        log.info("esRequest index={}, id={}", esRequest.index(), esRequest.id());
        assertEquals(TEST_INDEX, esRequest.index(), "索引名应与传入一致");
        assertEquals("id-4", esRequest.id(), "id 应与传入一致");
    }

    @Test
    @DisplayName("buildUpdateRequest：script 更新")
    void buildUpdateRequestScript() {
        UpdateRequest request = UpdateRequest.builder().id("id-5").scriptSource("ctx._source.status='done'").build();
        org.elasticsearch.action.update.UpdateRequest esRequest =
                PersistenceEsRequestHelper.buildUpdateRequest(request, TEST_INDEX);
        log.info("esRequest id={}, script={}", esRequest.id(), esRequest.script());
        assertEquals("id-5", esRequest.id(), "id 应与传入一致");
        assertNotNull(esRequest.script(), "script 应被设置");
    }

    @Test
    @DisplayName("buildUpdateRequest：scriptedUpsert + upsertDoc 透传")
    void buildUpdateRequestScriptedUpsert() {
        Map<String, Object> scriptParams = new LinkedHashMap<String, Object>();
        scriptParams.put("t", 1000L);
        UpdateRequest request = UpdateRequest.builder()
                .id("id-su-1")
                .scriptSource("if (ctx._source.createTime == null) { ctx._source.createTime = params.t } ctx._source.updateTime = params.t")
                .scriptParamMap(scriptParams)
                .options(UpdateOptions.builder()
                        .scriptedUpsert(true)
                        .upsertDoc(Collections.emptyMap())
                        .build())
                .build();
        org.elasticsearch.action.update.UpdateRequest esRequest =
                PersistenceEsRequestHelper.buildUpdateRequest(request, TEST_INDEX);
        log.info("esRequest id={}, script={}, scriptedUpsert={}, upsertRequest={}",
                esRequest.id(), esRequest.script(), esRequest.scriptedUpsert(), esRequest.upsertRequest());
        assertEquals("id-su-1", esRequest.id(), "id 应与传入一致");
        assertNotNull(esRequest.script(), "script 应被设置");
        assertTrue(esRequest.scriptedUpsert(), "scriptedUpsert 应为 true");
        assertNotNull(esRequest.upsertRequest(), "upsertRequest 应被设置");
    }

    @Test
    @DisplayName("buildDeleteRequest：基础删除请求")
    void buildDeleteRequestBasic() {
        DeleteRequest request = DeleteRequest.builder().id("id-6").build();
        org.elasticsearch.action.delete.DeleteRequest esRequest =
                PersistenceEsRequestHelper.buildDeleteRequest(request, TEST_INDEX);
        log.info("esRequest index={}, id={}", esRequest.index(), esRequest.id());
        assertEquals(TEST_INDEX, esRequest.index(), "索引名应与传入一致");
        assertEquals("id-6", esRequest.id(), "id 应与传入一致");
    }

    @Test
    @DisplayName("buildBulkRequest：混合 index/delete item")
    void buildBulkRequestMixedItems() {
        HelperDoc doc = new HelperDoc();
        doc.setId("bulk-1");
        List<BulkItem> items = new ArrayList<>();
        items.add(BulkItem.builder().type(BulkItemType.INDEX).document(doc).id("bulk-1").build());
        items.add(BulkItem.builder().type(BulkItemType.DELETE).id("bulk-2").build());
        BulkRequest request = BulkRequest.builder().itemList(items).defaultIndex(TEST_INDEX).build();
        org.elasticsearch.action.bulk.BulkRequest esRequest =
                PersistenceEsRequestHelper.buildBulkRequest(request, Collections.singletonList(TEST_INDEX));
        log.info("esRequest items={}", esRequest.requests().size());
        assertEquals(2, esRequest.requests().size(), "应包含 2 个 bulk item");
    }

    @Test
    @DisplayName("buildQuery：term + range 拼 bool 查询")
    void buildQueryTermAndRange() {
        Map<String, Object> termMap = new LinkedHashMap<>();
        termMap.put("status", "done");
        Map<String, Object> rangeMap = new LinkedHashMap<>();
        rangeMap.put("ts", 100);
        PersistenceQuery query = PersistenceQuery.builder().termMap(termMap).rangeMap(rangeMap).build();
        QueryBuilder qb = PersistenceEsRequestHelper.buildQuery(query);
        log.info("queryBuilder={}", qb);
        assertNotNull(qb, "QueryBuilder 不应为空");
    }

    @Test
    @DisplayName("buildQuery：rawJson 走 wrapper 查询")
    void buildQueryRawJson() {
        PersistenceQuery query = PersistenceQuery.builder().rawJson("{\"match_all\":{}}").build();
        QueryBuilder qb = PersistenceEsRequestHelper.buildQuery(query);
        log.info("queryBuilder={}", qb);
        assertNotNull(qb, "QueryBuilder 不应为空");
    }

    @Test
    @DisplayName("buildIndexRequest：routing + pipeline + refreshPolicy 透传")
    void buildIndexRequestRoutingPipelineRefreshPolicy() {
        HelperDoc doc = new HelperDoc();
        doc.setId("id-rp");
        IndexRequest request = IndexRequest.builder().document(doc).id("id-rp")
                .options(IndexOptions.builder()
                        .routing("route-1")
                        .pipeline("pipe-1")
                        .refreshPolicy(SimpleElasticsearchPersistenceCoreConstant.REFRESH_POLICY_WAIT_FOR)
                        .build())
                .build();
        org.elasticsearch.action.index.IndexRequest esRequest =
                PersistenceEsRequestHelper.buildIndexRequest(request, TEST_INDEX);
        assertEquals("route-1", esRequest.routing(), "routing 应透传");
        assertEquals("pipe-1", esRequest.getPipeline(), "pipeline 应透传");
        assertEquals(WriteRequest.RefreshPolicy.WAIT_UNTIL, esRequest.getRefreshPolicy(),
                "refreshPolicy=wait_for 应映射为 WAIT_UNTIL");
    }

    @Test
    @DisplayName("buildIndexRequest：refresh=true 映射 IMMEDIATE，refresh=false 映射 NONE")
    void buildIndexRequestRefreshBooleanMapping() {
        HelperDoc doc = new HelperDoc();
        doc.setId("id-rb");
        IndexRequest requestTrue = IndexRequest.builder().document(doc).id("id-rb")
                .options(IndexOptions.builder().refresh(true).build()).build();
        org.elasticsearch.action.index.IndexRequest esTrue =
                PersistenceEsRequestHelper.buildIndexRequest(requestTrue, TEST_INDEX);
        assertEquals(WriteRequest.RefreshPolicy.IMMEDIATE, esTrue.getRefreshPolicy(), "refresh=true -> IMMEDIATE");

        IndexRequest requestFalse = IndexRequest.builder().document(doc).id("id-rb")
                .options(IndexOptions.builder().refresh(false).build()).build();
        org.elasticsearch.action.index.IndexRequest esFalse =
                PersistenceEsRequestHelper.buildIndexRequest(requestFalse, TEST_INDEX);
        assertEquals(WriteRequest.RefreshPolicy.NONE, esFalse.getRefreshPolicy(), "refresh=false -> NONE");
    }

    @Test
    @DisplayName("buildUpdateRequest：routing + retryOnConflict + detectNoop 透传")
    void buildUpdateRequestRoutingRetryDetectNoop() {
        UpdateRequest request = UpdateRequest.builder().id("id-u")
                .fieldMap(Collections.singletonMap("status", "done"))
                .options(UpdateOptions.builder()
                        .routing("route-u")
                        .retryOnConflict(3)
                        .detectNoop(true)
                        .build())
                .build();
        org.elasticsearch.action.update.UpdateRequest esRequest =
                PersistenceEsRequestHelper.buildUpdateRequest(request, TEST_INDEX);
        assertEquals("route-u", esRequest.routing(), "routing 应透传");
        assertEquals(3, esRequest.retryOnConflict(), "retryOnConflict 应透传");
        assertTrue(esRequest.detectNoop(), "detectNoop 应透传为 true");
    }

    @Test
    @DisplayName("buildDeleteRequest：routing + version + versionType 透传")
    void buildDeleteRequestRoutingVersionVersionType() {
        DeleteRequest request = DeleteRequest.builder().id("id-d")
                .options(DeleteOptions.builder()
                        .routing("route-d")
                        .version(10L)
                        .versionType("external")
                        .build())
                .build();
        org.elasticsearch.action.delete.DeleteRequest esRequest =
                PersistenceEsRequestHelper.buildDeleteRequest(request, TEST_INDEX);
        assertEquals("route-d", esRequest.routing(), "routing 应透传");
        assertEquals(10L, esRequest.version(), "version 应透传");
        assertEquals(VersionType.EXTERNAL, esRequest.versionType(), "versionType=external 应映射为 EXTERNAL");
    }

    @Test
    @DisplayName("buildBulkRequest：item 级 pipeline 覆盖 request 级 pipeline")
    void buildBulkRequestItemPipelineOverridesRequestPipeline() {
        HelperDoc doc = new HelperDoc();
        doc.setId("bp-1");
        List<BulkItem> items = new ArrayList<>();
        items.add(BulkItem.builder().type(BulkItemType.INDEX).document(doc).id("bp-1").pipeline("item-pipe").build());
        BulkRequest request = BulkRequest.builder().itemList(items).defaultIndex(TEST_INDEX)
                .options(BulkOptions.builder().pipeline("req-pipe").build()).build();
        org.elasticsearch.action.bulk.BulkRequest esRequest =
                PersistenceEsRequestHelper.buildBulkRequest(request, Collections.singletonList(TEST_INDEX));
        assertNull(esRequest.pipeline(), "有 item 级 pipeline 时不应设 request 级 pipeline");
        org.elasticsearch.action.index.IndexRequest itemReq =
                (org.elasticsearch.action.index.IndexRequest) esRequest.requests().get(0);
        assertEquals("item-pipe", itemReq.getPipeline(), "item 级 pipeline 应生效");
    }

    @Test
    @DisplayName("buildBulkRequest：无 item 级 pipeline 时 request 级 pipeline 生效")
    void buildBulkRequestRequestPipelineWhenNoItemPipeline() {
        HelperDoc doc = new HelperDoc();
        doc.setId("bp-2");
        List<BulkItem> items = new ArrayList<>();
        items.add(BulkItem.builder().type(BulkItemType.INDEX).document(doc).id("bp-2").build());
        BulkRequest request = BulkRequest.builder().itemList(items).defaultIndex(TEST_INDEX)
                .options(BulkOptions.builder().pipeline("req-pipe").build()).build();
        org.elasticsearch.action.bulk.BulkRequest esRequest =
                PersistenceEsRequestHelper.buildBulkRequest(request, Collections.singletonList(TEST_INDEX));
        assertEquals("req-pipe", esRequest.pipeline(), "无 item 级 pipeline 时 request 级 pipeline 应生效");
    }

    @Test
    @DisplayName("buildBulkRequest：item 级 routing 覆盖 options 级 routing")
    void buildBulkRequestItemRoutingOverridesOptionsRouting() {
        HelperDoc doc = new HelperDoc();
        doc.setId("bp-3");
        List<BulkItem> items = new ArrayList<>();
        items.add(BulkItem.builder().type(BulkItemType.INDEX).document(doc).id("bp-3").routing("item-route").build());
        BulkRequest request = BulkRequest.builder().itemList(items).defaultIndex(TEST_INDEX)
                .options(BulkOptions.builder().routing("opt-route").build()).build();
        org.elasticsearch.action.bulk.BulkRequest esRequest =
                PersistenceEsRequestHelper.buildBulkRequest(request, Collections.singletonList(TEST_INDEX));
        org.elasticsearch.action.DocWriteRequest<?> itemReq = esRequest.requests().get(0);
        assertEquals("item-route", itemReq.routing(), "item 级 routing 应覆盖 options 级");
    }
}
