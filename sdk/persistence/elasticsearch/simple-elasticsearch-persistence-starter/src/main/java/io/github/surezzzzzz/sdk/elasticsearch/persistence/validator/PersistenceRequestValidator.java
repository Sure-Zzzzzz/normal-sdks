package io.github.surezzzzzz.sdk.elasticsearch.persistence.validator;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.PersistenceRequest;

/**
 * Persistence Request Validator
 *
 * @author surezzzzzz
 */
public interface PersistenceRequestValidator<Req extends PersistenceRequest> {

    Class<Req> getRequestType();

    void validate(Req request);
}
