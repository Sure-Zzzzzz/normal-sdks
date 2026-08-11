package io.github.surezzzzzz.sdk.ops.middleware.redis;

import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;

/**
 * Redis 数据源清单请求校验器。
 *
 * @author surezzzzzz
 */
public class RedisDatasourceListRequestValidator
        extends DefaultMiddlewareOpsRequestValidator<RedisDatasourceListRequest> {

    /**
     * 创建校验器。
     */
    public RedisDatasourceListRequestValidator() {
        super(RedisDatasourceListRequest.class);
    }

    @Override
    public void validate(RedisDatasourceListRequest request) {
    }
}
