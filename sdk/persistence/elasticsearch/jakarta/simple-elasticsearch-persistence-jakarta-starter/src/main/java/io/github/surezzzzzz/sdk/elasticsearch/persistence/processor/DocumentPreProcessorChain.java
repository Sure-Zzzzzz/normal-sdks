package io.github.surezzzzzz.sdk.elasticsearch.persistence.processor;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.SimpleElasticsearchPersistenceConstant;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 顺序执行文档处理器；返回 null 明确失败，不静默跳过写入
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class DocumentPreProcessorChain {

    private final List<DocumentPreProcessor> processorList;

    /**
     * 执行写入前处理链。
     *
     * @param document 文档对象
     * @param context  处理上下文
     * @return 处理后的文档对象
     */
    public Object process(Object document, DocumentProcessContext context) {
        if (document == null || CollectionUtils.isEmpty(processorList)) {
            return document;
        }
        Object current = document;
        Class<?> entityClass = current.getClass();
        for (DocumentPreProcessor processor : processorList) {
            if (!processor.supports(entityClass)) {
                continue;
            }
            current = processor.process(current, context);
            if (current == null) {
                throw new PersistenceExecutionException(ErrorCode.EXECUTION_FAILED,
                        SimpleElasticsearchPersistenceConstant.DOCUMENT_PRE_PROCESSOR_NULL_RESULT);
            }
        }
        return current;
    }
}

