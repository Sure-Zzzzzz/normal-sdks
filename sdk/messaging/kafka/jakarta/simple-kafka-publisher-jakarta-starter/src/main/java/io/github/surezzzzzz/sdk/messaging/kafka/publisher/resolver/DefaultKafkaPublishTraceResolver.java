package io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.SimpleKafkaPublisherConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.support.KafkaPublishStringHelper;
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
        String traceId = KafkaPublishStringHelper.trimToNull(
                MDC.get(SimpleKafkaPublisherConstant.MDC_TRACE_ID));
        if (traceId != null) {
            return traceId;
        }
        traceId = KafkaPublishStringHelper.trimToNull(
                MDC.get(SimpleKafkaPublisherConstant.MDC_TRACE_ID_WITH_HYPHEN));
        if (traceId != null) {
            return traceId;
        }
        return KafkaPublishStringHelper.trimToNull(
                MDC.get(SimpleKafkaPublisherConstant.MDC_X_TRACE_ID));
    }
}
