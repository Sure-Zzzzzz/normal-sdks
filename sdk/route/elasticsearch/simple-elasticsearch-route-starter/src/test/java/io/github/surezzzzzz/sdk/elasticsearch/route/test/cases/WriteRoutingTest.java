package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.EsApiHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.WriteTestProfilesResolver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 写入路由测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
@ActiveProfiles(resolver = WriteTestProfilesResolver.class)
public class WriteRoutingTest {

    @Autowired
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Autowired
    private SimpleElasticsearchRouteRegistry registry;

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @Document(indexName = "write_test_secondary")
    public static class WriteTestDocument {
        @Id
        private String id;

        @Field(type = FieldType.Keyword)
        private String testField;
    }

    @Test
    public void testCreateIndexRoutesToSecondary() {
        log.info("========== 测试创建索引是否正确路由到 secondary =========");
        try {
            String indexName = "write_test_secondary";
            org.elasticsearch.client.RestClient secondaryLow =
                    registry.getLowLevelClient("secondary");
            if (EsApiHelper.indexExists(secondaryLow, indexName)) {
                log.info("索引 {} 已存在,先删除", indexName);
                EsApiHelper.deleteIndex(secondaryLow, indexName);
            }

            EsApiHelper.createIndex(secondaryLow, indexName);

            boolean exists = EsApiHelper.indexExists(secondaryLow, indexName);
            log.info("索引创建后存在检查: {}", exists);
            assertTrue(exists, "索引应该创建成功");
        } catch (Exception e) {
            log.error("索引创建失败", e);
            assertNotPrimaryRouteError(e, "write_test_secondary");
            throw e;
        }
    }

    @Test
    public void testSaveDocumentRoutesToSecondary() throws Exception {
        log.info("========== 测试保存文档是否正确路由到 secondary =========");
        try {
            String indexName = "write_test_secondary";
            org.elasticsearch.client.RestClient secondaryLow =
                    registry.getLowLevelClient("secondary");
            if (!EsApiHelper.indexExists(secondaryLow, indexName)) {
                EsApiHelper.createIndex(secondaryLow, indexName);
                log.info("索引 {} 不存在,已创建", indexName);
            }
            WriteTestDocument doc = new WriteTestDocument("test-id-001", "test-value");

            Object saved = EsApiHelper.save(elasticsearchRestTemplate, doc);

            log.info("文档保存结果: {}", saved);
            assertNotNull(saved, "save() 不应返回 null");
            assertEquals("test-id-001", ((WriteTestDocument) saved).getId(), "文档 ID 应该匹配");

            Thread.sleep(500);
            Object readBack = EsApiHelper.get(elasticsearchRestTemplate, "test-id-001", WriteTestDocument.class);

            log.info("读回文档: {}", readBack);
            assertNotNull(readBack, "保存后应能从 secondary 读回文档");
            assertTrue(readBack instanceof WriteTestDocument, "读回类型应匹配");
            assertEquals("test-value", ((WriteTestDocument) readBack).getTestField(), "读回的字段值应与写入值匹配");
        } catch (Exception e) {
            log.error("文档保存失败", e);
            assertNotPrimaryRouteError(e, "write_test_secondary");
            throw e;
        }
    }

    @Test
    public void testIndexCoordinatesRoutesToSecondary() {
        log.info("========== 测试 test_index_b.secondary 是否正确路由到 secondary =========");
        try {
            String indexName = "test_index_b.secondary";
            org.elasticsearch.client.RestClient secondaryLow =
                    registry.getLowLevelClient("secondary");
            if (EsApiHelper.indexExists(secondaryLow, indexName)) {
                log.info("索引 {} 已存在,先删除", indexName);
                EsApiHelper.deleteIndex(secondaryLow, indexName);
            }

            EsApiHelper.createIndex(secondaryLow, indexName);

            boolean exists = EsApiHelper.indexExists(secondaryLow, indexName);
            log.info("索引创建后存在检查: {}", exists);
            assertTrue(exists, "索引应该创建成功");
        } catch (Exception e) {
            log.error("索引创建失败", e);
            assertNotPrimaryRouteError(e, "test_index_b.secondary");
            throw e;
        }
    }

    private void assertNotPrimaryRouteError(Exception e, String indexName) {
        String errorMsg = buildCauseChain(e);
        if (errorMsg.contains("9201") || errorMsg.contains("9202")) {
            fail("路由错误! 索引 " + indexName + " 应该路由到 secondary，"
                    + "但实际路由到了 primary。错误信息: " + errorMsg);
        }
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
