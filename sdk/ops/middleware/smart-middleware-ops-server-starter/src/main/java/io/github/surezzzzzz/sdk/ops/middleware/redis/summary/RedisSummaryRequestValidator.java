package io.github.surezzzzzz.sdk.ops.middleware.redis.summary;

import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;

/**
 * Redis 数据源摘要请求校验器。
 *
 * @author surezzzzzz
 */
public class RedisSummaryRequestValidator extends DefaultMiddlewareOpsRequestValidator<RedisSummaryRequest> {

    /**
     * 创建校验器。
     */
    public RedisSummaryRequestValidator() {
        super(RedisSummaryRequest.class);
    }

    @Override
    public void validate(RedisSummaryRequest request) {
        requireDatasource(request.getDatasourceKey());
    }
}
