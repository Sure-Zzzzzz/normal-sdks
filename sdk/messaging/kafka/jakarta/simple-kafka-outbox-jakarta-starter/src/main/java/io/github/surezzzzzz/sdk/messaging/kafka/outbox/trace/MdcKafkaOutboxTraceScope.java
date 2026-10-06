package io.github.surezzzzzz.sdk.messaging.kafka.outbox.trace;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.annotation.SimpleKafkaOutboxComponent;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.SimpleKafkaOutboxConstant;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * MDC Kafka Outbox traceId 临时作用域
 *
 * @author surezzzzzz
 */
@SimpleKafkaOutboxComponent
@ConditionalOnMissingBean(KafkaOutboxTraceScope.class)
public class MdcKafkaOutboxTraceScope implements KafkaOutboxTraceScope {

    /**
     * 打开 traceId 临时作用域
     *
     * @param traceId 快照 traceId
     * @return 恢复句柄
     */
    @Override
    public Scope open(String traceId) {
        final String previousTraceId = MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID);
        final String previousTraceIdWithHyphen = MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID_WITH_HYPHEN);
        final String previousXTraceId = MDC.get(SimpleKafkaOutboxConstant.MDC_X_TRACE_ID);
        MDC.remove(SimpleKafkaOutboxConstant.MDC_TRACE_ID_WITH_HYPHEN);
        MDC.remove(SimpleKafkaOutboxConstant.MDC_X_TRACE_ID);
        if (traceId == null) {
            MDC.remove(SimpleKafkaOutboxConstant.MDC_TRACE_ID);
        } else {
            MDC.put(SimpleKafkaOutboxConstant.MDC_TRACE_ID, traceId);
        }
        return () -> {
            restore(SimpleKafkaOutboxConstant.MDC_TRACE_ID, previousTraceId);
            restore(SimpleKafkaOutboxConstant.MDC_TRACE_ID_WITH_HYPHEN, previousTraceIdWithHyphen);
            restore(SimpleKafkaOutboxConstant.MDC_X_TRACE_ID, previousXTraceId);
        };
    }

    /**
     * 恢复 MDC 原值：null 则移除键，非 null 则写回。
     */
    private void restore(String key, String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }
}
