package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 写入路由测试
 *
 * <p><b>测试目的:</b> 验证往第二个数据源写入时,路由是否正确</p>
 *
 * <p><b>测试方案:</b></p>
 * <ul>
 *   <li>primary 数据源配置为不存在的端口 (localhost:9201)</li>
 *   <li>secondary 数据源配置为真实 ES (localhost:9200)</li>
 *   <li>往应该路由到 secondary 的索引写入数据</li>
 *   <li>如果成功 → 路由正确</li>
 *   <li>如果报错连接 9201 → 路由错误</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
@ActiveProfiles("write-test")
public class WriteRoutingTest {

    @Autowired
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    /**
     * 测试文档 - 应该路由到 secondary (9200)
     */
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

    /**
     * 测试1: 创建索引操作
     * 预期: 路由到 secondary (9200) 成功
     * 如果失败并报错连接 9201,说明路由错误
     */
    @Test
    public void testCreateIndexRoutesToSecondary() {
        log.info("========== 测试创建索引是否正确路由到 secondary =========");

        try {
            IndexOperations indexOps = elasticsearchRestTemplate.indexOps(WriteTestDocument.class);

            // 先删除索引(如果存在)
            if (indexOps.exists()) {
                log.info("索引 write_test_secondary 已存在,先删除");
                indexOps.delete();
            }

            // 创建索引
            log.info("开始创建索引 write_test_secondary");
            boolean created = indexOps.create();

            log.info("✓ 索引创建成功! 说明路由到了 secondary (9200)");
            assertTrue(created, "索引应该创建成功");

        } catch (Exception e) {
            log.error("❌ 索引创建失败!", e);

            // 检查是否是连接 9201 失败
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("9201")) {
                fail("路由错误! 索引 write_test_secondary 应该路由到 secondary (9200), " +
                     "但实际路由到了 primary (9201). 错误信息: " + errorMsg);
            } else {
                // 其他错误,直接抛出
                throw e;
            }
        }
    }

    /**
     * 测试2: 保存文档操作
     * 预期: 路由到 secondary (9200) 成功
     * 如果失败并报错连接 9201,说明路由错误
     */
    @Test
    public void testSaveDocumentRoutesToSecondary() {
        log.info("========== 测试保存文档是否正确路由到 secondary =========");

        try {
            // 确保索引存在
            IndexOperations indexOps = elasticsearchRestTemplate.indexOps(WriteTestDocument.class);
            if (!indexOps.exists()) {
                indexOps.create();
                log.info("索引 write_test_secondary 不存在,已创建");
            }

            // 保存文档
            WriteTestDocument doc = new WriteTestDocument("test-id-001", "test-value");
            log.info("开始保存文档到索引 write_test_secondary");

            WriteTestDocument saved = elasticsearchRestTemplate.save(doc);

            log.info("✓ 文档保存成功! 说明路由到了 secondary (9200)");
            assertNotNull(saved, "保存的文档不应该为空");
            assertEquals("test-id-001", saved.getId(), "文档ID应该匹配");

        } catch (Exception e) {
            log.error("❌ 文档保存失败!", e);

            // 检查是否是连接 9201 失败
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("9201")) {
                fail("路由错误! 索引 write_test_secondary 应该路由到 secondary (9200), " +
                     "但实际路由到了 primary (9201). 错误信息: " + errorMsg);
            } else {
                // 其他错误,直接抛出
                throw e;
            }
        }
    }

    /**
     * 测试3: 使用 IndexCoordinates 直接指定索引名
     * 测试 test_index_b.secondary (配置中的另一个规则)
     */
    @Test
    public void testIndexCoordinatesRoutesToSecondary() {
        log.info("========== 测试 IndexCoordinates 是否正确路由到 secondary =========");

        try {
            IndexCoordinates index = IndexCoordinates.of("test_index_b.secondary");
            IndexOperations indexOps = elasticsearchRestTemplate.indexOps(index);

            // 先删除索引(如果存在)
            if (indexOps.exists()) {
                log.info("索引 test_index_b.secondary 已存在,先删除");
                indexOps.delete();
            }

            // 创建索引
            log.info("开始创建索引 test_index_b.secondary");
            boolean created = indexOps.create();

            log.info("✓ 索引创建成功! 说明路由到了 secondary (9200)");
            assertTrue(created, "索引应该创建成功");

        } catch (Exception e) {
            log.error("❌ 索引创建失败!", e);

            // 检查是否是连接 9201 失败
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("9201")) {
                fail("路由错误! 索引 test_index_b.secondary 应该路由到 secondary (9200), " +
                     "但实际路由到了 primary (9201). 错误信息: " + errorMsg);
            } else {
                // 其他错误,直接抛出
                throw e;
            }
        }
    }
}
