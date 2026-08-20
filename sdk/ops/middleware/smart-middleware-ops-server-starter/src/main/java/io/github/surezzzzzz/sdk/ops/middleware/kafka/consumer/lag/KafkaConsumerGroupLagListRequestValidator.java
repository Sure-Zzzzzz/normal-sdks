package io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.lag;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;
import org.springframework.http.HttpStatus;

/**
 * Kafka 消费组积压分页请求校验器。
 *
 * @author surezzzzzz
 */
public class KafkaConsumerGroupLagListRequestValidator
        extends DefaultMiddlewareOpsRequestValidator<KafkaConsumerGroupLagListRequest> {

    private static final int MAX_GROUP_ID_LENGTH = 255;
    private final int maxSize;

    public KafkaConsumerGroupLagListRequestValidator(int maxSize) {
        super(KafkaConsumerGroupLagListRequest.class);
        this.maxSize = maxSize;
    }

    @Override
    public void validate(KafkaConsumerGroupLagListRequest request) {
        requireDatasource(request.getDatasourceKey());
        String groupId = request.getGroupId();
        if (groupId == null || groupId.trim().isEmpty() || groupId.length() > MAX_GROUP_ID_LENGTH
                || containsControlCharacter(groupId)) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "Kafka 消费组标识不符合查询规范");
        }
        if (request.getSize() <= 0 || request.getSize() > maxSize) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "结果数量超出允许范围");
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
