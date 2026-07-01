package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

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
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IndexQuery 和 UpdateQuery Extractor 测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
@ActiveProfiles(resolver = WriteTestProfilesResolver.class)
public class IndexQueryExtractorTest {

    @Autowired
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

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

    private Object createIndexCoordinates(String indexName) {
        try {
            Class<?> clazz = Class.forName("org.springframework.data.elasticsearch.core.mapping.IndexCoordinates");
            java.lang.reflect.Method of = clazz.getMethod("of", String[].class);
            return of.invoke(null, (Object) new String[]{indexName});
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create IndexCoordinates for: " + indexName, e);
        }
    }

    private boolean indexExists(String indexName) {
        try {
            java.lang.reflect.Method indexExists = elasticsearchRestTemplate.getClass()
                    .getMethod("indexExists", String.class);
            return (boolean) indexExists.invoke(elasticsearchRestTemplate, indexName);
        } catch (NoSuchMethodException e) {
            Object indexOps = indexOpsForCoordinates(indexName);
            try {
                java.lang.reflect.Method exists = indexOps.getClass().getMethod("exists");
                return (boolean) exists.invoke(indexOps);
            } catch (InvocationTargetException ex) {
                throw unwrap(ex);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to call exists()", ex);
            }
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to call indexExists(" + indexName + ")", e);
        }
    }

    private boolean createIndex(String indexName) {
        try {
            java.lang.reflect.Method createIndex = elasticsearchRestTemplate.getClass()
                    .getMethod("createIndex", String.class);
            return (boolean) createIndex.invoke(elasticsearchRestTemplate, indexName);
        } catch (NoSuchMethodException e) {
            Object indexOps = indexOpsForCoordinates(indexName);
            try {
                java.lang.reflect.Method create = indexOps.getClass().getMethod("create");
                return (boolean) create.invoke(indexOps);
            } catch (InvocationTargetException ex) {
                throw unwrap(ex);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to call create()", ex);
            }
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to call createIndex(" + indexName + ")", e);
        }
    }

    private Object indexOpsForCoordinates(String indexName) {
        try {
            Object indexCoordinates = createIndexCoordinates(indexName);
            Class<?> coordinatesClass = Class.forName(
                    "org.springframework.data.elasticsearch.core.mapping.IndexCoordinates");
            java.lang.reflect.Method indexOps = elasticsearchRestTemplate.getClass()
                    .getMethod("indexOps", coordinatesClass);
            return indexOps.invoke(elasticsearchRestTemplate, indexCoordinates);
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to call indexOps for: " + indexName, e);
        }
    }

