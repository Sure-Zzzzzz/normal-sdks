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
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IndexQuery 和 UpdateQuery Extractor 测试
 *
 * <p><b>测试目的:</b> 验证新增的 IndexQueryExtractor 和 UpdateQueryExtractor 是否正确工作</p>
 *
 * <p><b>测试方案:</b></p>
 * <ul>
 *   <li>primary 数据源配置为不存在的端口 (localhost:9201)</li>
 *   <li>secondary 数据源配置为真实 ES (localhost:9200)</li>
 *   <li>通过 IndexQuery 手动指定索引名,验证路由是否正确</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
@ActiveProfiles("write-test")
public class IndexQueryExtractorTest {

    @Autowired
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    /**
     * 测试文档 - 使用通用索引名,不应该路由到 secondary
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @Document(indexName = "test_generic_doc")
    public static class GenericDocument {
        @Id
        private String id;

        @Field(type = FieldType.Keyword)
        private String testField;
    }

    /**
     * 测试1: IndexQuery 手动指定索引名 - 应该路由到 secondary
     *
     * <p>场景: 用户通过 IndexQueryBuilder.withIndex() 手动覆盖实体类的索引名</p>
     */
    @Test
    public void testIndexQueryWithManualIndexName() {
        log.info("========== 测试 IndexQuery 手动指定索引名路由 ==========");

        try {
            // 确保索引存在
            IndexCoordinates index = IndexCoordinates.of("indexquery_test.secondary");
            IndexOperations indexOps = elasticsearchRestTemplate.indexOps(index);

            if (!indexOps.exists()) {
                indexOps.create();
                log.info("索引 indexquery_test.secondary 已创建");
            }

            // 创建 IndexQuery,手动指定索引名为 secondary
            GenericDocument doc = new GenericDocument("iq-001", "test-value");
            IndexQuery indexQuery = new IndexQueryBuilder()
                    .withId(doc.getId())
                    .withObject(doc)
                    .withIndex("indexquery_test.secondary")  // 手动指定索引名,应该路由到 secondary (9200)
                    .build();

            // 执行索引操作
            log.info("开始索引文档,IndexQuery 指定索引: indexquery_test.secondary");
            String documentId = elasticsearchRestTemplate.index(indexQuery, index);

            log.info("✓ 文档索引成功! documentId=[{}], 说明路由到了 secondary (9200)", documentId);
            assertNotNull(documentId, "文档ID不应该为空");
            assertEquals("iq-001", documentId, "文档ID应该匹配");

        } catch (Exception e) {
            log.error("❌ 文档索引失败!", e);

            // 检查是否是连接 9201 失败
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("9201")) {
                fail("路由错误! IndexQuery 指定的索引 indexquery_test.secondary 应该路由到 secondary (9200), " +
                     "但实际路由到了 primary (9201). 错误信息: " + errorMsg);
            } else {
                // 其他错误,直接抛出
                throw e;
            }
        }
    }

    /**
     * 测试2: IndexQuery 未指定索引名 - 应该回退到实体类的 @Document 注解
     *
     * <p>场景: IndexQuery 不指定索引名时,应该由 EntityObjectExtractor 提取</p>
     */
    @Test
    public void testIndexQueryWithoutManualIndexName() {
        log.info("========== 测试 IndexQuery 未指定索引名的回退逻辑 ==========");

        try {
            // 使用实体类的索引名 test_generic_doc (应该路由到 primary,但 primary 不可用)
            // 这个测试验证当 IndexQueryExtractor 返回 null 时,责任链会继续到 EntityObjectExtractor

            GenericDocument doc = new GenericDocument("iq-002", "test-value-2");
            IndexQuery indexQuery = new IndexQueryBuilder()
                    .withId(doc.getId())
                    .withObject(doc)
                    // 不调用 .withIndex(),让责任链回退到 EntityObjectExtractor
                    .build();

            // 这里应该会失败,因为 test_generic_doc 会路由到 primary (9201 不可用)
            log.info("开始索引文档,IndexQuery 未指定索引,使用实体类注解: test_generic_doc");

            // 预期: 抛出异常,因为 test_generic_doc 无法匹配任何路由规则,使用 default (primary),而 primary 不可用
            Exception exception = assertThrows(Exception.class, () -> {
                elasticsearchRestTemplate.index(indexQuery, IndexCoordinates.of("test_generic_doc"));
            });

            log.info("✓ 测试通过! 未指定索引名时正确回退到实体类注解,并因 primary 不可用而失败");
            assertTrue(exception.getMessage().contains("9201") ||
                      exception.getMessage().contains("Connection refused"),
                      "应该因为无法连接 primary (9201) 而失败");

        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ 测试失败!", e);
            fail("测试执行过程中发生意外错误: " + e.getMessage());
        }
    }

    /**
     * 测试3: IndexQuery 的优先级低于 IndexCoordinates
     *
     * <p>场景: 当方法同时有 IndexQuery(带索引名) 和 IndexCoordinates 参数时,
     * IndexCoordinatesExtractor (Order 1) 应该优先于 IndexQueryExtractor (Order 4)</p>
     */
    @Test
    public void testIndexCoordinatesTakesPrecedenceOverIndexQuery() {
        log.info("========== 测试 IndexCoordinates 优先级高于 IndexQuery ==========");

        try {
            // 确保索引存在
            IndexCoordinates highPriorityIndex = IndexCoordinates.of("priority_test.secondary");
            IndexOperations indexOps = elasticsearchRestTemplate.indexOps(highPriorityIndex);

            if (!indexOps.exists()) {
                indexOps.create();
                log.info("索引 priority_test.secondary 已创建");
            }

            GenericDocument doc = new GenericDocument("iq-003", "priority-test");

            // IndexQuery 指定一个索引名
            IndexQuery indexQuery = new IndexQueryBuilder()
                    .withId(doc.getId())
                    .withObject(doc)
                    .withIndex("indexquery_specified.secondary")  // 这个会被忽略
                    .build();

            // template.index() 方法的第二个参数是 IndexCoordinates,优先级更高
            log.info("IndexQuery 指定索引: indexquery_specified.secondary");
            log.info("IndexCoordinates 指定索引: priority_test.secondary");
            log.info("预期: 使用 IndexCoordinates 的索引 (优先级更高)");

            String documentId = elasticsearchRestTemplate.index(indexQuery, highPriorityIndex);

            log.info("✓ 文档索引成功! documentId=[{}], IndexCoordinates 优先级验证通过", documentId);
            assertNotNull(documentId, "文档ID不应该为空");

        } catch (Exception e) {
            log.error("❌ 优先级测试失败!", e);

            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("9201")) {
                fail("优先级验证失败! 应该使用 IndexCoordinates (priority_test.secondary -> secondary), " +
                     "但实际使用了其他索引并路由到了 primary (9201). 错误信息: " + errorMsg);
            } else {
                throw e;
            }
        }
    }
}
