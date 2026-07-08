package io.github.surezzzzzz.sdk.elasticsearch.persistence.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.BulkItemType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.IndexOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.IndexOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.UpdateOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.query.PersistenceQuery;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkItem;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.DeleteRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.IndexRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceEsRequestHelper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.index.query.QueryBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
