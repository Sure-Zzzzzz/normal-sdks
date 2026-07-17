package io.github.surezzzzzz.sdk.messaging.kafka.publisher.serializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishException;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishSerializeContext;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.support.KafkaPublishStringHelper;

/**
 * Jackson Kafka 发布序列化器
 *
 * @author surezzzzzz
 */
public class JacksonKafkaPublishSerializer implements KafkaPublishSerializer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * 序列化发布内容
     *
     * @param context 序列化上下文
     * @return 序列化后的字符串
     */
    @Override
    public String serialize(KafkaPublishSerializeContext context) {
        Object target = context.isEnvelopeEnabled() ? context.getEnvelope() : context.getPayload();
        if (!context.isEnvelopeEnabled() && target instanceof String) {
            return (String) target;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(target);
        } catch (JsonProcessingException e) {
            throw new KafkaPublishException(ErrorCode.KAFKA_PUBLISHER_006,
                    String.format(ErrorMessage.SERIALIZE_FAILED,
                            KafkaPublishStringHelper.safeForErrorMessage(context.getMessageType()),
                            KafkaPublishStringHelper.safeForErrorMessage(context.getMessageId())), e);
        }
    }
}
