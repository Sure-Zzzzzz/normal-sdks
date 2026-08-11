package io.github.surezzzzzz.sdk.ops.middleware.service;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认类型化执行器注册表。
 *
 * @author surezzzzzz
 */
public class DefaultMiddlewareOpsExecutorRegistry implements MiddlewareOpsExecutorRegistry {

    private final Map<Class<?>, MiddlewareOpsExecutor<?, ?>> executors = new LinkedHashMap<>();
    private final Map<Class<?>, MiddlewareOpsRequestValidator<?>> validators = new LinkedHashMap<>();

    /**
     * 创建注册表。
     *
     * @param executorList  显式执行器
     * @param validatorList 显式校验器
     */
    public DefaultMiddlewareOpsExecutorRegistry(List<MiddlewareOpsExecutor<?, ?>> executorList,
                                                List<MiddlewareOpsRequestValidator<?>> validatorList) {
        for (MiddlewareOpsExecutor<?, ?> executor : executorList) {
            register(executors, executor.getRequestType(), executor);
        }
        for (MiddlewareOpsRequestValidator<?> validator : validatorList) {
            register(validators, validator.getRequestType(), validator);
        }
    }

    @Override
    public MiddlewareOpsExecutor<?, ?> getExecutor(MiddlewareOpsRequest request) {
        MiddlewareOpsExecutor<?, ?> executor = findAssignable(executors, request);
        if (executor == null) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "不支持的运维查询能力");
        }
        return executor;
    }

    @Override
    public void validate(MiddlewareOpsRequest request) {
        MiddlewareOpsRequestValidator<?> validator = findAssignable(validators, request);
        if (validator == null) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "不支持的运维查询能力");
        }
        validateTyped(validator, request);
    }

    private <T> T findAssignable(Map<Class<?>, T> candidates, MiddlewareOpsRequest request) {
        T matched = null;
        for (Map.Entry<Class<?>, T> entry : candidates.entrySet()) {
            if (entry.getKey().isInstance(request)) {
                if (matched != null) {
                    throw new IllegalStateException("同一请求类型不能匹配多个默认实现");
                }
                matched = entry.getValue();
            }
        }
        return matched;
    }

    private <T> void register(Map<Class<?>, T> target, Class<?> requestType, T value) {
        if (target.put(requestType, value) != null) {
            throw new IllegalArgumentException("同一请求类型只能注册一个默认实现");
        }
    }

    @SuppressWarnings("unchecked")
    private void validateTyped(MiddlewareOpsRequestValidator<?> validator, MiddlewareOpsRequest request) {
        ((MiddlewareOpsRequestValidator<MiddlewareOpsRequest>) validator).validate(request);
    }
}
