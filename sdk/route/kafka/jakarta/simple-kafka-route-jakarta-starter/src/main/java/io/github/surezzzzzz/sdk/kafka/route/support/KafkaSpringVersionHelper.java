package io.github.surezzzzzz.sdk.kafka.route.support;

import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Spring Kafka 版本兼容 Helper
 *
 * @author surezzzzzz
 */
public final class KafkaSpringVersionHelper {

    private static volatile String detectedVersion;

    private KafkaSpringVersionHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 探测 Spring Kafka 版本
     *
     * @return Spring Kafka 实现版本，探测失败返回 unknown
     */
    public static String detectSpringKafkaVersion() {
        if (detectedVersion == null) {
            synchronized (KafkaSpringVersionHelper.class) {
                if (detectedVersion == null) {
                    detectedVersion = doDetectSpringKafkaVersion();
                }
            }
        }
        return detectedVersion;
    }

    /**
     * 判断当前 Spring Kafka 是否为 2.x
     *
     * @return true 表示 2.x
     */
    public static boolean isSpringKafka2x() {
        String version = detectSpringKafkaVersion();
        return version != null && version.startsWith(SimpleKafkaRouteConstant.SPRING_KAFKA_2_VERSION_PREFIX);
    }

    /**
     * 判断默认 ProducerFactory 实现是否可读取 transactionIdPrefix
     *
     * @return true 表示可读取
     */
    public static boolean supportsGetTransactionIdPrefix() {
        return KafkaReflectionHelper.findMethod(DefaultKafkaProducerFactory.class,
                SimpleKafkaRouteConstant.REFLECT_METHOD_GET_TRANSACTION_ID_PREFIX) != null;
    }

    /**
     * 判断 ProducerFactory 是否支持 copyWithConfigurationOverride
     *
     * @return true 表示支持
     */
    public static boolean supportsCopyWithConfigurationOverride() {
        return KafkaReflectionHelper.findMethod(ProducerFactory.class,
                SimpleKafkaRouteConstant.REFLECT_METHOD_COPY_WITH_CONFIGURATION_OVERRIDE, java.util.Map.class) != null;
    }

    /**
     * 判断 ConsumerFactory 是否支持 getConfigurationProperties
     *
     * @return true 表示支持
     */
    public static boolean supportsConsumerFactoryGetConfigProperties() {
        return KafkaReflectionHelper.findMethod(ConsumerFactory.class, SimpleKafkaRouteConstant.REFLECT_METHOD_GET_CONFIGURATION_PROPERTIES) != null;
    }

    /**
     * 判断 KafkaTemplate 是否支持 isTransactional
     *
     * @return true 表示支持
     */
    public static boolean supportsKafkaTemplateIsTransactional() {
        return KafkaReflectionHelper.findMethod(KafkaTemplate.class, SimpleKafkaRouteConstant.REFLECT_METHOD_IS_TRANSACTIONAL) != null;
    }

    private static String doDetectSpringKafkaVersion() {
        Package kafkaPackage = KafkaTemplate.class.getPackage();
        if (kafkaPackage == null || !KafkaRouteStringHelper.hasText(kafkaPackage.getImplementationVersion())) {
            return SimpleKafkaRouteConstant.UNKNOWN_VERSION;
        }
        return kafkaPackage.getImplementationVersion();
    }
}
