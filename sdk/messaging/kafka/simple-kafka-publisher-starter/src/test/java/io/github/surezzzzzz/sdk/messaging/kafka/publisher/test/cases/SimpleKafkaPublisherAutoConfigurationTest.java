package io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.kafka.route.template.KafkaRouteTemplate;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.configuration.SimpleKafkaPublisherConfiguration;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.KafkaPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishConfigurationException;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.KafkaPublishKeyResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.KafkaPublishRouteKeyResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.KafkaPublishTopicResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.serializer.KafkaPublishSerializer;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.support.KafkaPublishClock;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.support.KafkaPublishMessageIdGenerator;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.support.KafkaPublishTraceResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.validator.KafkaPublishPropertiesValidator;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.support.KafkaPublisherTestHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Simple Kafka Publisher 自动配置测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SimpleKafkaPublisherAutoConfigurationTest {

    private static final String ENABLE_PROPERTY = "io.github.surezzzzzz.sdk.messaging.kafka.publisher.enable=true";

    @Test
    public void testDisabledDoesNotCreatePublisher() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleKafkaPublisherConfiguration.class))
                .withBean(KafkaRouteTemplate.class, () -> mock(KafkaRouteTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    log.info("publisher disabled Bean 数量: {}", context.getBeanDefinitionNames().length);
                    assertEquals(0, context.getBeansOfType(KafkaPublisher.class).size(),
                            "默认关闭时不应创建 KafkaPublisher");
                });
    }

    @Test
    public void testMissingRouteBeanDoesNotCreatePublisher() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleKafkaPublisherConfiguration.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues(ENABLE_PROPERTY)
                .run(context -> {
                    log.info("缺少 route Bean 时 publisher 数量: {}", context.getBeansOfType(KafkaPublisher.class).size());
                    assertEquals(0, context.getBeansOfType(KafkaPublisher.class).size(),
                            "缺少 KafkaRouteTemplate Bean 时不应创建 publisher");
                });
    }

    @Test
    public void testEnabledCreatesPublisherWithoutGlobalKafkaBeans() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleKafkaPublisherConfiguration.class))
                .withBean(KafkaRouteTemplate.class, () -> mock(KafkaRouteTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues(ENABLE_PROPERTY)
                .run(context -> {
                    log.info("publisher enabled Bean 数量: {}", context.getBeanDefinitionNames().length);
                    assertEquals(1, context.getBeansOfType(KafkaPublisher.class).size(),
                            "应创建一个 KafkaPublisher");
                    assertEquals(0, context.getBeansOfType(KafkaTemplate.class).size(),
                            "publisher 不应注册全局 KafkaTemplate");
                    assertEquals(0, context.getBeansOfType(ProducerFactory.class).size(),
                            "publisher 不应注册全局 ProducerFactory");
                    assertEquals(0, context.getBeansOfType(ConsumerFactory.class).size(),
                            "publisher 不应注册全局 ConsumerFactory");
                });
    }

    @Test
    public void testInvalidConfigurationFailsAtStartup() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleKafkaPublisherConfiguration.class))
                .withBean(KafkaRouteTemplate.class, () -> mock(KafkaRouteTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues(ENABLE_PROPERTY,
                        "io.github.surezzzzzz.sdk.messaging.kafka.publisher.send.timeout-ms=0")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    log.info("非法配置启动失败: {}", failure == null ? null : failure.getMessage());
                    assertNotNull(failure, "非法 timeout 配置应在启动期失败");
                    assertTrue(hasCause(failure, KafkaPublishConfigurationException.class),
                            "非法配置根因应包含 KafkaPublishConfigurationException");
                });
    }

    @Test
    public void testCustomValidatorAndClockOverrideDefaults() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleKafkaPublisherConfiguration.class))
                .withUserConfiguration(CustomExtensionConfiguration.class)
                .withBean(KafkaRouteTemplate.class, () -> mock(KafkaRouteTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues(ENABLE_PROPERTY)
                .run(context -> {
                    KafkaPublishPropertiesValidator validator =
                            context.getBean(KafkaPublishPropertiesValidator.class);
                    KafkaPublishClock clock = context.getBean(KafkaPublishClock.class);
                    log.info("自定义 validator={}, clock={}", validator, clock);
                    assertSame(context.getBean("customValidator"), validator,
                            "自定义配置校验器应覆盖默认实现");
                    assertSame(context.getBean("customClock"), clock,
                            "自定义发布时钟应覆盖默认实现");
                    assertEquals(KafkaPublisherTestHelper.RECORD_TIMESTAMP, clock.currentTimeMillis(),
                            "自定义发布时钟结果应精确生效");
                });
    }

    @Test
    public void testMissingObjectMapperReportsConfigurationException() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleKafkaPublisherConfiguration.class))
                .withBean(KafkaRouteTemplate.class, () -> mock(KafkaRouteTemplate.class))
                .withPropertyValues(ENABLE_PROPERTY)
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    log.info("缺少 ObjectMapper 启动失败: {}", failure == null ? null : failure.getMessage());
                    assertNotNull(failure, "缺少 ObjectMapper 且无自定义 serializer 时应启动失败");
                    assertTrue(hasCause(failure, KafkaPublishConfigurationException.class),
                            "根因应包含 KafkaPublishConfigurationException");
                });
    }

    @Test
    public void testCustomSerializerDoesNotRequireObjectMapper() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleKafkaPublisherConfiguration.class))
                .withUserConfiguration(CustomSerializerConfiguration.class)
                .withBean(KafkaRouteTemplate.class, () -> mock(KafkaRouteTemplate.class))
                .withPropertyValues(ENABLE_PROPERTY)
                .run(context -> {
                    KafkaPublishSerializer serializer = context.getBean(KafkaPublishSerializer.class);
                    log.info("自定义 serializer: {}", serializer);
                    assertSame(context.getBean("customSerializer"), serializer,
                            "自定义 serializer 应覆盖默认实现");
                    assertEquals(1, context.getBeansOfType(KafkaPublisher.class).size(),
                            "自定义 serializer 场景应正常创建 publisher");
                });
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Test
    public void testAllSpiBeansCanBeOverridden() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleKafkaPublisherConfiguration.class))
                .withUserConfiguration(CustomExtensionConfiguration.class, CustomSpiConfiguration.class)
                .withBean(KafkaRouteTemplate.class, () -> mock(KafkaRouteTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues(ENABLE_PROPERTY)
                .run(context -> {
                    log.info("全部 SPI 自定义 Bean 覆盖验证");
                    assertSame(context.getBean("customTopicResolver"),
                            context.getBean(KafkaPublishTopicResolver.class),
                            "自定义 topicResolver 应覆盖默认实现");
                    assertSame(context.getBean("customKeyResolver"),
                            context.getBean(KafkaPublishKeyResolver.class),
                            "自定义 keyResolver 应覆盖默认实现");
                    assertSame(context.getBean("customRouteKeyResolver"),
                            context.getBean(KafkaPublishRouteKeyResolver.class),
                            "自定义 routeKeyResolver 应覆盖默认实现");
                    assertSame(context.getBean("customMessageIdGenerator"),
                            context.getBean(KafkaPublishMessageIdGenerator.class),
                            "自定义 messageIdGenerator 应覆盖默认实现");
                    assertSame(context.getBean("customTraceResolver"),
                            context.getBean(KafkaPublishTraceResolver.class),
                            "自定义 traceResolver 应覆盖默认实现");
                    assertEquals(1, context.getBeansOfType(KafkaPublisher.class).size(),
                            "所有 SPI 覆盖后 publisher 仍应正常创建");
                });
    }

    @Configuration
    static class CustomSpiConfiguration {

        @Bean
        public KafkaPublishTopicResolver customTopicResolver() {
            return message -> "custom-topic";
        }

        @Bean
        public KafkaPublishKeyResolver customKeyResolver() {
            return message -> "custom-key";
        }

        @Bean
        public KafkaPublishRouteKeyResolver customRouteKeyResolver() {
            return message -> "custom-route";
        }

        @Bean
        public KafkaPublishMessageIdGenerator customMessageIdGenerator() {
            return () -> "custom-message-id";
        }

        @Bean
        public KafkaPublishTraceResolver customTraceResolver() {
            return () -> "custom-trace-id";
        }
    }

    @Configuration
    static class CustomExtensionConfiguration {

        @Bean
        public KafkaPublishPropertiesValidator customValidator() {
            return properties -> {
            };
        }

        @Bean
        public KafkaPublishClock customClock() {
            return () -> KafkaPublisherTestHelper.RECORD_TIMESTAMP;
        }
    }

    @Configuration
    static class CustomSerializerConfiguration {

        @Bean
        public KafkaPublishSerializer customSerializer() {
            return context -> KafkaPublisherTestHelper.PAYLOAD;
        }
    }
}
