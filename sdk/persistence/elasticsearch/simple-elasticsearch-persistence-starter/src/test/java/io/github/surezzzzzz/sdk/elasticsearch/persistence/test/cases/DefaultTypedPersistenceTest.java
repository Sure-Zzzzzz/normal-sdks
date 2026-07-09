package io.github.surezzzzzz.sdk.elasticsearch.persistence.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.BulkItemType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.IndexOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.SimpleElasticsearchPersistenceCoreConstant;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.BulkOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.IndexOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkItem;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.IndexRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.DefaultPersistenceEngine;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.DefaultTypedPersistence;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.TypedPersistence;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.ConflictUpdateResolver;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.CreateThenUpdateOnConflictHelper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DefaultTypedPersistence 单元测试
 *
 * <p>用捕获型 fake engine 验证 options 合并、强制 CREATE、routingResolver、bulkCreate，
 * 不连真实 ES。
 *
 * @author surezzzzzz
 */
@Slf4j
class DefaultTypedPersistenceTest {

    @Data
    @Document(indexName = "test_typed_doc")
    static class TypedDoc {
        @Id
        private String id;
        private String name;
    }

    @Test
    @DisplayName("create 强制 CREATE：即便 defaultIndexOptions.operationType=INDEX 也被覆盖为 CREATE")
    void createForcesCreateOperationType() {
        CapturingEngine engine = new CapturingEngine();
        IndexOptions defaults = IndexOptions.builder().operationType(IndexOperationType.INDEX).build();
        TypedPersistence<TypedDoc> typed = engine.forEntity(TypedDoc.class)
                .withDefaultIndexOptions(defaults);

        TypedDoc doc = new TypedDoc();
        doc.setId("id-1");
        doc.setName("n");
        typed.create(doc);

        IndexRequest captured = engine.lastIndexRequest;
        assertNotNull(captured.getOptions(), "options 不应为空");
        assertEquals(IndexOperationType.CREATE, captured.getOptions().getOperationType(),
                "create 必须强制 CREATE，default options 不得覆盖");
    }

    @Test
    @DisplayName("index 默认走 INDEX，defaultIndexOptions 不含 operationType 时不覆盖")
    void indexUsesIndexOperationType() {
        CapturingEngine engine = new CapturingEngine();
        TypedPersistence<TypedDoc> typed = engine.forEntity(TypedDoc.class)
                .withDefaultIndexOptions(IndexOptions.builder().refresh(true).build());

        TypedDoc doc = new TypedDoc();
        doc.setId("id-2");
        typed.index(doc);

        IndexRequest captured = engine.lastIndexRequest;
        assertEquals(IndexOperationType.INDEX, captured.getOptions().getOperationType(),
                "index 应为 INDEX");
        assertTrue(captured.getOptions().getRefresh(), "default refresh 应被合并");
    }

    @Test
    @DisplayName("显式 IndexOptions 覆盖 defaultIndexOptions")
    void explicitOptionsOverrideDefaults() {
        CapturingEngine engine = new CapturingEngine();
        TypedPersistence<TypedDoc> typed = engine.forEntity(TypedDoc.class)
                .withDefaultIndexOptions(IndexOptions.builder().refresh(true).timeoutMs(1000L).build());

        TypedDoc doc = new TypedDoc();
        doc.setId("id-3");
        typed.index(doc, IndexOptions.builder().refresh(false).build());

        IndexRequest captured = engine.lastIndexRequest;
        assertEquals(Boolean.FALSE, captured.getOptions().getRefresh(), "显式 refresh 应覆盖默认");
        assertEquals(Long.valueOf(1000L), captured.getOptions().getTimeoutMs(), "未覆盖字段应保留默认");
    }

    @Test
    @DisplayName("withRoutingResolver 生效，且显式 options.routing 覆盖 routingResolver")
    void routingResolverAndExplicitRouting() {
        CapturingEngine engine = new CapturingEngine();
        TypedPersistence<TypedDoc> typed = engine.forEntity(TypedDoc.class)
                .withRoutingResolver(d -> "resolved-routing");

        TypedDoc doc = new TypedDoc();
        doc.setId("id-4");
        typed.index(doc);
        assertEquals("resolved-routing", engine.lastIndexRequest.getOptions().getRouting(),
                "routingResolver 应注入 routing");

        typed.index(doc, IndexOptions.builder().routing("explicit-routing").build());
        assertEquals("explicit-routing", engine.lastIndexRequest.getOptions().getRouting(),
                "显式 options.routing 应覆盖 routingResolver");
    }

