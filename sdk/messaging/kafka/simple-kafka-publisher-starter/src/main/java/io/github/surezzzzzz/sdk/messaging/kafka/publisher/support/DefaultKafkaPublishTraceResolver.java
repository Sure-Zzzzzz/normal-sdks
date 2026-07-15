package io.github.surezzzzzz.sdk.messaging.kafka.publisher.support;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.SimpleKafkaPublisherConstant;
import org.slf4j.MDC;

/**
 * 默认 Kafka 发布 traceId 解析器
 *
 * @author surezzzzzz
 */
public class DefaultKafkaPublishTraceResolver implements KafkaPublishTraceResolver {

    /**
     * 解析 traceId
     *
     * @return traceId
     */
    @Override
    public String resolveTraceId() {
        String traceId = MDC.get(SimpleKafkaPublisherConstant.MDC_TRACE_ID);
        if (KafkaPublishStringHelper.hasText(traceId)) {
            return traceId;
        }
        traceId = MDC.get(SimpleKafkaPublisherConstant.MDC_TRACE_ID_WITH_HYPHEN);
        if (KafkaPublishStringHelper.hasText(traceId)) {
            return traceId;
        }
        return MDC.get(SimpleKafkaPublisherConstant.MDC_X_TRACE_ID);
    }
}
