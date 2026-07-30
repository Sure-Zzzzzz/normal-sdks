package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.kafka.route.resolver.KafkaRouteResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.clientapplication.ConsumerApplication;
import io.github.surezzzzzz.sdk.messaging.kafka.clientapplication.handler.ExternalMarkedConsumer;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.configuration.SimpleKafkaConsumerConfiguration;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.container.KafkaConsumerContainerFactory;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.container.KafkaConsumerContainerManager;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.error.KafkaConsumerErrorHandler;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.KafkaConsumerIdempotencyChecker;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.NoOpKafkaConsumerIdempotencyChecker;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.listener.KafkaConsumerEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.ConsumerRegistration;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.registrar.KafkaConsumerRegistrar;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Consumer 自动配置测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class SimpleKafkaConsumerConfigurationTest {

    @Test
    public void testDisabledConsumerDoesNotRegisterBeans() {
        new ApplicationContextRunner().withUserConfiguration(SimpleKafkaConsumerConfiguration.class)
                .run(context -> {
                    assertFalse(context.containsBean("kafkaConsumerRegistrar"));
                    assertEquals(0, context.getBeansOfType(KafkaConsumerContainerManager.class).size());
                });
    }

    @Test
    public void testEnabledConsumerCreatesDefaultsWithoutGlobalKafkaBeans() {
        runner().run(context -> {
            log.info("Consumer 自动配置 Bean 数量：{}", context.getBeanDefinitionCount());
            assertNotNull(context.getBean(KafkaConsumerRegistrar.class));
            assertNotNull(context.getBean(KafkaConsumerContainerManager.class));
            assertTrue(context.getBean(KafkaConsumerIdempotencyChecker.class) instanceof NoOpKafkaConsumerIdempotencyChecker);
            assertEquals(0, context.getBeansOfType(ConsumerFactory.class).size());
            assertEquals(0, context.getBeansOfType(KafkaTemplate.class).size());
            assertEquals(0, context.getBeansOfType(KafkaListenerContainerFactory.class).size());
        });
    }

    @Test
    public void testApplicationPackageMarkedHandlerIsCreatedAndRegisteredOnce() {
        new ApplicationContextRunner()
                .withUserConfiguration(ConsumerApplication.class, SimpleKafkaConsumerConfiguration.class)
                .withBean(KafkaConsumerContainerManager.class, () -> mock(KafkaConsumerContainerManager.class))
                .withPropertyValues("io.github.surezzzzzz.sdk.messaging.kafka.consumer.enable=true",
                        "spring.profiles.active=mock-external-consumer")
                .run(context -> {
                    assertNotNull(context.getBean(ExternalMarkedConsumer.class));
                    List<ConsumerRegistration> registrations = context.getBean(KafkaConsumerRegistrar.class)
                            .getRegistrations();
                    assertEquals(1, registrations.size());
                    assertEquals("mock.external.application.topic", registrations.get(0).getTopic());
                });
    }

    @Test
    public void testCustomSpiOverridesDefaults() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleKafkaConsumerConfiguration.class))
                .withUserConfiguration(CustomSpiConfiguration.class)
                .withBean(SimpleKafkaRouteRegistry.class, () -> mock(SimpleKafkaRouteRegistry.class))
                .withBean(KafkaRouteResolver.class, () -> mock(KafkaRouteResolver.class))
                .withBean(SimpleKafkaRouteProperties.class, SimpleKafkaRouteProperties::new)
                .withPropertyValues("io.github.surezzzzzz.sdk.messaging.kafka.consumer.enable=true")
                .run(context -> {
                    assertSame(context.getBean("customChecker"), context.getBean(KafkaConsumerIdempotencyChecker.class));
                    assertSame(context.getBean("customListener"), context.getBean(KafkaConsumerEventListener.class));
                    assertSame(context.getBean("customContainerFactory"), context.getBean(KafkaConsumerContainerFactory.class));
                    assertSame(context.getBean("customErrorHandler"), context.getBean(KafkaConsumerErrorHandler.class));
                });
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(SimpleKafkaConsumerConfiguration.class)
                .withBean(SimpleKafkaRouteRegistry.class, () -> mock(SimpleKafkaRouteRegistry.class))
                .withBean(KafkaRouteResolver.class, () -> mock(KafkaRouteResolver.class))
                .withBean(SimpleKafkaRouteProperties.class, SimpleKafkaRouteProperties::new)
                .withPropertyValues("io.github.surezzzzzz.sdk.messaging.kafka.consumer.enable=true");
    }

    @Configuration
    static class CustomSpiConfiguration {

        @Bean
        public KafkaConsumerIdempotencyChecker customChecker() {
            return new NoOpKafkaConsumerIdempotencyChecker();
        }

        @Bean
        public KafkaConsumerEventListener customListener() {
            return context -> {
            };
        }

        @Bean
        public KafkaConsumerContainerFactory customContainerFactory() {
            return context -> mock(org.springframework.kafka.listener.MessageListenerContainer.class);
        }

        @Bean
        public KafkaConsumerErrorHandler<String, String> customErrorHandler() {
            return (record, cause, attempt) -> null;
        }
    }
}
