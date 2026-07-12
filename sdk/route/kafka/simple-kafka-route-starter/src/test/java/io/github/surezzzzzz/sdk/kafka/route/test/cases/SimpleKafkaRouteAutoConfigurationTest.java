package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteConfiguration;
import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.diagnostic.KafkaRouteDiagnostics;
import io.github.surezzzzzz.sdk.kafka.route.factory.KafkaConsumerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.factory.KafkaProducerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.matcher.KafkaRoutePatternMatcher;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.kafka.route.resolver.KafkaRouteResolver;
import io.github.surezzzzzz.sdk.kafka.route.template.KafkaRouteTemplate;
import io.github.surezzzzzz.sdk.kafka.route.test.factory.MockKafkaConsumerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.test.factory.MockKafkaProducerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.validator.KafkaRoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka route 自动配置测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SimpleKafkaRouteAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SimpleKafkaRouteConfiguration.class)
            .withBean(KafkaProducerFactoryFactory.class, MockKafkaProducerFactoryFactory::new)
            .withBean(KafkaConsumerFactoryFactory.class, MockKafkaConsumerFactoryFactory::new)
            .withPropertyValues(
                    "io.github.surezzzzzz.sdk.kafka.route.enable=true",
                    "io.github.surezzzzzz.sdk.kafka.route.default-source=default",
                    "io.github.surezzzzzz.sdk.kafka.route.sources.default.bootstrap-servers[0]=127.0.0.1:9092",
                    "io.github.surezzzzzz.sdk.kafka.route.sources.default.client-id=default-client",
                    "io.github.surezzzzzz.sdk.kafka.route.sources.event.bootstrap-servers[0]=127.0.0.1:9093",
                    "io.github.surezzzzzz.sdk.kafka.route.sources.event.client-id=event-client",
                    "io.github.surezzzzzz.sdk.kafka.route.rules[0].pattern=event.",
                    "io.github.surezzzzzz.sdk.kafka.route.rules[0].type=prefix",
                    "io.github.surezzzzzz.sdk.kafka.route.rules[0].datasource=event",
                    "io.github.surezzzzzz.sdk.kafka.route.rules[0].priority=1",
                    "io.github.surezzzzzz.sdk.kafka.route.diagnostics.enable=false"
            );

    @Test
    public void testRouteBeansCreated() {
        contextRunner.run(context -> {
            log.info("Kafka route 自动配置 Bean 数量: {}", context.getBeanDefinitionNames().length);
            assertTrue(context.containsBean("simpleKafkaRouteRegistry"));
            assertTrue(context.containsBean("kafkaRouteTemplate"));
            assertTrue(context.containsBean("kafkaRoutePatternMatcher"));
            assertTrue(context.containsBean("kafkaRouteDiagnostics"));
            assertNotNull(context.getBean(KafkaRouteDiagnostics.class));
            assertTrue(context.getBean(SimpleKafkaRouteRegistry.class).containsDatasource("default"));
            assertTrue(context.getBean(SimpleKafkaRouteRegistry.class).containsDatasource("event"));
            assertSame(context.getBean(KafkaRouteTemplate.class).kafkaTemplate("event"),
                    context.getBean(KafkaRouteTemplate.class).kafkaTemplateByTopic("event.order.created"));
        });
    }

    @Test
    public void testDoesNotCreateGlobalKafkaBeans() {
        contextRunner.run(context -> {
            assertEquals(0, context.getBeansOfType(ProducerFactory.class).size());
            assertEquals(0, context.getBeansOfType(ConsumerFactory.class).size());
            assertEquals(0, context.getBeansOfType(org.springframework.kafka.core.KafkaTemplate.class).size());
        });
    }

    @Test
    public void testEnableFalseDoesNotCreateRouteBeans() {
        new ApplicationContextRunner()
                .withUserConfiguration(SimpleKafkaRouteConfiguration.class)
                .run(context -> {
                    assertFalse(context.containsBean("simpleKafkaRouteRegistry"));
                    assertFalse(context.containsBean("kafkaRouteTemplate"));
                    assertFalse(context.containsBean("kafkaRoutePatternMatcher"));
                });
    }

    @Test
    public void testMatcherRegisteredByPreciseComponentScan() {
        contextRunner.run(context -> assertNotNull(context.getBean(KafkaRoutePatternMatcher.class)));
    }

    @Test
    public void testCustomSpiBeansOverrideDefaultBeans() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleKafkaRouteConfiguration.class))
                .withUserConfiguration(CustomSpiConfiguration.class)
                .withPropertyValues(
                        "io.github.surezzzzzz.sdk.kafka.route.enable=true",
                        "io.github.surezzzzzz.sdk.kafka.route.default-source=default",
                        "io.github.surezzzzzz.sdk.kafka.route.sources.default.bootstrap-servers[0]=127.0.0.1:9092",
                        "io.github.surezzzzzz.sdk.kafka.route.diagnostics.enable=false"
                )
                .run(context -> {
                    assertSame(context.getBean("customProducerFactoryFactory"),
                            context.getBean(KafkaProducerFactoryFactory.class));
                    assertSame(context.getBean("customConsumerFactoryFactory"),
                            context.getBean(KafkaConsumerFactoryFactory.class));
                    assertSame(context.getBean("customKafkaRouteResolver"),
                            context.getBean(KafkaRouteResolver.class));
                    assertSame(context.getBean("customKafkaRoutePropertiesValidator"),
                            context.getBean(KafkaRoutePropertiesValidator.class));
                    assertTrue(context.getBean(CustomSpiConfiguration.class).validatorCalled.get());
                    assertTrue(context.getBean(SimpleKafkaRouteRegistry.class).containsDatasource("default"));
                });
    }

    @Configuration
    static class CustomSpiConfiguration {

        private final AtomicBoolean validatorCalled = new AtomicBoolean(false);

        @Bean
        public KafkaProducerFactoryFactory customProducerFactoryFactory() {
            return new MockKafkaProducerFactoryFactory();
        }

        @Bean
        public KafkaConsumerFactoryFactory customConsumerFactoryFactory() {
            return new MockKafkaConsumerFactoryFactory();
        }

        @Bean
        public KafkaRouteResolver customKafkaRouteResolver() {
            return new KafkaRouteResolver() {
                @Override
                public String resolveDataSource(io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteContext context) {
                    return "default";
                }

                @Override
                public SimpleKafkaRouteProperties.RouteRule resolveRule(
                        io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteContext context) {
                    return null;
                }
            };
        }

        @Bean
        public KafkaRoutePropertiesValidator customKafkaRoutePropertiesValidator() {
            return properties -> validatorCalled.set(true);
        }
    }
}
