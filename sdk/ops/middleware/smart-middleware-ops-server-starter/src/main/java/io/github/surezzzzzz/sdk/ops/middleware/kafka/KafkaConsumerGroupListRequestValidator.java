package io.github.surezzzzzz.sdk.ops.middleware.kafka;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;
import org.springframework.http.HttpStatus;

/**
 * Kafka 消费组清单请求校验器。
 *
 * @author surezzzzzz
 */
public class KafkaConsumerGroupListRequestValidator
        extends DefaultMiddlewareOpsRequestValidator<KafkaConsumerGroupListRequest> {

    private final int maxSize;

    /**
     * 创建校验器。
     *
     * @param maxSize 单次最大结果数量
     */
    public KafkaConsumerGroupListRequestValidator(int maxSize) {
        super(KafkaConsumerGroupListRequest.class);
        this.maxSize = maxSize;
    }

    @Override
    public void validate(KafkaConsumerGroupListRequest request) {
        requireDatasource(request.getDatasourceKey());
        requireLiteralPrefix(request.getPrefix());
        requireSize(request.getSize());
    }

    private void requireLiteralPrefix(String prefix) {
        if (prefix == null) {
            return;
        }
        for (int index = 0; index < prefix.length(); index++) {
            if (Character.isISOControl(prefix.charAt(index))) {
                throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "Kafka 消费组前缀不符合查询规范");
            }
        }
    }

    private void requireSize(int size) {
        if (size <= 0 || size > maxSize) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "结果数量超出允许范围");
        }
    }
}
