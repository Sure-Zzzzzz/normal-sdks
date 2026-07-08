package io.github.surezzzzzz.sdk.elasticsearch.persistence.validator;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.annotation.SimpleElasticsearchPersistenceComponent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.PersistenceRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistence Request Validator Registry
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchPersistenceComponent
public class PersistenceRequestValidatorRegistry {

    private final Map<Class<?>, List<PersistenceRequestValidator<?>>> validatorMap;

    public PersistenceRequestValidatorRegistry(List<PersistenceRequestValidator<?>> validatorList) {
        this.validatorMap = new ConcurrentHashMap<>();
        for (PersistenceRequestValidator<?> validator : validatorList) {
            validatorMap.computeIfAbsent(validator.getRequestType(), key -> new ArrayList<>()).add(validator);
        }
    }

    @SuppressWarnings("unchecked")
    public <Req extends PersistenceRequest> void validate(Req request) {
        Class<?> requestClass = request.getClass();
        for (Map.Entry<Class<?>, List<PersistenceRequestValidator<?>>> entry : validatorMap.entrySet()) {
            if (entry.getKey().isAssignableFrom(requestClass)) {
                for (PersistenceRequestValidator<?> validator : entry.getValue()) {
                    ((PersistenceRequestValidator<Req>) validator).validate(request);
                }
            }
        }
    }
}
