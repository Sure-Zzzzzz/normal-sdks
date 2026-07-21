package io.github.surezzzzzz.sdk.messaging.kafka.outbox.trace;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.annotation.SimpleKafkaOutboxComponent;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.SimpleKafkaOutboxConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.support.KafkaOutboxStringHelper;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * 默认 MDC traceId 快照解析器
 *
 * @author surezzzzzz
 */
@SimpleKafkaOutboxComponent
@ConditionalOnMissingBean(KafkaOutboxTraceSnapshotResolver.class)
public class DefaultKafkaOutboxTraceSnapshotResolver implements KafkaOutboxTraceSnapshotResolver {

    /**
     * 按 publisher 约定解析当前线程 traceId
     *
     * @return traceId
     */
    @Override
    public String resolveTraceId() {
        String traceId = KafkaOutboxStringHelper.trimToNull(MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID));
        if (traceId != null) {
            return traceId;
        }
        traceId = KafkaOutboxStringHelper.trimToNull(
                MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID_WITH_HYPHEN));
        if (traceId != null) {
            return traceId;
        }
        return KafkaOutboxStringHelper.trimToNull(MDC.get(SimpleKafkaOutboxConstant.MDC_X_TRACE_ID));
    }
}
