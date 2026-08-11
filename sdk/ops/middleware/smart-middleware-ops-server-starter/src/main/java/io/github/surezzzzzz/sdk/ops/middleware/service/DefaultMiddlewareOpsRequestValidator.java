package io.github.surezzzzzz.sdk.ops.middleware.service;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import org.springframework.http.HttpStatus;

/**
 * 固定请求类型的基础校验器。
 *
 * @param <Req> 请求类型
 * @author surezzzzzz
 */
public abstract class DefaultMiddlewareOpsRequestValidator<Req extends MiddlewareOpsRequest>
        implements MiddlewareOpsRequestValidator<Req> {

    private final Class<Req> requestType;

    /**
     * 创建校验器。
     *
     * @param requestType 请求类型
     */
    protected DefaultMiddlewareOpsRequestValidator(Class<Req> requestType) {
        this.requestType = requestType;
    }

    @Override
    public Class<Req> getRequestType() {
        return requestType;
    }

    /**
     * 校验数据源标识。
     *
     * @param datasourceKey 数据源标识
     */
    protected void requireDatasource(String datasourceKey) {
        if (datasourceKey == null || datasourceKey.trim().isEmpty()) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "数据源标识不能为空");
        }
    }
}
