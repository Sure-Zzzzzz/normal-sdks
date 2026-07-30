package io.github.surezzzzzz.sdk.messaging.kafka.consumer.configuration;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.kafka.route.resolver.KafkaRouteResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.SimpleKafkaConsumerConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.container.DefaultKafkaConsumerContainerFactory;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.container.KafkaConsumerContainerFactory;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.container.KafkaConsumerContainerManager;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.error.*;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.exception.KafkaConsumerConfigurationException;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.KafkaConsumerIdempotencyChecker;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.NoOpKafkaConsumerIdempotencyChecker;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.listener.CompositeKafkaConsumerEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.listener.KafkaConsumerEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.listener.NoOpKafkaConsumerEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.registrar.KafkaConsumerRegistrar;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.registrar.SimpleKafkaConsumerAnnotationHandler;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.registrar.SimpleKafkaConsumerComponentRegistrar;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.validator.DefaultKafkaConsumerPropertiesValidator;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.validator.KafkaConsumerPropertiesValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.stream.Collectors;

/**
 * Simple Kafka Consumer 自动配置
 *
 * @author surezzzzzz
 */
@Configuration
@EnableConfigurationProperties(SimpleKafkaConsumerProperties.class)
@ConditionalOnClass(name = SimpleKafkaConsumerConstant.CLASS_NAME_KAFKA_TEMPLATE)
@ConditionalOnProperty(prefix = SimpleKafkaConsumerConstant.CONFIG_PREFIX,
        name = SimpleKafkaConsumerConstant.CONFIG_PROPERTY_ENABLE,
        havingValue = "true")
public class SimpleKafkaConsumerConfiguration {

    @Bean
    @ConditionalOnMissingBean(SimpleKafkaConsumerComponentRegistrar.class)
    public static SimpleKafkaConsumerComponentRegistrar simpleKafkaConsumerComponentRegistrar() {
        return new SimpleKafkaConsumerComponentRegistrar();
    }

    @Bean
    @ConditionalOnMissingBean(KafkaConsumerPropertiesValidator.class)
    public KafkaConsumerPropertiesValidator kafkaConsumerPropertiesValidator() {
        return new DefaultKafkaConsumerPropertiesValidator();
    }

    @Bean
    @ConditionalOnMissingBean(KafkaConsumerRegistrar.class)
    public KafkaConsumerRegistrar kafkaConsumerRegistrar() {
        return new KafkaConsumerRegistrar();
    }

    @Bean
    @ConditionalOnMissingBean(SimpleKafkaConsumerAnnotationHandler.class)
    public SimpleKafkaConsumerAnnotationHandler simpleKafkaConsumerAnnotationHandler(
            ConfigurableListableBeanFactory beanFactory, KafkaConsumerRegistrar registrar) {
        return new SimpleKafkaConsumerAnnotationHandler(beanFactory, registrar);
    }

    @Bean
    @ConditionalOnMissingBean(RetryableExceptionClassifier.class)
    public RetryableExceptionClassifier retryableExceptionClassifier() {
        return new DefaultRetryableExceptionClassifier();
    }

    @Bean
    @ConditionalOnMissingBean(KafkaConsumerBackoffPolicy.class)
    public KafkaConsumerBackoffPolicy kafkaConsumerBackoffPolicy(SimpleKafkaConsumerProperties properties) {
        return new DefaultKafkaConsumerBackoffPolicy(properties);
    }

    @Bean
    @ConditionalOnMissingBean(KafkaConsumerErrorHandler.class)
    public DefaultKafkaConsumerErrorHandler<String, String> kafkaConsumerErrorHandler(
            SimpleKafkaConsumerProperties properties, RetryableExceptionClassifier classifier,
            KafkaConsumerBackoffPolicy backoffPolicy) {
        return new DefaultKafkaConsumerErrorHandler<>(properties, classifier, backoffPolicy);
    }