    @Test
    @DisplayName("bulkCreate 构建 CREATE item，bulkIndex 构建 INDEX item")
    void bulkCreateAndBulkIndexItemType() {
        CapturingEngine engine = new CapturingEngine();
        TypedPersistence<TypedDoc> typed = engine.forEntity(TypedDoc.class);

        TypedDoc a = new TypedDoc();
        a.setId("a");
        TypedDoc b = new TypedDoc();
        b.setId("b");

        typed.bulkIndex(Arrays.asList(a, b));
        assertEquals(BulkItemType.INDEX, engine.lastBulkRequest.getItemList().get(0).getType(),
                "bulkIndex item 应为 INDEX");
        assertNull(engine.lastBulkRequest.getItemList().get(0).getRouting(), "无 routingResolver 时 routing 为空");

        typed.bulkCreate(Arrays.asList(a, b));
        assertEquals(BulkItemType.CREATE, engine.lastBulkRequest.getItemList().get(0).getType(),
                "bulkCreate item 应为 CREATE");
    }

    @Test
    @DisplayName("withDefaultBulkOptions 生效，显式 options 覆盖")
    void defaultBulkOptionsMerge() {
        CapturingEngine engine = new CapturingEngine();
        TypedPersistence<TypedDoc> typed = engine.forEntity(TypedDoc.class)
                .withDefaultBulkOptions(BulkOptions.builder().batchSize(50).continueOnFailure(true).build());

        TypedDoc a = new TypedDoc();
        a.setId("a");
        typed.bulkIndex(Arrays.asList(a));
        assertEquals(Integer.valueOf(50), engine.lastBulkRequest.getOptions().getBatchSize(),
                "default bulk batchSize 应生效");

        typed.bulkIndex(Arrays.asList(a), BulkOptions.builder().batchSize(10).build());
        assertEquals(Integer.valueOf(10), engine.lastBulkRequest.getOptions().getBatchSize(),
                "显式 batchSize 应覆盖默认");
        assertTrue(engine.lastBulkRequest.getOptions().getContinueOnFailure(),
                "未覆盖字段应保留默认");
    }

    @Test
    @DisplayName("typed createThenUpdateOnConflict 构建 CREATE request 并补齐 UPDATE index/id/routing")
    void typedCreateThenUpdateOnConflictBuildsRequests() {
        CapturingEngine engine = new CapturingEngine();
        TypedPersistence<TypedDoc> typed = engine.forEntity(TypedDoc.class)
                .withIndexResolver(d -> "resolved_index")
                .withIdResolver(TypedDoc::getId)
                .withRoutingResolver(d -> "routing-" + d.getId());

        TypedDoc doc = new TypedDoc();
        doc.setId("id-5");
        doc.setName("n5");
        typed.createThenUpdateOnConflict(doc, d -> UpdateRequest.builder()
                .fieldMap(java.util.Collections.singletonMap("extraField", "v5"))
                .build());

        assertNotNull(engine.lastCreateRequest, "应捕获 CREATE request");
        assertNotNull(engine.lastUpdateRequest, "应捕获 UPDATE request");
        assertEquals(IndexOperationType.CREATE, engine.lastCreateRequest.getOptions().getOperationType(),
                "CREATE request 应强制 CREATE");
        assertEquals("resolved_index", engine.lastCreateRequest.getIndex(), "CREATE index 应来自 resolver");
        assertEquals("id-5", engine.lastCreateRequest.getId(), "CREATE id 应来自 resolver");
        assertEquals("routing-id-5", engine.lastCreateRequest.getOptions().getRouting(),
                "CREATE routing 应来自 resolver");
        assertEquals("resolved_index", engine.lastUpdateRequest.getIndex(), "UPDATE index 应自动补齐");
        assertEquals("id-5", engine.lastUpdateRequest.getId(), "UPDATE id 应自动补齐");
        assertEquals("routing-id-5", engine.lastUpdateRequest.getOptions().getRouting(),
                "UPDATE routing 应自动补齐");
    }

