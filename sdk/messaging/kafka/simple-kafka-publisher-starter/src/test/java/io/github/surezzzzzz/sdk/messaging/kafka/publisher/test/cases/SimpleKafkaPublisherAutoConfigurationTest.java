package io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.kafka.route.template.KafkaRouteTemplate;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.configuration.SimpleKafkaPublisherConfiguration;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.configuration.SimpleKafkaPublisherProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.DefaultKafkaPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.KafkaPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishConfigurationException;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.KafkaPublishKeyResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.KafkaPublishRouteKeyResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.KafkaPublishTopicResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.serializer.KafkaPublishSerializer;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.support.KafkaPublishClock;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.generator.KafkaPublishMessageIdGenerator;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.KafkaPublishTraceResolver;
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
    public void testDefaultSerializerDoesNotRequireObjectMapper() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleKafkaPublisherConfiguration.class))
                .withBean(KafkaRouteTemplate.class, () -> mock(KafkaRouteTemplate.class))
                .withPropertyValues(ENABLE_PROPERTY)
                .run(context -> {
                    log.info("无 ObjectMapper Bean 时启动失败: {}", context.getStartupFailure());
                    assertNull(context.getStartupFailure(), "默认 serializer 不应依赖 ObjectMapper Bean");
                    assertEquals(1, context.getBeansOfType(KafkaPublisher.class).size(),
                            "无 ObjectMapper Bean 时仍应创建默认 publisher");
                    assertEquals(0, context.getBeansOfType(ObjectMapper.class).size(),
                            "publisher 不应注册全局 ObjectMapper Bean");
                });
    }

    @Test
    public void testGlobalObjectMapperConfigurationDoesNotAffectDefaultSerializer() {
        ObjectMapper globalObjectMapper = new ObjectMapper();
        globalObjectMapper.setPropertyNamingStrategy(
                com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleKafkaPublisherConfiguration.class))
                .withBean(KafkaRouteTemplate.class, () -> mock(KafkaRouteTemplate.class))
                .withBean(ObjectMapper.class, () -> globalObjectMapper)
                .withPropertyValues(ENABLE_PROPERTY)
                .run(context -> {
                    KafkaPublishSerializer serializer = context.getBean(KafkaPublishSerializer.class);
                    String result = serializer.serialize(
                            io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishSerializeContext.builder()
                                    .topic(KafkaPublisherTestHelper.TOPIC)
                                    .messageId(KafkaPublisherTestHelper.MESSAGE_ID)
                                    .messageType(KafkaPublisherTestHelper.MESSAGE_TYPE)
                                    .payload(new MockNamingPayload("mock-value"))
                                    .envelopeEnabled(false)
                                    .build());

                    log.info("全局 ObjectMapper 隔离序列化结果: {}", result);
                    assertSame(globalObjectMapper, context.getBean(ObjectMapper.class),
                            "publisher 不应替换调用方全局 ObjectMapper");
                    assertEquals("{\"mockValue\":\"mock-value\"}", result,
                            "默认 serializer 不应继承全局 ObjectMapper 的 snake_case 策略");
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

    @Test
    public void testCustomPublisherRetiresDefaultChain() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleKafkaPublisherConfiguration.class))
                .withUserConfiguration(CustomPublisherConfiguration.class)
                .withBean(KafkaRouteTemplate.class, () -> mock(KafkaRouteTemplate.class))
                .withPropertyValues(ENABLE_PROPERTY,
                        "io.github.surezzzzzz.sdk.messaging.kafka.publisher.send.timeout-ms=0")
                .run(context -> {
                    log.info("自定义 publisher 完整接管启动失败: {}", context.getStartupFailure());
                    assertNull(context.getStartupFailure(), "完整接管后默认配置校验不应执行");
                    assertSame(context.getBean("customPublisher"), context.getBean(KafkaPublisher.class),
                            "容器应使用调用方自定义 KafkaPublisher");
                    assertEquals(0, context.getBeansOfType(KafkaPublishSerializer.class).size(),
                            "完整接管后不应注册默认 serializer");
                    assertEquals(0, context.getBeansOfType(DefaultKafkaPublisher.class).size(),
                            "完整接管后不应注册 DefaultKafkaPublisher");
                    assertEquals(0, context.getBeansOfType(SimpleKafkaPublisherProperties.class).size(),
                            "完整接管后不应注册默认 publisher properties");
                    assertEquals(0, context.getBeansOfType(KafkaPublishTopicResolver.class).size(),
                            "完整接管后不应注册默认 topic resolver");
                    assertEquals(0, context.getBeansOfType(KafkaPublishKeyResolver.class).size(),
                            "完整接管后不应注册默认 key resolver");
                    assertEquals(0, context.getBeansOfType(KafkaPublishRouteKeyResolver.class).size(),
                            "完整接管后不应注册默认 routeKey resolver");
                    assertEquals(0, context.getBeansOfType(KafkaPublishMessageIdGenerator.class).size(),
                            "完整接管后不应注册默认 messageId generator");
                    assertEquals(0, context.getBeansOfType(KafkaPublishTraceResolver.class).size(),
                            "完整接管后不应注册默认 trace resolver");
                    assertEquals(0, context.getBeansOfType(KafkaPublishClock.class).size(),
                            "完整接管后不应注册默认 clock");
                    assertEquals(0, context.getBeansOfType(KafkaPublishPropertiesValidator.class).size(),
                            "完整接管后不应注册默认 validator");
                    assertFalse(context.containsBean("kafkaPublishPropertiesValidationInitializer"),
                            "完整接管后不应注册配置校验触发器");
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

    @lombok.Getter
    @lombok.AllArgsConstructor
    static class MockNamingPayload {
        private final String mockValue;
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
    static class CustomPublisherConfiguration {

        @Bean
        public KafkaPublisher customPublisher() {
            return mock(KafkaPublisher.class);
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
