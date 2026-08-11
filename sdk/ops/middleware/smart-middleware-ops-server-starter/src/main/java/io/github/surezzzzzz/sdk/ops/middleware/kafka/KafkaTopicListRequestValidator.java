package io.github.surezzzzzz.sdk.ops.middleware.kafka;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;
import org.springframework.http.HttpStatus;

/**
 * Kafka topic 清单请求校验器。
 *
 * @author surezzzzzz
 */
public class KafkaTopicListRequestValidator extends DefaultMiddlewareOpsRequestValidator<KafkaTopicListRequest> {

    private final int maxSize;

    /**
     * 创建校验器。
     *
     * @param maxSize 单次最大结果数量
     */
    public KafkaTopicListRequestValidator(int maxSize) {
        super(KafkaTopicListRequest.class);
        this.maxSize = maxSize;
    }

    @Override
    public void validate(KafkaTopicListRequest request) {
        requireDatasource(request.getDatasourceKey());
        requireSize(request.getSize());
    }

    private void requireSize(int size) {
        if (size <= 0 || size > maxSize) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "结果数量超出允许范围");
        }
    }
}
