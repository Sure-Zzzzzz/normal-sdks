package io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.template.KafkaRouteTemplate;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.configuration.SimpleKafkaPublisherConfiguration;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.configuration.SimpleKafkaPublisherProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.SimpleKafkaPublisherConstant;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Kafka Publisher 配置绑定测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaPublisherPropertiesBindingTest {

    private static final String ENABLE_PROPERTY = "io.github.surezzzzzz.sdk.messaging.kafka.publisher.enable=true";

    @Test
    public void testDefaultValuesAppliedWhenNotConfigured() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleKafkaPublisherConfiguration.class))
                .withBean(KafkaRouteTemplate.class, () -> mock(KafkaRouteTemplate.class))
                .withBean(com.fasterxml.jackson.databind.ObjectMapper.class,
                        com.fasterxml.jackson.databind.ObjectMapper::new)
                .withPropertyValues(ENABLE_PROPERTY)
                .run(context -> {
                    SimpleKafkaPublisherProperties properties =
                            context.getBean(SimpleKafkaPublisherProperties.class);
                    log.info("默认配置绑定结果: {}", properties);
                    assertTrue(properties.isEnable(), "enable 应绑定 true");
                    assertEquals(SimpleKafkaPublisherConstant.DEFAULT_APP_NAME, properties.getAppName(),
                            "app-name 默认值应引用常量");
                    assertTrue(properties.getEnvelope().isEnable(),
                            "envelope.enable 默认应为 true");
                    assertFalse(properties.getEnvelope().isIncludeNullPayload(),
                            "envelope.include-null-payload 默认应为 false");
                    assertTrue(properties.getHeaders().isEnableDefaultHeaders(),
                            "headers.enable-default-headers 默认应为 true");
                    assertFalse(properties.getHeaders().isAllowHeaderOverride(),
                            "headers.allow-header-override 默认应为 false");
                    assertEquals(SimpleKafkaPublisherConstant.DEFAULT_HEADER_MESSAGE_ID,
                            properties.getHeaders().getMessageIdHeader(),
                            "默认 message-id header 名称应引用常量");
                    assertEquals(SimpleKafkaPublisherConstant.DEFAULT_SEND_TIMEOUT_MS,
                            properties.getSend().getTimeoutMs(),
                            "send.timeout-ms 默认值应引用常量");
                });
    }

    @Test
    public void testCustomValuesBoundFromPropertySource() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleKafkaPublisherConfiguration.class))
                .withBean(KafkaRouteTemplate.class, () -> mock(KafkaRouteTemplate.class))
                .withBean(com.fasterxml.jackson.databind.ObjectMapper.class,
                        com.fasterxml.jackson.databind.ObjectMapper::new)
                .withPropertyValues(ENABLE_PROPERTY,
                        "io.github.surezzzzzz.sdk.messaging.kafka.publisher.app-name=mock-bound-app",
                        "io.github.surezzzzzz.sdk.messaging.kafka.publisher.envelope.include-null-payload=true",
                        "io.github.surezzzzzz.sdk.messaging.kafka.publisher.headers.allow-header-override=true",
                        "io.github.surezzzzzz.sdk.messaging.kafka.publisher.headers.message-id-header=x-custom-id",
                        "io.github.surezzzzzz.sdk.messaging.kafka.publisher.send.timeout-ms=5000")
                .run(context -> {
                    SimpleKafkaPublisherProperties properties =
                            context.getBean(SimpleKafkaPublisherProperties.class);
                    log.info("自定义配置绑定结果: {}", properties);
                    assertEquals("mock-bound-app", properties.getAppName(),
                            "app-name 应绑定自定义值");
                    assertTrue(properties.getEnvelope().isIncludeNullPayload(),
                            "envelope.include-null-payload 应绑定 true");
                    assertTrue(properties.getHeaders().isAllowHeaderOverride(),
                            "headers.allow-header-override 应绑定 true");
                    assertEquals("x-custom-id", properties.getHeaders().getMessageIdHeader(),
                            "headers.message-id-header 应绑定自定义值");
                    assertEquals(5000L, properties.getSend().getTimeoutMs(),
                            "send.timeout-ms 应绑定自定义值");
                });
    }

    @Test
    public void testDisabledDoesNotBindPublisherContext() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleKafkaPublisherConfiguration.class))
                .withBean(KafkaRouteTemplate.class, () -> mock(KafkaRouteTemplate.class))
                .withBean(com.fasterxml.jackson.databind.ObjectMapper.class,
                        com.fasterxml.jackson.databind.ObjectMapper::new)
                .run(context -> {
                    log.info("默认关闭时 publisher 数量: {}",
                            context.getBeansOfType(io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.KafkaPublisher.class).size());
                    assertEquals(0,
                            context.getBeansOfType(io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.KafkaPublisher.class).size(),
                            "enable 默认 false 时不应创建 publisher");
                });
    }
}
