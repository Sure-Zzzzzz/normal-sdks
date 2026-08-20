package io.github.surezzzzzz.sdk.ops.middleware.kafka.topic.runtime;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;
import org.springframework.http.HttpStatus;

/**
 * Kafka Topic 分区状态请求校验器。
 *
 * @author surezzzzzz
 */
public class KafkaTopicRuntimeRequestValidator extends DefaultMiddlewareOpsRequestValidator<KafkaTopicRuntimeRequest> {

    private final int maxTopicLength;

    /**
     * 创建 Kafka Topic 分区状态请求校验器。
     *
     * @param maxTopicLength topic 最大长度
     */
    public KafkaTopicRuntimeRequestValidator(int maxTopicLength) {
        super(KafkaTopicRuntimeRequest.class);
        this.maxTopicLength = maxTopicLength;
    }

    @Override
    public void validate(KafkaTopicRuntimeRequest request) {
        requireDatasource(request.getDatasourceKey());
        String topic = request.getTopic();
        if (topic == null || topic.trim().isEmpty() || topic.length() > maxTopicLength || containsControlCharacter(topic)) {
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
