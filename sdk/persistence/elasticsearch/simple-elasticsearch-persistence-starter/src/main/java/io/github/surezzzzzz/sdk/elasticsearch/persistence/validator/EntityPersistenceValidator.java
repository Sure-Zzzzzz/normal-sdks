package io.github.surezzzzzz.sdk.elasticsearch.persistence.validator;

/**
 * Entity Persistence Validator
 *
 * @author surezzzzzz
 */
public interface EntityPersistenceValidator<T> {

    void validate(T document);
}