    private String indexDocumentToName(IndexQuery query, String indexName) {
        try {
            java.lang.reflect.Method indexMethod = elasticsearchRestTemplate.getClass()
                    .getMethod("index", IndexQuery.class);
            return (String) indexMethod.invoke(elasticsearchRestTemplate, withIndexName(query, indexName));
        } catch (NoSuchMethodException e) {
            try {
                Object indexCoordinates = createIndexCoordinates(indexName);
                Class<?> coordinatesClass = Class.forName(
                        "org.springframework.data.elasticsearch.core.mapping.IndexCoordinates");
                java.lang.reflect.Method indexMethod = elasticsearchRestTemplate.getClass()
                        .getMethod("index", IndexQuery.class, coordinatesClass);
                return (String) indexMethod.invoke(elasticsearchRestTemplate, query, indexCoordinates);
            } catch (InvocationTargetException ex) {
                throw unwrap(ex);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to index document to: " + indexName, ex);
            }
        } catch (InvocationTargetException e) {
            throw unwrap(e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to index document to: " + indexName, e);
        }
    }

    private IndexQuery withIndexName(IndexQuery query, String indexName) {
        try {
            java.lang.reflect.Method setIndexName = IndexQuery.class.getMethod("setIndexName", String.class);
            setIndexName.invoke(query, indexName);
            return query;
        } catch (NoSuchMethodException e) {
            return query;
        } catch (Exception e) {
            throw new RuntimeException("Failed to set index name", e);
        }
    }

    private IndexQuery buildIndexQueryWithIndex(String id, Object object, String indexName) {
        IndexQueryBuilder builder = new IndexQueryBuilder()
                .withId(id)
                .withObject(object);
        try {
            java.lang.reflect.Method withIndexName = IndexQueryBuilder.class.getMethod("withIndexName", String.class);
            withIndexName.invoke(builder, indexName);
        } catch (NoSuchMethodException e) {
            try {
                java.lang.reflect.Method withIndex = IndexQueryBuilder.class.getMethod("withIndex", String.class);
                withIndex.invoke(builder, indexName);
            } catch (NoSuchMethodException ignored) {
                log.info("当前 IndexQueryBuilder 不支持直接写入索引名，调用侧会用 index/indexOps 参数指定索引");
            } catch (Exception ex) {
                throw new RuntimeException("Failed to build IndexQuery with index", ex);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to build IndexQuery with index", e);
        }
        return builder.build();
    }

    @Test
    public void testIndexQueryWithManualIndexName() {
        log.info("========== 测试 IndexQuery 手动指定索引名路由 ==========");
        try {
            if (!indexExists("indexquery_test.secondary")) {
                createIndex("indexquery_test.secondary");
                log.info("索引 indexquery_test.secondary 已创建");
            }
            GenericDocument doc = new GenericDocument("iq-001", "test-value");
            IndexQuery indexQuery = buildIndexQueryWithIndex(doc.getId(), doc, "indexquery_test.secondary");

            String documentId = indexDocumentToName(indexQuery, "indexquery_test.secondary");

            log.info("文档索引成功，documentId=[{}]", documentId);
            assertNotNull(documentId, "文档ID不应该为空");
            assertEquals("iq-001", documentId, "文档ID应该匹配");
        } catch (Exception e) {
            log.error("文档索引失败", e);
            assertNotPrimaryRouteError(e, "indexquery_test.secondary");
            throw e;
        }
    }

    @Test
    public void testIndexQueryWithoutManualIndexName() {
        log.info("========== 测试 IndexQuery 未指定索引名的回退逻辑 ==========");
        GenericDocument doc = new GenericDocument("iq-002", "test-value-2");
        IndexQuery indexQuery = new IndexQueryBuilder()
                .withId(doc.getId())
                .withObject(doc)
                .build();

        Exception exception = assertThrows(Exception.class, () ->
                indexDocumentToName(indexQuery, "test_generic_doc"));

        String fullMsg = buildCauseChain(exception);
        log.info("完整 cause 链: {}", fullMsg);
        assertTrue(fullMsg.contains("9201") || fullMsg.contains("9202") || fullMsg.contains("Connection refused")
                        || fullMsg.contains("refused") || fullMsg.contains("connect"),
                "应该因为无法连接 primary 而失败，cause链: " + fullMsg);
    }

    @Test
    public void testIndexCoordinatesTakesPrecedenceOverIndexQuery() {
        log.info("========== 测试显式 index 参数优先于 IndexQuery ==========");
        try {
            if (!indexExists("priority_test.secondary")) {
                createIndex("priority_test.secondary");
                log.info("索引 priority_test.secondary 已创建");
            }
            GenericDocument doc = new GenericDocument("iq-003", "priority-test");
            IndexQuery indexQuery = buildIndexQueryWithIndex(doc.getId(), doc, "indexquery_specified.secondary");

            String documentId = indexDocumentToName(indexQuery, "priority_test.secondary");

            log.info("文档索引成功，documentId=[{}]", documentId);
            assertNotNull(documentId, "文档ID不应该为空");
            assertEquals("iq-003", documentId, "文档ID应该匹配");
        } catch (Exception e) {
            log.error("优先级测试失败", e);
            assertNotPrimaryRouteError(e, "priority_test.secondary");
            throw e;
        }
    }

    private RuntimeException unwrap(InvocationTargetException e) {
        Throwable cause = e.getTargetException();
        if (cause instanceof RuntimeException) {
            return (RuntimeException) cause;
        }
        if (cause instanceof Error) {
            throw (Error) cause;
        }
        return new RuntimeException(cause);
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
