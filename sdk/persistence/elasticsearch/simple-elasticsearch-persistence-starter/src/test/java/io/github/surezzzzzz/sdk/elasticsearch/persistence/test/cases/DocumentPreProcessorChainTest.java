package io.github.surezzzzzz.sdk.elasticsearch.persistence.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.PersistenceOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.exception.SimpleElasticsearchPersistenceException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentPreProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentPreProcessorChain;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentProcessContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DocumentPreProcessorChain 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
class DocumentPreProcessorChainTest {

    @Data
    static class ChainDoc {
        private String name;
    }

    @Test
    @DisplayName("process：无 processor 时原样返回")
    void processNoProcessorReturnsOriginal() {
        DocumentPreProcessorChain chain = new DocumentPreProcessorChain(Collections.<DocumentPreProcessor>emptyList());
        ChainDoc doc = new ChainDoc();
        Object result = chain.process(doc, context());
        log.info("result={}", result);
        assertSame(doc, result, "无 processor 时应原样返回");
    }

    @Test
    @DisplayName("process：supports=false 时跳过")
    void processUnsupportedReturnsOriginal() {
        List<DocumentPreProcessor> processors = new ArrayList<DocumentPreProcessor>();
        processors.add(new DocumentPreProcessor() {
            @Override
            public boolean supports(Class<?> entityClass) {
                return false;
            }

            @Override
            public Object process(Object document, DocumentProcessContext context) {
                throw new IllegalStateException("不应执行");
            }
        });
        DocumentPreProcessorChain chain = new DocumentPreProcessorChain(processors);
        ChainDoc doc = new ChainDoc();
        Object result = chain.process(doc, context());
        log.info("result={}", result);
        assertSame(doc, result, "不支持时应原样返回");
    }

    @Test
    @DisplayName("process：多个 processor 按列表顺序执行")
    void processInOrder() {
        List<DocumentPreProcessor> processors = new ArrayList<DocumentPreProcessor>();
        processors.add(new AppendProcessor("a"));
        processors.add(new AppendProcessor("b"));
        DocumentPreProcessorChain chain = new DocumentPreProcessorChain(processors);
        ChainDoc doc = new ChainDoc();
        doc.setName("");
        Object result = chain.process(doc, context());
        log.info("result={}", result);
        assertSame(doc, result, "应返回处理后的同一对象");
        assertEquals("ab", doc.getName(), "应按顺序执行 processor");
    }

    @Test
    @DisplayName("process：processor 返回 null 抛执行异常")
    void processNullThrows() {
        List<DocumentPreProcessor> processors = new ArrayList<DocumentPreProcessor>();
        processors.add(new DocumentPreProcessor() {
            @Override
            public boolean supports(Class<?> entityClass) {
                return true;
            }

            @Override
            public Object process(Object document, DocumentProcessContext context) {
                return null;
            }
        });
        DocumentPreProcessorChain chain = new DocumentPreProcessorChain(processors);
        SimpleElasticsearchPersistenceException ex = assertThrows(
                SimpleElasticsearchPersistenceException.class,
                () -> chain.process(new ChainDoc(), context()));
        log.info("errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.EXECUTION_FAILED, ex.getErrorCode(), "应抛执行失败错误码");
    }

    private DocumentProcessContext context() {
        return DocumentProcessContext.builder()
                .operationType(PersistenceOperationType.INDEX)
                .rawIndex("test_chain")
                .renderedIndex("test_chain")
                .datasource("secondary")
                .bulk(false)
                .build();
    }

    private static class AppendProcessor implements DocumentPreProcessor {

        private final String value;

        private AppendProcessor(String value) {
            this.value = value;
        }

        @Override
        public boolean supports(Class<?> entityClass) {
            return ChainDoc.class.isAssignableFrom(entityClass);
        }

        @Override
        public Object process(Object document, DocumentProcessContext context) {
            ChainDoc doc = (ChainDoc) document;
            doc.setName(doc.getName() + value);
            return doc;
        }
    }
}
