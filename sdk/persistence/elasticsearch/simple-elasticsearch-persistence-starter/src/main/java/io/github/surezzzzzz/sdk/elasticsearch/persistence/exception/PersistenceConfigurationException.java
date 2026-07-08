package io.github.surezzzzzz.sdk.elasticsearch.persistence.exception;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.exception.SimpleElasticsearchPersistenceException;

/**
 * Persistence Configuration Exception
 *
 * @author surezzzzzz
 */
public class PersistenceConfigurationException extends SimpleElasticsearchPersistenceException {

    private static final long serialVersionUID = 1L;

    public PersistenceConfigurationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public PersistenceConfigurationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
