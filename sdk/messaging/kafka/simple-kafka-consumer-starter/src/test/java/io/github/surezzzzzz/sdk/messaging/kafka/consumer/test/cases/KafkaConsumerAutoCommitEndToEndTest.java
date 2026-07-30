package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.kafka.route.resolver.KafkaRouteResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.configuration.SimpleKafkaConsumerProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.container.KafkaConsumerContainerFactory;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.container.KafkaConsumerContainerManager;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.error.DeadLetterPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.error.KafkaConsumerErrorHandler;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.exception.KafkaConsumerConfigurationException;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.handler.KafkaConsumerHandler;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.NoOpKafkaConsumerIdempotencyChecker;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.listener.NoOpKafkaConsumerEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.ConsumerRegistration;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.registrar.KafkaConsumerRegistrar;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Kafka Consumer 自动提交配置校验测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaConsumerAutoCommitEndToEndTest {

    @Test
    public void testAutoCommitConfigurationFailsBeforeCreatingResources() {
        KafkaConsumerRegistrar registrar = new KafkaConsumerRegistrar();
        registrar.register(ConsumerRegistration.builder().id("mock-auto-commit").topic("mock-auto-commit-topic")
                .datasource("source-a").groupId("mock-auto-commit-group")
                .handler((KafkaConsumerHandler<String, String>) record -> {
                }).build());
        SimpleKafkaRouteRegistry routeRegistry = mock(SimpleKafkaRouteRegistry.class);
        SimpleKafkaRouteProperties routeProperties = new SimpleKafkaRouteProperties();
        SimpleKafkaRouteProperties.DataSourceConfig source = new SimpleKafkaRouteProperties.DataSourceConfig();
        source.getConsumer().setGroupId("mock-route-group");
        routeProperties.getSources().put("source-a", source);
        org.mockito.Mockito.when(routeRegistry.containsDatasource("source-a")).thenReturn(true);
        SimpleKafkaConsumerProperties properties = new SimpleKafkaConsumerProperties();
        properties.getContainer().setEnableAutoCommit(true);
        KafkaConsumerErrorHandler<String, String> errorHandler = (record, cause, attempt) -> null;
        DeadLetterPublisher deadLetterPublisher = (record, cause, attempt, errorCode) -> false;
        KafkaConsumerContainerFactory containerFactory = context -> mock(org.springframework.kafka.listener.MessageListenerContainer.class);
        KafkaConsumerContainerManager manager = new KafkaConsumerContainerManager(registrar, routeRegistry,
                mock(KafkaRouteResolver.class), routeProperties, properties, new NoOpKafkaConsumerIdempotencyChecker(),
                errorHandler, deadLetterPublisher, new NoOpKafkaConsumerEventListener(), containerFactory);

        KafkaConsumerConfigurationException exception = assertThrows(KafkaConsumerConfigurationException.class,
                manager::start);
        log.info("自动提交配置拒绝：errorCode={}，message={}", exception.getErrorCode(), exception.getMessage());

        assertEquals(ErrorCode.CONFIG_INVALID, exception.getErrorCode());
        assertEquals("消费配置或注册非法：enable-auto-commit-unsupported", exception.getMessage());
        verify(routeRegistry, never()).createConsumerFactory(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }
}
