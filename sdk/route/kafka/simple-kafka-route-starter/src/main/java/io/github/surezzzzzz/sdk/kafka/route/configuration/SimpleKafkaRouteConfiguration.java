package io.github.surezzzzzz.sdk.kafka.route.configuration;

import io.github.surezzzzzz.sdk.kafka.route.SimpleKafkaRoutePackage;
import io.github.surezzzzzz.sdk.kafka.route.annotation.SimpleKafkaRouteComponent;
import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import io.github.surezzzzzz.sdk.kafka.route.diagnostic.DefaultKafkaRouteDiagnostics;
import io.github.surezzzzzz.sdk.kafka.route.diagnostic.KafkaRouteDiagnostics;
import io.github.surezzzzzz.sdk.kafka.route.factory.DefaultKafkaConsumerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.factory.DefaultKafkaProducerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.factory.KafkaConsumerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.factory.KafkaProducerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.matcher.KafkaRoutePatternMatcher;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.kafka.route.resolver.DefaultKafkaRouteResolver;
import io.github.surezzzzzz.sdk.kafka.route.resolver.KafkaRouteResolver;
import io.github.surezzzzzz.sdk.kafka.route.template.KafkaRouteTemplate;
import io.github.surezzzzzz.sdk.kafka.route.validator.DefaultKafkaRoutePropertiesValidator;
import io.github.surezzzzzz.sdk.kafka.route.validator.KafkaRoutePropertiesValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Simple Kafka Route 自动配置
 *
 * @author surezzzzzz
 */
@Configuration
@EnableConfigurationProperties(SimpleKafkaRouteProperties.class)
@ComponentScan(
        basePackageClasses = SimpleKafkaRoutePackage.class,
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(SimpleKafkaRouteComponent.class)
)
@ConditionalOnClass(name = SimpleKafkaRouteConstant.CLASS_NAME_KAFKA_TEMPLATE)
@ConditionalOnProperty(prefix = SimpleKafkaRouteConstant.CONFIG_PREFIX,
        name = SimpleKafkaRouteConstant.CONFIG_PROPERTY_ENABLE,
        havingValue = SimpleKafkaRouteConstant.BOOLEAN_TRUE)
public class SimpleKafkaRouteConfiguration {

    @Bean
    @ConditionalOnMissingBean(KafkaRoutePropertiesValidator.class)
    public KafkaRoutePropertiesValidator kafkaRoutePropertiesValidator(KafkaRoutePatternMatcher patternMatcher) {
        return new DefaultKafkaRoutePropertiesValidator(patternMatcher);
    }

    @Bean
    @ConditionalOnMissingBean(KafkaProducerFactoryFactory.class)
    public KafkaProducerFactoryFactory kafkaProducerFactoryFactory() {
        return new DefaultKafkaProducerFactoryFactory();
    }

    @Bean
    @ConditionalOnMissingBean(KafkaConsumerFactoryFactory.class)
    public KafkaConsumerFactoryFactory kafkaConsumerFactoryFactory() {
        return new DefaultKafkaConsumerFactoryFactory();
    }

    @Bean
    @ConditionalOnMissingBean(SimpleKafkaRouteRegistry.class)
    public SimpleKafkaRouteRegistry simpleKafkaRouteRegistry(SimpleKafkaRouteProperties properties,
                                                             KafkaRoutePropertiesValidator validator,
                                                             KafkaProducerFactoryFactory producerFactoryFactory,
                                                             KafkaConsumerFactoryFactory consumerFactoryFactory) {
        return new SimpleKafkaRouteRegistry(properties, validator, producerFactoryFactory, consumerFactoryFactory);
    }

    @Bean
    @ConditionalOnMissingBean(KafkaRouteResolver.class)
    public KafkaRouteResolver kafkaRouteResolver(SimpleKafkaRouteProperties properties,
                                                 KafkaRoutePatternMatcher patternMatcher) {
        return new DefaultKafkaRouteResolver(properties, patternMatcher);
    }

    @Bean
    @ConditionalOnMissingBean(KafkaRouteTemplate.class)
    public KafkaRouteTemplate kafkaRouteTemplate(SimpleKafkaRouteRegistry registry,
                                                 KafkaRouteResolver routeResolver) {
        return new KafkaRouteTemplate(registry, routeResolver);
    }

    @Bean
    @ConditionalOnMissingBean(KafkaRouteDiagnostics.class)
    public KafkaRouteDiagnostics kafkaRouteDiagnostics(SimpleKafkaRouteProperties properties) {
        return new DefaultKafkaRouteDiagnostics(properties);
    }
}
