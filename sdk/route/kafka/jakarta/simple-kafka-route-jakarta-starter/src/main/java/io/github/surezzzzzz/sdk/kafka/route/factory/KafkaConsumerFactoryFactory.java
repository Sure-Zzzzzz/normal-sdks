package io.github.surezzzzzz.sdk.kafka.route.factory;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.kafka.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaConsumerFactoryOverride;
import org.springframework.kafka.core.ConsumerFactory;

/**
 * Kafka ConsumerFactory 工厂
 *
 * @author surezzzzzz
 */
public interface KafkaConsumerFactoryFactory {

    /**
     * 创建 registry 持有的基础 ConsumerFactory
     *
     * @param datasourceKey 数据源标识
     * @param config        数据源配置
     * @return 基础 ConsumerFactory
     */
    ConsumerFactory<Object, Object> create(String datasourceKey, SimpleKafkaRouteProperties.DataSourceConfig config);

    /**
     * 创建调用方持有的独立 ConsumerFactory
     *
     * <p>即使 override 为 null，也必须返回独立实例，调用方负责销毁。未实现此方法的旧 SPI
     * 必须固定抛出 KAFKA_ROUTE_015，不能回落调用两参数方法。</p>
     *
     * @param datasourceKey 数据源标识
     * @param config        数据源配置
     * @param override      consumer 覆盖配置，可为 null
     * @return 独立 ConsumerFactory
     */
    default ConsumerFactory<Object, Object> create(String datasourceKey,
                                                   SimpleKafkaRouteProperties.DataSourceConfig config,
                                                   KafkaConsumerFactoryOverride override) {
        throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_015,
                String.format(ErrorMessage.CONSUMER_FACTORY_OVERRIDE_UNSUPPORTED, datasourceKey));
    }
}
