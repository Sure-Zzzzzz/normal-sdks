package io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.configuration.SimpleKafkaPublisherProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishConfigurationException;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.support.KafkaPublisherTestHelper;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.validator.DefaultKafkaPublishPropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Kafka Publisher 配置校验测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaPublishPropertiesValidatorTest {

    private final DefaultKafkaPublishPropertiesValidator validator =
            new DefaultKafkaPublishPropertiesValidator();

    @Test
    public void testDefaultPropertiesValid() {
        SimpleKafkaPublisherProperties properties = new SimpleKafkaPublisherProperties();

        log.info("默认 Publisher 配置: {}", properties);
        assertDoesNotThrow(() -> validator.validate(properties), "默认配置应通过校验");
    }

    @Test
    public void testTimeoutMustBePositive() {
        SimpleKafkaPublisherProperties properties = KafkaPublisherTestHelper.properties();
        properties.getSend().setTimeoutMs(0L);

        KafkaPublishConfigurationException exception = assertThrows(
                KafkaPublishConfigurationException.class, () -> validator.validate(properties));

        log.info("非法 timeout 配置错误: {}", exception.getMessage());
        assertEquals(ErrorCode.KAFKA_PUBLISHER_001, exception.getErrorCode(),
                "非法 timeout 应使用配置错误码");
    }

    @Test
    public void testNestedConfigCannotBeNull() {
        SimpleKafkaPublisherProperties properties = KafkaPublisherTestHelper.properties();
        properties.setHeaders(null);

        KafkaPublishConfigurationException exception = assertThrows(
                KafkaPublishConfigurationException.class, () -> validator.validate(properties));

        log.info("空嵌套配置错误: {}", exception.getMessage());
        assertEquals(ErrorCode.KAFKA_PUBLISHER_001, exception.getErrorCode(),
                "空嵌套配置应使用配置错误码");
    }

    @Test
    public void testHeaderNamesMustBeUniqueCaseInsensitively() {
        SimpleKafkaPublisherProperties properties = KafkaPublisherTestHelper.properties();
        properties.getHeaders().setMessageTypeHeader("X-Message-Id");

        KafkaPublishConfigurationException exception = assertThrows(
                KafkaPublishConfigurationException.class, () -> validator.validate(properties));

        log.info("重复 header 配置错误: {}", exception.getMessage());
        assertEquals(ErrorCode.KAFKA_PUBLISHER_001, exception.getErrorCode(),
                "大小写不同的重复 header 名称应启动失败");
    }

    @Test
    public void testHeaderNameCannotBeBlankOrContainControlCharacter() {
        SimpleKafkaPublisherProperties blankProperties = KafkaPublisherTestHelper.properties();
        blankProperties.getHeaders().setTraceIdHeader(" ");
        SimpleKafkaPublisherProperties controlProperties = KafkaPublisherTestHelper.properties();
        controlProperties.getHeaders().setSourceHeader("\nx-source");

        KafkaPublishConfigurationException blankException = assertThrows(
                KafkaPublishConfigurationException.class, () -> validator.validate(blankProperties));
        KafkaPublishConfigurationException controlException = assertThrows(
                KafkaPublishConfigurationException.class, () -> validator.validate(controlProperties));

        log.info("空 header 名称错误: {}", blankException.getMessage());
        log.info("控制字符 header 名称错误: {}", controlException.getMessage());
        assertEquals(ErrorCode.KAFKA_PUBLISHER_001, blankException.getErrorCode(),
                "空 header 名称应启动失败");
        assertEquals(ErrorCode.KAFKA_PUBLISHER_001, controlException.getErrorCode(),
                "含控制字符的 header 名称应启动失败");
    }

    @Test
    public void testDisabledDefaultHeadersIgnoreUnusedHeaderNameConfiguration() {
        SimpleKafkaPublisherProperties properties = KafkaPublisherTestHelper.properties();
        properties.getHeaders().setEnableDefaultHeaders(false);
        properties.getHeaders().setMessageIdHeader(" ");
        properties.getHeaders().setMessageTypeHeader(" ");

        log.info("默认 header 关闭后的配置: {}", properties.getHeaders());
        assertDoesNotThrow(() -> validator.validate(properties),
                "默认 header 关闭后，未生效的 header 名称配置不应阻止应用启动");
    }

    @Test
    public void testAppNameRequiredOnlyWhenUsed() {
        SimpleKafkaPublisherProperties invalidProperties = KafkaPublisherTestHelper.properties();
        invalidProperties.setAppName(" ");
        invalidProperties.getHeaders().setEnableDefaultHeaders(true);
        SimpleKafkaPublisherProperties validProperties = KafkaPublisherTestHelper.properties();
        validProperties.setAppName(" ");
        validProperties.getEnvelope().setEnable(false);
        validProperties.getHeaders().setEnableDefaultHeaders(false);

        KafkaPublishConfigurationException exception = assertThrows(
                KafkaPublishConfigurationException.class, () -> validator.validate(invalidProperties));

        log.info("空 app-name 配置错误: {}", exception.getMessage());
        assertEquals(ErrorCode.KAFKA_PUBLISHER_001, exception.getErrorCode(),
                "默认 header 使用 source 时 app-name 不能为空");
        assertDoesNotThrow(() -> validator.validate(validProperties),
                "envelope 和默认 header 均关闭时允许 app-name 为空");
    }
}
