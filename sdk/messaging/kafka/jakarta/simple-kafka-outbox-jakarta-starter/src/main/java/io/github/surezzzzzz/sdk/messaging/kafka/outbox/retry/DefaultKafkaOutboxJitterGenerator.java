package io.github.surezzzzzz.sdk.messaging.kafka.outbox.retry;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.annotation.SimpleKafkaOutboxComponent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 默认 Kafka Outbox 随机抖动生成器
 *
 * @author surezzzzzz
 */
@SimpleKafkaOutboxComponent
@ConditionalOnMissingBean(KafkaOutboxJitterGenerator.class)
public class DefaultKafkaOutboxJitterGenerator implements KafkaOutboxJitterGenerator {
    /**
     * 生成单位区间随机值
     *
     * @return 随机值
     */
    @Override
    public double nextDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }
}
