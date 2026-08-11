package io.github.surezzzzzz.sdk.ops.middleware.kafka;

import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;

/**
 * Kafka 数据源清单请求校验器。
 *
 * @author surezzzzzz
 */
public class KafkaDatasourceListRequestValidator
        extends DefaultMiddlewareOpsRequestValidator<KafkaDatasourceListRequest> {

    /**
     * 创建校验器。
     */
    public KafkaDatasourceListRequestValidator() {
        super(KafkaDatasourceListRequest.class);
    }

    @Override
    public void validate(KafkaDatasourceListRequest request) {
    }
}
