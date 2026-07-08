package io.github.surezzzzzz.sdk.elasticsearch.persistence.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.BulkItemType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.exception.SimpleElasticsearchPersistenceException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.IndexOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.UpdateOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.query.PersistenceQuery;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.*;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.ByQueryTaskResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.PersistenceEngine;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.test.EsApiHelper;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.test.PersistenceTestProfilesResolver;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.test.SimpleElasticsearchPersistenceTestApplication;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PersistenceEngine 端到端测试
 *
 * <p>4 版本闭环（2.7.9/2.4.5/2.3.12/2.2.x），通过 {@link PersistenceTestProfilesResolver}
 * 激活对应 profile，连 secondary 数据源（对应版本 ES）。
 * 覆盖 index/create/update/delete/bulk golden path + 选项透传 + 路由错误，
 * 写入后通过 RestClient 查回验证全字段。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchPersistenceTestApplication.class)
@ActiveProfiles(resolver = PersistenceTestProfilesResolver.class)
class PersistenceEngineIntegrationTest {

    private static final String DATASOURCE = "secondary";
    private static final String INDEX = "test_persistence_e2e";

    @Autowired
    private PersistenceEngine engine;

    @Autowired
    private SimpleElasticsearchRouteRegistry registry;

    private void assumeByQueryCompatible() {
        // ES 6.8.x client 会自动带 ignore_throttled；ES 6.2.2 服务端不识别该参数。
        Assumptions.assumeFalse("2.2.x".equals(System.getProperty("spring.profiles.active")));
    }

    @Data
    @Document(indexName = "test_persistence_e2e")
    static class TestDoc {
        @Id
        private String id;
        private String name;
        private long ts;
    }

    @Data
    @Document(indexName = "test_persistence_primary_fail")
    static class PrimaryFailDoc {
        @Id
        private String id;
        private String name;
    }

    @Test
    @DisplayName("index：写入文档可查回，result 字段齐全")
    void testIndexAndFetch() throws Exception {
        TestDoc doc = new TestDoc();
        doc.setId("e2e-index-1");
        doc.setName("name-1");
        doc.setTs(100L);

        PersistenceResult result = engine.index(doc);
        log.info("index 结果={}", result);
        assertTrue(result.isSuccess(), "index 应成功");
        assertEquals("e2e-index-1", result.getId(), "result.id 应与文档 id 一致");
        assertEquals(DATASOURCE, result.getDatasource(), "result.datasource 应为 secondary");

        EsApiHelper.refreshIndex(secondaryClient(), INDEX);
        Map<String, Object> saved = EsApiHelper.getDoc(secondaryClient(), INDEX, "e2e-index-1");
        log.info("查回 source={}", saved);
        assertNotNull(saved, "文档应存在");
        assertEquals("e2e-index-1", saved.get("id"), "id 字段应一致");
        assertEquals("name-1", saved.get("name"), "name 字段应一致");
        assertEquals(100L, ((Number) saved.get("ts")).longValue(), "ts 字段应一致");
    }

    @Test
    @DisplayName("index：IndexOptions refresh=true 后立即可查")
    void testIndexWithOptions() throws Exception {
        TestDoc doc = new TestDoc();
        doc.setId("e2e-index-2");
        doc.setName("name-2");
        doc.setTs(200L);

        IndexOptions options = IndexOptions.builder().timeoutMs(5000L).refresh(true).build();
        PersistenceResult result = engine.index(doc, options);
        log.info("index with options 结果={}", result);
        assertTrue(result.isSuccess(), "带选项的 index 应成功");

        Map<String, Object> saved = EsApiHelper.getDoc(secondaryClient(), INDEX, "e2e-index-2");
        log.info("查回 source={}", saved);
        assertNotNull(saved, "refresh=true 后文档应立即可查");
        assertEquals("name-2", saved.get("name"), "name 字段应一致");
    }

