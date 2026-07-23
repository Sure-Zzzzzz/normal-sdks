package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.support.KafkaConfigurationCompatibilityHelper;
import io.github.surezzzzzz.sdk.kafka.route.test.factory.RecordingConsumerFactory;
import io.github.surezzzzzz.sdk.kafka.route.test.factory.RecordingProducerFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kafka 配置兼容 Helper 测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaConfigurationCompatibilityHelperTest {

    @Test
    public void testDestroyProducerFactory() {
        RecordingProducerFactory factory = new RecordingProducerFactory("default");

        KafkaConfigurationCompatibilityHelper.destroyProducerFactory(factory);

        assertTrue(factory.isDestroyed());
    }

    @Test
    public void testDestroyConsumerFactory() {
        RecordingConsumerFactory factory = new RecordingConsumerFactory("default");

        KafkaConfigurationCompatibilityHelper.destroyConsumerFactory(factory);

        assertTrue(factory.isDestroyed());
    }

    @Test
    public void testDestroyNullDoesNothing() {
        assertDoesNotThrow(() -> KafkaConfigurationCompatibilityHelper.destroyProducerFactory(null));
        assertDoesNotThrow(() -> KafkaConfigurationCompatibilityHelper.destroyConsumerFactory(null));
    }
}