    @Bean
    @ConditionalOnMissingBean(DeadLetterPublisher.class)
    public DefaultDeadLetterPublisher deadLetterPublisher(SimpleKafkaConsumerProperties properties,
                                                          ObjectProvider<SimpleKafkaRouteRegistry> registryProvider) {
        return new DefaultDeadLetterPublisher(registryProvider.getIfAvailable(), properties);
    }

    @Bean
    @ConditionalOnMissingBean(KafkaConsumerContainerFactory.class)
    public DefaultKafkaConsumerContainerFactory kafkaConsumerContainerFactory() {
        return new DefaultKafkaConsumerContainerFactory();
    }

    @Bean
    @ConditionalOnMissingBean(KafkaConsumerIdempotencyChecker.class)
    @ConditionalOnProperty(prefix = SimpleKafkaConsumerConstant.CONFIG_PREFIX,
            name = SimpleKafkaConsumerConstant.CONFIG_PROPERTY_IDEMPOTENCY_ENABLE,
            havingValue = "false", matchIfMissing = true)
    public KafkaConsumerIdempotencyChecker noOpKafkaConsumerIdempotencyChecker() {
        return new NoOpKafkaConsumerIdempotencyChecker();
    }

    @Bean
    @ConditionalOnMissingBean(KafkaConsumerEventListener.class)
    public KafkaConsumerEventListener noOpKafkaConsumerEventListener() {
        return new NoOpKafkaConsumerEventListener();
    }

    @Bean
    @ConditionalOnMissingBean(KafkaConsumerContainerManager.class)
    public KafkaConsumerContainerManager kafkaConsumerContainerManager(
            KafkaConsumerRegistrar registrar,
            SimpleKafkaConsumerProperties properties,
            KafkaConsumerPropertiesValidator validator,
            KafkaConsumerErrorHandler<String, String> errorHandler,
            DeadLetterPublisher deadLetterPublisher,
            KafkaConsumerContainerFactory containerFactory,
            ObjectProvider<SimpleKafkaRouteRegistry> registryProvider,
            ObjectProvider<KafkaRouteResolver> routeResolverProvider,
            ObjectProvider<SimpleKafkaRouteProperties> routePropertiesProvider,
            ObjectProvider<KafkaConsumerIdempotencyChecker> checkerProvider,
            ObjectProvider<KafkaConsumerEventListener> eventListenerProvider) {
        validator.validate(properties);
        SimpleKafkaRouteRegistry routeRegistry = registryProvider.getIfAvailable();
        if (routeRegistry == null) {
            throw new KafkaConsumerConfigurationException(ErrorCode.CONFIG_INVALID,
                    String.format(ErrorMessage.CONFIG_INVALID,
                            SimpleKafkaConsumerConstant.REASON_ROUTE_REGISTRY_MISSING));
        }
        KafkaRouteResolver routeResolver = routeResolverProvider.getIfAvailable();
        SimpleKafkaRouteProperties routeProperties = routePropertiesProvider.getIfAvailable();
        if (routeResolver == null || routeProperties == null) {
            throw new KafkaConsumerConfigurationException(ErrorCode.CONFIG_INVALID,
                    String.format(ErrorMessage.CONFIG_INVALID,
                            SimpleKafkaConsumerConstant.REASON_ROUTE_REGISTRY_MISSING));
        }
        KafkaConsumerIdempotencyChecker idempotencyChecker = checkerProvider.getIfAvailable();
        if (idempotencyChecker == null) {
            throw new KafkaConsumerConfigurationException(ErrorCode.CONFIG_INVALID,
                    String.format(ErrorMessage.CONFIG_INVALID,
                            SimpleKafkaConsumerConstant.REASON_IDEMPOTENCY_REDIS_MISSING));
        }
        KafkaConsumerEventListener eventListener = new CompositeKafkaConsumerEventListener(
                eventListenerProvider.orderedStream().collect(Collectors.toList()));
        return new KafkaConsumerContainerManager(registrar, routeRegistry, routeResolver, routeProperties,
                properties, idempotencyChecker, errorHandler, deadLetterPublisher, eventListener, containerFactory);
    }
}
