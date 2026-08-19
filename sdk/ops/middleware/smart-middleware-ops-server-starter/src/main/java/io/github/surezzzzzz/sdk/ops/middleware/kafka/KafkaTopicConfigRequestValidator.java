package io.github.surezzzzzz.sdk.ops.middleware.kafka;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;
import org.springframework.http.HttpStatus;

/**
 * Kafka 精确 Topic 配置请求校验器。
 *
 * @author surezzzzzz
 */
public class KafkaTopicConfigRequestValidator extends DefaultMiddlewareOpsRequestValidator<KafkaTopicConfigRequest> {

    private final int maxTopicLength;

    public KafkaTopicConfigRequestValidator(int maxTopicLength) {
        super(KafkaTopicConfigRequest.class);
        this.maxTopicLength = maxTopicLength;
    }

    @Override
    public void validate(KafkaTopicConfigRequest request) {
        requireDatasource(request.getDatasourceKey());
        String topic = request.getTopic();
        if (topic == null || topic.trim().isEmpty() || topic.length() > maxTopicLength
                || containsControlCharacter(topic)) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "Kafka Topic 不符合查询规范");
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
