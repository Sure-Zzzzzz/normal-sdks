package io.github.surezzzzzz.sdk.messaging.kafka.publisher.configuration;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteConfiguration;
import io.github.surezzzzzz.sdk.kafka.route.template.KafkaRouteTemplate;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.SimpleKafkaPublisherPackage;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.annotation.SimpleKafkaPublisherComponent;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.SimpleKafkaPublisherConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.customizer.KafkaPublishEnvelopeCustomizer;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.customizer.KafkaPublishHeaderCustomizer;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.DefaultKafkaPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.KafkaPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.generator.DefaultKafkaPublishMessageIdGenerator;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.generator.KafkaPublishMessageIdGenerator;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishKeyResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishRouteKeyResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishTopicResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishTraceResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.KafkaPublishKeyResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.KafkaPublishRouteKeyResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.KafkaPublishTopicResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.KafkaPublishTraceResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.serializer.JacksonKafkaPublishSerializer;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.serializer.KafkaPublishSerializer;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.support.KafkaPublishClock;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.support.SystemKafkaPublishClock;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.validator.DefaultKafkaPublishPropertiesValidator;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.validator.KafkaPublishPropertiesValidator;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Simple Kafka Publisher 自动配置
 *
 * @author surezzzzzz
 */
@Configuration
@AutoConfigureAfter(SimpleKafkaRouteConfiguration.class)
@ConditionalOnClass(KafkaRouteTemplate.class)
@ConditionalOnBean(KafkaRouteTemplate.class)
@ConditionalOnProperty(prefix = SimpleKafkaPublisherConstant.CONFIG_PREFIX,
        name = SimpleKafkaPublisherConstant.CONFIG_PROPERTY_ENABLE,
        havingValue = SimpleKafkaPublisherConstant.BOOLEAN_TRUE)
public class SimpleKafkaPublisherConfiguration {

    /**
     * 默认 Kafka Publisher 配置
     *
     * <p>调用方提供 KafkaPublisher 后，整条默认发布链和配置校验全部退场。
     *
     * @author surezzzzzz
     */
    @Configuration
    @ConditionalOnMissingBean(KafkaPublisher.class)
    @EnableConfigurationProperties(SimpleKafkaPublisherProperties.class)
    @ComponentScan(
            basePackageClasses = SimpleKafkaPublisherPackage.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(SimpleKafkaPublisherComponent.class)
    )
    public static class DefaultKafkaPublisherConfiguration {

        /**
         * 创建 KafkaPublisher
         *
         * @param kafkaRouteTemplate  route 模板
         * @param properties          publisher 配置
         * @param serializer          序列化器
         * @param topicResolver       topic 解析器
         * @param keyResolver         key 解析器
         * @param routeKeyResolver    routeKey 解析器
         * @param messageIdGenerator  messageId 生成器
         * @param traceResolver       traceId 解析器
         * @param clock                      发布时钟
         * @param headerCustomizerProvider   消息头自定义器 Provider
         * @param envelopeCustomizerProvider 消息封装自定义器 Provider
         * @return KafkaPublisher
         */
        @Bean
        public KafkaPublisher kafkaPublisher(KafkaRouteTemplate kafkaRouteTemplate,
                                             SimpleKafkaPublisherProperties properties,
                                             KafkaPublishSerializer serializer,
                                             KafkaPublishTopicResolver topicResolver,
                                             KafkaPublishKeyResolver keyResolver,
                                             KafkaPublishRouteKeyResolver routeKeyResolver,
                                             KafkaPublishMessageIdGenerator messageIdGenerator,
                                             KafkaPublishTraceResolver traceResolver,
                                             KafkaPublishClock clock,
                                             ObjectProvider<KafkaPublishHeaderCustomizer> headerCustomizerProvider,
                                             ObjectProvider<KafkaPublishEnvelopeCustomizer> envelopeCustomizerProvider) {
            List<KafkaPublishHeaderCustomizer> headerCustomizers = headerCustomizerProvider.orderedStream()
                    .collect(Collectors.toList());
            List<KafkaPublishEnvelopeCustomizer> envelopeCustomizers = envelopeCustomizerProvider.orderedStream()
                    .collect(Collectors.toList());
            return new DefaultKafkaPublisher(kafkaRouteTemplate, properties, serializer, topicResolver, keyResolver,
                    routeKeyResolver, messageIdGenerator, traceResolver, clock,
                    headerCustomizers, envelopeCustomizers);
        }

        /**
         * 创建默认配置校验器
         *
         * @return 配置校验器
         */
        @Bean
        @ConditionalOnMissingBean(KafkaPublishPropertiesValidator.class)
        public KafkaPublishPropertiesValidator kafkaPublishPropertiesValidator() {
            return new DefaultKafkaPublishPropertiesValidator();
        }

        /**
         * 创建配置校验触发器
         *
         * @param properties Publisher 配置
         * @param validator  配置校验器
         * @return 配置校验触发器
         */
        @Bean
        public InitializingBean kafkaPublishPropertiesValidationInitializer(
                SimpleKafkaPublisherProperties properties,
                KafkaPublishPropertiesValidator validator) {
            return () -> validator.validate(properties);
        }

        /**
         * 创建默认发布时钟
         *
         * @return 发布时钟
         */
        @Bean
        @ConditionalOnMissingBean(KafkaPublishClock.class)
        public KafkaPublishClock kafkaPublishClock() {
            return new SystemKafkaPublishClock();
        }

        /**
         * 创建默认序列化器
         *
         * @return KafkaPublishSerializer
         */
        @Bean
        @ConditionalOnMissingBean(KafkaPublishSerializer.class)
        public KafkaPublishSerializer kafkaPublishSerializer() {
            return new JacksonKafkaPublishSerializer();
        }

        /**
         * 创建默认 topic 解析器
         *
         * @return topic 解析器
         */
        @Bean
        @ConditionalOnMissingBean(KafkaPublishTopicResolver.class)
        public KafkaPublishTopicResolver kafkaPublishTopicResolver() {
            return new DefaultKafkaPublishTopicResolver();
        }

        /**
         * 创建默认 key 解析器
         *
         * @return key 解析器
         */
        @Bean
        @ConditionalOnMissingBean(KafkaPublishKeyResolver.class)
        public KafkaPublishKeyResolver kafkaPublishKeyResolver() {
            return new DefaultKafkaPublishKeyResolver();
        }

        /**
         * 创建默认 routeKey 解析器
         *
         * @return routeKey 解析器
         */
        @Bean
        @ConditionalOnMissingBean(KafkaPublishRouteKeyResolver.class)
        public KafkaPublishRouteKeyResolver kafkaPublishRouteKeyResolver() {
            return new DefaultKafkaPublishRouteKeyResolver();
        }

        /**
         * 创建默认 messageId 生成器
         *
         * @return messageId 生成器
         */
        @Bean
        @ConditionalOnMissingBean(KafkaPublishMessageIdGenerator.class)
        public KafkaPublishMessageIdGenerator kafkaPublishMessageIdGenerator() {
            return new DefaultKafkaPublishMessageIdGenerator();
        }

        /**
         * 创建默认 traceId 解析器
         *
         * @return traceId 解析器
         */
        @Bean
        @ConditionalOnMissingBean(KafkaPublishTraceResolver.class)
        public KafkaPublishTraceResolver kafkaPublishTraceResolver() {
            return new DefaultKafkaPublishTraceResolver();
        }
    }
}
