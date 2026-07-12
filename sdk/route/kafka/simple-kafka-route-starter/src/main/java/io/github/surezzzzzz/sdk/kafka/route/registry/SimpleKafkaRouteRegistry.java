package io.github.surezzzzzz.sdk.kafka.route.registry;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.kafka.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.kafka.route.exception.RouteException;
import io.github.surezzzzzz.sdk.kafka.route.factory.KafkaConsumerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.factory.KafkaProducerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaConfigurationCompatibilityHelper;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaRouteStringHelper;
import io.github.surezzzzzz.sdk.kafka.route.validator.KafkaRoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Kafka route 注册表
 *
 * @author surezzzzzz
 */
@Slf4j
public class SimpleKafkaRouteRegistry implements DisposableBean {

    private final SimpleKafkaRouteProperties properties;
    private final Map<String, ProducerFactory<Object, Object>> producerFactories = new LinkedHashMap<>();
    private final Map<String, KafkaTemplate<Object, Object>> kafkaTemplates = new LinkedHashMap<>();
    private final Map<String, ConsumerFactory<Object, Object>> consumerFactories = new LinkedHashMap<>();
    private volatile boolean destroyed = false;

    public SimpleKafkaRouteRegistry(SimpleKafkaRouteProperties properties,
                                    KafkaRoutePropertiesValidator validator,
                                    KafkaProducerFactoryFactory producerFactoryFactory,
                                    KafkaConsumerFactoryFactory consumerFactoryFactory) {
        this.properties = properties;
        validator.validate(properties);
        initialize(producerFactoryFactory, consumerFactoryFactory);
    }

    public ProducerFactory<Object, Object> getProducerFactory() {
        return getProducerFactory(properties.getDefaultSource());
    }

    public ProducerFactory<Object, Object> getProducerFactory(String datasourceKey) {
        validateDatasourceKey(datasourceKey);
        ProducerFactory<Object, Object> factory = producerFactories.get(datasourceKey);
        if (factory == null) {
            throw datasourceNotFound(datasourceKey);
        }
        return factory;
    }

    public KafkaTemplate<Object, Object> getKafkaTemplate() {
        return getKafkaTemplate(properties.getDefaultSource());
    }

    public KafkaTemplate<Object, Object> getKafkaTemplate(String datasourceKey) {
        validateDatasourceKey(datasourceKey);
        KafkaTemplate<Object, Object> template = kafkaTemplates.get(datasourceKey);
        if (template == null) {
            throw datasourceNotFound(datasourceKey);
        }
        return template;
    }

    public ConsumerFactory<Object, Object> getConsumerFactory() {
        return getConsumerFactory(properties.getDefaultSource());
    }

    public ConsumerFactory<Object, Object> getConsumerFactory(String datasourceKey) {
        validateDatasourceKey(datasourceKey);
        ConsumerFactory<Object, Object> factory = consumerFactories.get(datasourceKey);
        if (factory == null) {
            throw datasourceNotFound(datasourceKey);
        }
        return factory;
    }

    public Set<String> getDatasourceKeys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(producerFactories.keySet()));
    }

    public boolean containsDatasource(String datasourceKey) {
        return producerFactories.containsKey(datasourceKey);
    }

    private void initialize(KafkaProducerFactoryFactory producerFactoryFactory,
                            KafkaConsumerFactoryFactory consumerFactoryFactory) {
        String datasourceKey = null;
        try {
            for (Map.Entry<String, SimpleKafkaRouteProperties.DataSourceConfig> entry : properties.getSources().entrySet()) {
                datasourceKey = entry.getKey();
                SimpleKafkaRouteProperties.DataSourceConfig config = entry.getValue();
                ProducerFactory<Object, Object> producerFactory = producerFactoryFactory.create(datasourceKey, config);
                producerFactories.put(datasourceKey, producerFactory);
                kafkaTemplates.put(datasourceKey, new KafkaTemplate<>(producerFactory));
                ConsumerFactory<Object, Object> consumerFactory = consumerFactoryFactory.create(datasourceKey, config);
                consumerFactories.put(datasourceKey, consumerFactory);
                log.info("Kafka route datasource 初始化完成，datasource=[{}]，bootstrapServerCount=[{}]，securityProtocol=[{}]，producerConfigured=[{}]，consumerConfigured=[{}]，transactionConfigured=[{}]",
                        datasourceKey,
                        config.getBootstrapServers() == null ? 0 : config.getBootstrapServers().size(),
                        config.getSecurity() == null ? null : config.getSecurity().getSecurityProtocol(),
                        config.getProducer() != null,
                        config.getConsumer() != null,
                        config.getProducer() != null && KafkaRouteStringHelper.hasText(config.getProducer().getTransactionIdPrefix()));
            }
        } catch (ConfigurationException e) {
            destroyCreatedFactories();
            throw e;
        } catch (Exception e) {
            destroyCreatedFactories();
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_006,
                    String.format(ErrorMessage.DATASOURCE_CREATE_FAILED, datasourceKey), e);
        }
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        destroyCreatedFactories();
    }

    private void destroyCreatedFactories() {
        for (Map.Entry<String, ProducerFactory<Object, Object>> entry : producerFactories.entrySet()) {
            try {
                KafkaConfigurationCompatibilityHelper.destroyProducerFactory(entry.getValue());
                log.info("Kafka route producer factory 已关闭，datasource=[{}]", entry.getKey());
            } catch (RuntimeException e) {
                log.warn("Kafka route producer factory 关闭失败，datasource=[{}]", entry.getKey(), e);
            }
        }
        for (Map.Entry<String, ConsumerFactory<Object, Object>> entry : consumerFactories.entrySet()) {
            try {
                KafkaConfigurationCompatibilityHelper.destroyConsumerFactory(entry.getValue());
                log.info("Kafka route consumer factory 已关闭，datasource=[{}]", entry.getKey());
            } catch (RuntimeException e) {
                log.warn("Kafka route consumer factory 关闭失败，datasource=[{}]", entry.getKey(), e);
            }
        }
        kafkaTemplates.clear();
        consumerFactories.clear();
        producerFactories.clear();
    }

    private void validateDatasourceKey(String datasourceKey) {
        if (!KafkaRouteStringHelper.hasText(datasourceKey)) {
            throw datasourceNotFound(datasourceKey);
        }
    }

    private RouteException datasourceNotFound(String datasourceKey) {
        return new RouteException(ErrorCode.KAFKA_ROUTE_003,
                String.format(ErrorMessage.DATASOURCE_NOT_FOUND, datasourceKey, getDatasourceKeys()));
    }
}
