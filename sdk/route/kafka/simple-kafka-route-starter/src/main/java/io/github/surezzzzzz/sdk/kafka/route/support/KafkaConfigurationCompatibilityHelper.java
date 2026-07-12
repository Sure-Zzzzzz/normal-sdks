package io.github.surezzzzzz.sdk.kafka.route.support;

/**
 * Kafka 配置兼容 Helper
 *
 * @author surezzzzzz
 */
public final class KafkaConfigurationCompatibilityHelper {

    private KafkaConfigurationCompatibilityHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 销毁 producer factory
     *
     * @param producerFactory producer factory
     */
    public static void destroyProducerFactory(Object producerFactory) {
        KafkaProducerFactoryCompatibilityHelper.destroyProducerFactory(producerFactory);
    }

    /**
     * 销毁 consumer factory
     *
     * @param consumerFactory consumer factory
     */
    public static void destroyConsumerFactory(Object consumerFactory) {
        KafkaConsumerFactoryCompatibilityHelper.destroyConsumerFactory(consumerFactory);
    }
}
