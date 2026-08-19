package io.github.surezzzzzz.sdk.ops.middleware.kafka;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;
import org.springframework.http.HttpStatus;

/**
 * Kafka 精确消费组详情请求校验器。
 *
 * @author surezzzzzz
 */
public class KafkaConsumerGroupDetailRequestValidator
        extends DefaultMiddlewareOpsRequestValidator<KafkaConsumerGroupDetailRequest> {

    private final int maxGroupIdLength;

    public KafkaConsumerGroupDetailRequestValidator(int maxGroupIdLength) {
        super(KafkaConsumerGroupDetailRequest.class);
        this.maxGroupIdLength = maxGroupIdLength;
    }

    @Override
    public void validate(KafkaConsumerGroupDetailRequest request) {
        requireDatasource(request.getDatasourceKey());
        String groupId = request.getGroupId();
        if (groupId == null || groupId.trim().isEmpty() || groupId.length() > maxGroupIdLength
                || containsControlCharacter(groupId)) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "Kafka 消费组标识不符合查询规范");
        }
    }

    private boolean containsControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }
}
