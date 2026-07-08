package io.github.surezzzzzz.sdk.elasticsearch.persistence.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.exception.SimpleElasticsearchPersistenceException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.DocumentMetadataHelper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DocumentMetadataHelper 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
class DocumentMetadataHelperTest {

    @Data
    @Document(indexName = "test_persistence_metadata")
    static class MetadataDoc {
        @Id
        private String id;
        private String name;
    }

    @Data
    static class NoAnnotationDoc {
        private String name;
    }

    @Test
    @DisplayName("resolveIndex：显式索引优先于 @Document")
    void resolveIndexExplicitWins() {
        String explicit = "explicit_index";
        String result = DocumentMetadataHelper.resolveIndex(new MetadataDoc(), explicit);
        log.info("输入 explicit={}, 结果 index={}", explicit, result);
        assertEquals(explicit, result, "显式索引应优先返回");
    }

    @Test
    @DisplayName("resolveIndex：无显式索引时从 @Document.indexName 提取")
    void resolveIndexFromAnnotation() {
        String result = DocumentMetadataHelper.resolveIndex(new MetadataDoc(), null);
        log.info("结果 index={}", result);
        assertEquals("test_persistence_metadata", result, "应从 @Document.indexName 提取");
    }

    @Test
    @DisplayName("resolveIndex：按 Class 提取索引名")
    void resolveIndexByClass() {
        String result = DocumentMetadataHelper.resolveIndex(MetadataDoc.class);
        log.info("结果 index={}", result);
        assertEquals("test_persistence_metadata", result, "应从 Class 的 @Document 提取");
    }

    @Test
    @DisplayName("resolveIndex：document 为空抛 PersistenceException")
    void resolveIndexNullDocumentThrows() {
        SimpleElasticsearchPersistenceException ex = assertThrows(
                SimpleElasticsearchPersistenceException.class,
                () -> DocumentMetadataHelper.resolveIndex((Object) null, null));
        log.info("异常 errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.REQUEST_VALIDATION_FAILED, ex.getErrorCode(), "应抛请求校验失败错误码");
    }

    @Test
    @DisplayName("resolveIndex：无 @Document 注解抛 PersistenceException")
    void resolveIndexNoAnnotationThrows() {
        SimpleElasticsearchPersistenceException ex = assertThrows(
                SimpleElasticsearchPersistenceException.class,
                () -> DocumentMetadataHelper.resolveIndex(NoAnnotationDoc.class));
        log.info("异常 errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.REQUEST_VALIDATION_FAILED, ex.getErrorCode(), "应抛请求校验失败错误码");
    }

    @Test
    @DisplayName("resolveId：显式 id 优先于 @Id 字段")
    void resolveIdExplicitWins() {
        MetadataDoc doc = new MetadataDoc();
        doc.setId("from-field");
        String result = DocumentMetadataHelper.resolveId(doc, "explicit-id");
        log.info("输入 explicitId=explicit-id, fieldId=from-field, 结果={}", result);
        assertEquals("explicit-id", result, "显式 id 应优先");
    }

    @Test
    @DisplayName("resolveId：无显式 id 时从 @Id 字段提取")
    void resolveIdFromAnnotation() {
        MetadataDoc doc = new MetadataDoc();
        doc.setId("from-field");
        String result = DocumentMetadataHelper.resolveId(doc, null);
        log.info("结果 id={}", result);
        assertEquals("from-field", result, "应从 @Id 字段提取");
    }

    @Test
    @DisplayName("resolveId：无 @Id 字段返回 null")
    void resolveIdNoIdFieldReturnsNull() {
        String result = DocumentMetadataHelper.resolveId(new NoAnnotationDoc(), null);
        log.info("结果 id={}", result);
        assertNull(result, "无 @Id 字段应返回 null");
    }

    @Test
    @DisplayName("resolveId：document 为空返回 null")
    void resolveIdNullDocumentReturnsNull() {
        String result = DocumentMetadataHelper.resolveId(null, null);
        log.info("结果 id={}", result);
        assertNull(result, "document 为空应返回 null");
    }
}
