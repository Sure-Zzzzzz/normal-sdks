package io.github.surezzzzzz.sdk.elasticsearch.persistence.exception;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.exception.SimpleElasticsearchPersistenceException;

/**
 * 持久化执行异常
 *
 * @author surezzzzzz
 */
public class PersistenceExecutionException extends SimpleElasticsearchPersistenceException {

    private static final long serialVersionUID = 1L;

    /**
     * 使用固定错误码和消息创建执行异常。
     */
    public PersistenceExecutionException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 使用固定错误码和消息创建执行异常。
     */
    public PersistenceExecutionException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}