    @Test
    @DisplayName("create：首次成功，重复 id 抛 EXECUTION_FAILED")
    void testCreateDuplicateFails() {
        TestDoc doc = new TestDoc();
        doc.setId("e2e-create-1");
        doc.setName("create-1");

        PersistenceResult r1 = engine.create(doc);
        log.info("首次 create 结果={}", r1);
        assertTrue(r1.isSuccess(), "首次 create 应成功");

        SimpleElasticsearchPersistenceException ex = assertThrows(
                SimpleElasticsearchPersistenceException.class, () -> engine.create(doc));
        log.info("重复 create 异常 errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.EXECUTION_FAILED, ex.getErrorCode(), "重复 id 的 create 应抛 EXECUTION_FAILED");
    }

    @Test
    @DisplayName("update：doc 字段局部更新，未传字段保留")
    void testUpdateDocFields() throws Exception {
        TestDoc doc = new TestDoc();
        doc.setId("e2e-update-1");
        doc.setName("orig");
        doc.setTs(1L);
        engine.index(doc);
        EsApiHelper.refreshIndex(secondaryClient(), INDEX);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", "updated");
        UpdateRequest req = UpdateRequest.builder().index(INDEX).id("e2e-update-1").fieldMap(fields).build();
        PersistenceResult result = engine.update(req);
        log.info("update 结果={}", result);
        assertTrue(result.isSuccess(), "update 应成功");

        EsApiHelper.refreshIndex(secondaryClient(), INDEX);
        Map<String, Object> saved = EsApiHelper.getDoc(secondaryClient(), INDEX, "e2e-update-1");
        log.info("查回 source={}", saved);
        assertEquals("updated", saved.get("name"), "name 应被更新为 updated");
        assertEquals(1L, ((Number) saved.get("ts")).longValue(), "ts 应保留原值");
    }

    @Test
    @DisplayName("update：script 更新生效")
    void testUpdateScript() throws Exception {
        TestDoc doc = new TestDoc();
        doc.setId("e2e-update-2");
        doc.setName("orig");
        doc.setTs(10L);
        engine.index(doc);
        EsApiHelper.refreshIndex(secondaryClient(), INDEX);

        UpdateRequest req = UpdateRequest.builder().index(INDEX).id("e2e-update-2")
                .scriptSource("ctx._source.ts = ctx._source.ts + 5").build();
        PersistenceResult result = engine.update(req);
        log.info("script update 结果={}", result);
        assertTrue(result.isSuccess(), "script update 应成功");

        EsApiHelper.refreshIndex(secondaryClient(), INDEX);
        Map<String, Object> saved = EsApiHelper.getDoc(secondaryClient(), INDEX, "e2e-update-2");
        log.info("查回 source={}", saved);
        assertEquals(15L, ((Number) saved.get("ts")).longValue(), "ts 应被 script 加 5");
    }

    @Test
    @DisplayName("update：scriptedUpsert：文档不存在时脚本初始化，已存在时只更新 updateTime")
    void testScriptedUpsert() throws Exception {
        // 第一次：文档不存在，脚本初始化 createTime + updateTime
        long t1 = 1000L;
        Map<String, Object> params1 = new LinkedHashMap<String, Object>();
        params1.put("t", t1);
        UpdateRequest req1 = UpdateRequest.builder()
                .index(INDEX).id("e2e-scripted-upsert-1")
                .scriptSource(
                        "if (ctx._source.createTime == null) { ctx._source.createTime = params.t } " +
                                "ctx._source.updateTime = params.t")
                .scriptParamMap(params1)
                .options(UpdateOptions.builder()
                        .scriptedUpsert(true)
                        .upsertDoc(Collections.emptyMap())
                        .build())
                .build();
        PersistenceResult r1 = engine.update(req1);
        log.info("scriptedUpsert 首次结果={}", r1);
        assertTrue(r1.isSuccess(), "scriptedUpsert 首次写入应成功");

        EsApiHelper.refreshIndex(secondaryClient(), INDEX);
        Map<String, Object> saved1 = EsApiHelper.getDoc(secondaryClient(), INDEX, "e2e-scripted-upsert-1");
        log.info("首次查回 source={}", saved1);
        assertEquals(t1, ((Number) saved1.get("createTime")).longValue(), "createTime 应被初始化");
        assertEquals(t1, ((Number) saved1.get("updateTime")).longValue(), "updateTime 应被初始化");

        // 第二次：文档已存在，createTime 应保留，updateTime 应更新
        long t2 = 2000L;
        Map<String, Object> params2 = new LinkedHashMap<String, Object>();
        params2.put("t", t2);
        UpdateRequest req2 = UpdateRequest.builder()
                .index(INDEX).id("e2e-scripted-upsert-1")
                .scriptSource(
                        "if (ctx._source.createTime == null) { ctx._source.createTime = params.t } " +
                                "ctx._source.updateTime = params.t")
                .scriptParamMap(params2)
                .options(UpdateOptions.builder()
                        .scriptedUpsert(true)
                        .upsertDoc(Collections.emptyMap())
                        .build())
                .build();
        PersistenceResult r2 = engine.update(req2);
        log.info("scriptedUpsert 再次结果={}", r2);
        assertTrue(r2.isSuccess(), "scriptedUpsert 再次写入应成功");

        EsApiHelper.refreshIndex(secondaryClient(), INDEX);
        Map<String, Object> saved2 = EsApiHelper.getDoc(secondaryClient(), INDEX, "e2e-scripted-upsert-1");
        log.info("再次查回 source={}", saved2);
        assertEquals(t1, ((Number) saved2.get("createTime")).longValue(), "createTime 应保留首次写入的值");
        assertEquals(t2, ((Number) saved2.get("updateTime")).longValue(), "updateTime 应更新为第二次的值");
    }

    @Test
    @DisplayName("delete：删除后文档不存在")
    void testDelete() throws Exception {
        TestDoc doc = new TestDoc();
        doc.setId("e2e-delete-1");
        doc.setName("delete-1");
        engine.index(doc);
        EsApiHelper.refreshIndex(secondaryClient(), INDEX);
        assertTrue(EsApiHelper.docExists(secondaryClient(), INDEX, "e2e-delete-1"), "删除前文档应存在");

        DeleteRequest req = DeleteRequest.builder().index(INDEX).id("e2e-delete-1").build();
        PersistenceResult result = engine.delete(req);
        log.info("delete 结果={}", result);
        assertTrue(result.isSuccess(), "delete 应成功");

        EsApiHelper.refreshIndex(secondaryClient(), INDEX);
        assertFalse(EsApiHelper.docExists(secondaryClient(), INDEX, "e2e-delete-1"), "删除后文档应不存在");
    }

    @Test
    @DisplayName("bulk：混合 index + delete，result 统计准确")
    void testBulkMixed() throws Exception {
        TestDoc doc1 = new TestDoc();
        doc1.setId("e2e-bulk-1");
        doc1.setName("bulk-1");
        TestDoc doc2 = new TestDoc();
        doc2.setId("e2e-bulk-2");
        doc2.setName("bulk-2");
        engine.index(doc2);
        EsApiHelper.refreshIndex(secondaryClient(), INDEX);

        List<BulkItem> items = new ArrayList<>();
        items.add(BulkItem.builder().type(BulkItemType.INDEX).document(doc1).id("e2e-bulk-1").build());
        items.add(BulkItem.builder().type(BulkItemType.DELETE).id("e2e-bulk-2").build());
        BulkRequest req = BulkRequest.builder().itemList(items).defaultIndex(INDEX).build();

        BulkResult result = engine.bulk(req);
        log.info("bulk 结果={}", result);
        assertTrue(result.isSuccess(), "bulk 应成功");
        assertFalse(result.isHasFailure(), "不应有失败项");
        assertEquals(2, result.getTotal(), "total 应为 2");
        assertEquals(2, result.getSucceeded(), "succeeded 应为 2");
        assertEquals(0, result.getFailed(), "failed 应为 0");

        EsApiHelper.refreshIndex(secondaryClient(), INDEX);
        assertTrue(EsApiHelper.docExists(secondaryClient(), INDEX, "e2e-bulk-1"), "bulk index 文档应存在");
        assertFalse(EsApiHelper.docExists(secondaryClient(), INDEX, "e2e-bulk-2"), "bulk delete 文档应不存在");
    }

    @Test
    @DisplayName("updateByQuery：按 term 条件批量更新字段，updated 计数准确，读回验证")
    void testUpdateByQuery() throws Exception {
        assumeByQueryCompatible();
        // 写入 3 条，其中 2 条 name=ubq-target
        TestDoc d1 = new TestDoc();
        d1.setId("e2e-ubq-1");
        d1.setName("ubq-target");
        d1.setTs(1L);
        TestDoc d2 = new TestDoc();
        d2.setId("e2e-ubq-2");
        d2.setName("ubq-target");
        d2.setTs(2L);
        TestDoc d3 = new TestDoc();
        d3.setId("e2e-ubq-3");
        d3.setName("ubq-other");
        d3.setTs(3L);
        engine.index(d1);
        engine.index(d2);
        engine.index(d3);
        EsApiHelper.refreshIndex(secondaryClient(), INDEX);

        ByQueryTaskResult result = engine.updateByQuery(
                UpdateByQueryRequest.builder()
                        .index(INDEX)
                        .query(PersistenceQuery.builder()
                                .termMap(Collections.<String, Object>singletonMap("name.keyword", "ubq-target"))
                                .build())
                        .scriptSource("ctx._source.ts = 99")
                        .build());
        log.info("updateByQuery 结果={}", result);
        assertTrue(result.isCompleted(), "同步 updateByQuery 应 completed=true");
        assertEquals(2L, result.getUpdated(), "应更新 2 条");
        assertEquals(0L, result.getVersionConflicts(), "不应有版本冲突");

        EsApiHelper.refreshIndex(secondaryClient(), INDEX);
        Map<String, Object> s1 = EsApiHelper.getDoc(secondaryClient(), INDEX, "e2e-ubq-1");
        Map<String, Object> s3 = EsApiHelper.getDoc(secondaryClient(), INDEX, "e2e-ubq-3");
        assertEquals(99L, ((Number) s1.get("ts")).longValue(), "ubq-1 的 ts 应被更新为 99");
        assertEquals(3L, ((Number) s3.get("ts")).longValue(), "ubq-3 的 ts 不应被修改");
    }

    @Test
    @DisplayName("deleteByQuery：按 term 条件批量删除，deleted 计数准确，读回验证")
    void testDeleteByQuery() throws Exception {
        assumeByQueryCompatible();
        // 写入 3 条，其中 2 条 name=dbq-target
        TestDoc d1 = new TestDoc();
        d1.setId("e2e-dbq-1");
        d1.setName("dbq-target");
        d1.setTs(1L);
        TestDoc d2 = new TestDoc();
        d2.setId("e2e-dbq-2");
        d2.setName("dbq-target");
        d2.setTs(2L);
        TestDoc d3 = new TestDoc();
        d3.setId("e2e-dbq-3");
        d3.setName("dbq-other");
        d3.setTs(3L);
        engine.index(d1);
        engine.index(d2);
        engine.index(d3);
        EsApiHelper.refreshIndex(secondaryClient(), INDEX);

        ByQueryTaskResult result = engine.deleteByQuery(
                DeleteByQueryRequest.builder()
                        .index(INDEX)
                        .query(PersistenceQuery.builder()
                                .termMap(Collections.<String, Object>singletonMap("name.keyword", "dbq-target"))
                                .build())
                        .build());
        log.info("deleteByQuery 结果={}", result);
        assertTrue(result.isCompleted(), "同步 deleteByQuery 应 completed=true");
        assertEquals(2L, result.getDeleted(), "应删除 2 条");

        EsApiHelper.refreshIndex(secondaryClient(), INDEX);
        assertFalse(EsApiHelper.docExists(secondaryClient(), INDEX, "e2e-dbq-1"), "dbq-1 应被删除");
        assertFalse(EsApiHelper.docExists(secondaryClient(), INDEX, "e2e-dbq-2"), "dbq-2 应被删除");
        assertTrue(EsApiHelper.docExists(secondaryClient(), INDEX, "e2e-dbq-3"), "dbq-3 应保留");
    }

    @Test
    @DisplayName("路由错误：写入 primary(9202) 抛 EXECUTION_FAILED")
    void testRoutingError() {
        PrimaryFailDoc doc = new PrimaryFailDoc();
        doc.setId("e2e-primary-fail-1");
        doc.setName("fail");

        SimpleElasticsearchPersistenceException ex = assertThrows(
                SimpleElasticsearchPersistenceException.class, () -> engine.index(doc));
        log.info("路由错误异常 errorCode={}, message={}, causeChain={}",
                ex.getErrorCode(), ex.getMessage(), buildCauseChain(ex));
        assertEquals(ErrorCode.EXECUTION_FAILED, ex.getErrorCode(), "路由到不存在的 primary 应抛 EXECUTION_FAILED");
    }

    @AfterEach
    void tearDown() {
        try {
            EsApiHelper.deleteIndex(secondaryClient(), INDEX);
        } catch (Exception e) {
            log.warn("清理索引 {} 失败: {}", INDEX, e.getMessage());
        }
    }

    private RestClient secondaryClient() {
        return registry.getLowLevelClient(DATASOURCE);
    }

    private String buildCauseChain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        while (cur != null) {
            if (cur.getMessage() != null) {
                sb.append(cur.getMessage()).append(" | ");
            }
            cur = cur.getCause();
        }
        return sb.toString();
    }
}
