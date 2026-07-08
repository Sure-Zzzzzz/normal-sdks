package io.github.surezzzzzz.sdk.elasticsearch.persistence.exception;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.exception.SimpleElasticsearchPersistenceException;

/**
 * Persistence Execution Exception
 *
 * @author surezzzzzz
 */
public class PersistenceExecutionException extends SimpleElasticsearchPersistenceException {

    private static final long serialVersionUID = 1L;

    public PersistenceExecutionException(String errorCode, String message) {
        super(errorCode, message);
    }

    public PersistenceExecutionException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
