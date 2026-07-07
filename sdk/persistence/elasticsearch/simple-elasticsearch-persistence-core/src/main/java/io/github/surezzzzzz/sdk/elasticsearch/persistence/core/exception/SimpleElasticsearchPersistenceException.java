package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.exception;

import lombok.Getter;

/**
 * Simple Elasticsearch Persistence Exception
 *
 * @author surezzzzzz
 */
@Getter
public class SimpleElasticsearchPersistenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public SimpleElasticsearchPersistenceException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SimpleElasticsearchPersistenceException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