    @Test
    @DisplayName("typed bulkCreateThenUpdateOnConflict 构建 CREATE bulk 并按原始 document 生成 UPDATE item")
    void typedBulkCreateThenUpdateOnConflictBuildsResolver() {
        CapturingEngine engine = new CapturingEngine();
        TypedPersistence<TypedDoc> typed = engine.forEntity(TypedDoc.class)
                .withIndexResolver(d -> "idx-" + d.getId())
                .withIdResolver(TypedDoc::getId)
                .withRoutingResolver(d -> "routing-" + d.getId())
                .withDefaultBulkOptions(BulkOptions.builder().batchSize(20).pipeline("create-pipeline").build());

        TypedDoc a = new TypedDoc();
        a.setId("a");
        TypedDoc b = new TypedDoc();
        b.setId("b");
        typed.bulkCreateThenUpdateOnConflict(Arrays.asList(a, b), d -> BulkItem.builder()
                .fieldMap(java.util.Collections.singletonMap("extraField", "value-" + d.getId()))
                .build(), null);

        assertEquals(2, engine.bulkRequestList.size(), "应执行 CREATE 与 UPDATE 两阶段 bulk");
        BulkRequest createRequest = engine.bulkRequestList.get(0);
        assertEquals(Integer.valueOf(20), createRequest.getOptions().getBatchSize(),
                "default bulk options 应生效");
        assertEquals("create-pipeline", createRequest.getOptions().getPipeline(),
                "CREATE 阶段 pipeline 应保留");
        assertEquals(BulkItemType.CREATE, createRequest.getItemList().get(0).getType(),
                "typed bulk helper 应构建 CREATE item");
        assertEquals("idx-a", createRequest.getItemList().get(0).getIndex(), "index 应来自 resolver");
        assertEquals("routing-a", createRequest.getItemList().get(0).getRouting(), "routing 应来自 resolver");

        BulkItem updateItem = engine.bulkRequestList.get(1).getItemList().get(0);
        assertEquals(BulkItemType.UPDATE, updateItem.getType(), "resolver 结果应转为 UPDATE");
        assertEquals("idx-b", updateItem.getIndex(), "UPDATE index 应从 create item 补齐");
        assertEquals("b", updateItem.getId(), "UPDATE id 应从 create item 补齐");
        assertEquals("routing-b", updateItem.getRouting(), "UPDATE routing 应从 create item 补齐");
        assertEquals("value-b", updateItem.getFieldMap().get("extraField"),
                "UPDATE 字段应按原始 document 生成");
    }

    /** 捕获型 fake engine，记录最后一次 index/bulk 的 request，不连 ES。 */
    static class CapturingEngine extends DefaultPersistenceEngine {
        IndexRequest lastIndexRequest;
        IndexRequest lastCreateRequest;
        UpdateRequest lastUpdateRequest;
        BulkRequest lastBulkRequest;
        List<BulkRequest> bulkRequestList = new ArrayList<>();
        ConflictUpdateResolver capturedResolver;

        CapturingEngine() {
            super(null, Runnable::run);
        }

        @Override
        public PersistenceResult index(IndexRequest request) {
            lastIndexRequest = request;
            return PersistenceResult.builder().success(true).build();
        }

        @Override
        public PersistenceResult create(IndexRequest request) {
            lastIndexRequest = request;
            lastCreateRequest = request;
            return PersistenceResult.builder().success(true).build();
        }

        @Override
        public PersistenceResult update(UpdateRequest request) {
            lastUpdateRequest = request;
            return PersistenceResult.builder().success(true).build();
        }

        @Override
        public BulkResult bulk(BulkRequest request) {
            lastBulkRequest = request;
            bulkRequestList.add(request);
            if (capturedResolver != null && bulkRequestList.size() == 1) {
                return BulkResult.builder()
                        .success(false)
                        .hasFailure(true)
                        .total(request.getItemList().size())
                        .succeeded(request.getItemList().size() - 1)
                        .failed(1)
                        .failureList(Arrays.asList(io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkItemFailure.builder()
                                .itemIndex(1)
                                .type(BulkItemType.CREATE)
                                .status(SimpleElasticsearchPersistenceCoreConstant.HTTP_STATUS_CONFLICT)
                                .build()))
                        .build();
            }
            return BulkResult.builder()
                    .success(true)
                    .total(request.getItemList().size())
                    .succeeded(request.getItemList().size())
                    .failureList(new ArrayList<>())
                    .build();
        }

        @Override
        public CompletableFuture<BulkResult> bulkAsync(BulkRequest request) {
            return CompletableFuture.completedFuture(bulk(request));
        }

        @Override
        public PersistenceResult createThenUpdateOnConflict(IndexRequest createRequest, UpdateRequest updateRequest) {
            lastCreateRequest = createRequest;
            lastUpdateRequest = updateRequest;
            return PersistenceResult.builder().success(true).build();
        }

        @Override
        public BulkResult bulkCreateThenUpdateOnConflict(BulkRequest createRequest, ConflictUpdateResolver resolver) {
            lastBulkRequest = createRequest;
            capturedResolver = resolver;
            return CreateThenUpdateOnConflictHelper.bulkCreateThenUpdateOnConflict(this, createRequest, resolver);
        }
    }
}
