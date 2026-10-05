package io.github.surezzzzzz.sdk.elasticsearch.persistence.validator;

/**
 * 强类型实体写前校验接口
 *
 * @author surezzzzzz
 */
public interface EntityPersistenceValidator<T> {

    /**
     * 校验使用前置条件，失败在发送业务 HTTP 前暴露。
     */
    void validate(T document);
}

