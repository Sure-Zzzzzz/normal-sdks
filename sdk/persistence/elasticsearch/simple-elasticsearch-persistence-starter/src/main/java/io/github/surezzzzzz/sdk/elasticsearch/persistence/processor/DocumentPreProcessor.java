package io.github.surezzzzzz.sdk.elasticsearch.persistence.processor;

/**
 * Document Pre Processor
 *
 * @author surezzzzzz
 */
public interface DocumentPreProcessor {

    /**
     * 判断是否支持当前实体类型。
     *
     * @param entityClass 实体类型
     * @return true 支持，false 不支持
     */
    boolean supports(Class<?> entityClass);

    /**
     * 写入前处理文档。
     *
     * @param document 文档对象
     * @param context 处理上下文
     * @return 处理后的文档对象
     */
    Object process(Object document, DocumentProcessContext context);
}
