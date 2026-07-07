package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request;

import lombok.experimental.SuperBuilder;

import java.io.Serializable;

/**
 * Persistence Request Base Class
 *
 * @author surezzzzzz
 */
@SuperBuilder
public abstract class PersistenceRequest implements Serializable {

    private static final long serialVersionUID = 1L;
}
