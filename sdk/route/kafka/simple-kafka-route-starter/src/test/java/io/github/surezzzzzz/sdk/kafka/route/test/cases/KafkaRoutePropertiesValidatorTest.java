package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.constant.RouteMatchType;
import io.github.surezzzzzz.sdk.kafka.route.exception.SimpleKafkaRouteException;
import io.github.surezzzzzz.sdk.kafka.route.matcher.KafkaRoutePatternMatcher;
import io.github.surezzzzzz.sdk.kafka.route.test.support.KafkaRouteTestDataHelper;
import io.github.surezzzzzz.sdk.kafka.route.validator.DefaultKafkaRoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka route 配置校验器测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaRoutePropertiesValidatorTest {

    private final DefaultKafkaRoutePropertiesValidator validator =
            new DefaultKafkaRoutePropertiesValidator(new KafkaRoutePatternMatcher());

    @Test
    public void testSourcesCannotBeEmpty() {
        assertErrorCode(ErrorCode.KAFKA_ROUTE_001, () -> validator.validate(new SimpleKafkaRouteProperties()));
    }

    @Test
    public void testDefaultSourceMustExist() {
        SimpleKafkaRouteProperties properties = new SimpleKafkaRouteProperties();
        properties.setDefaultSource("missing");
        properties.getSources().put("default", KafkaRouteTestDataHelper.source("default-client"));

        assertErrorCode(ErrorCode.KAFKA_ROUTE_002, () -> validator.validate(properties));
    }

    @Test
    public void testBootstrapServerMustBeHostPort() {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        properties.getSources().get("default").setBootstrapServers(Collections.singletonList("127.0.0.1"));

        assertErrorCode(ErrorCode.KAFKA_ROUTE_005, () -> validator.validate(properties));
    }

    @Test
    public void testReservedRawPropertyDoesNotExposeValue() {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        properties.getSources().get("default").getProperties().put("sasl.jaas.config", "mock-secret-value");

        SimpleKafkaRouteException exception = assertErrorCode(ErrorCode.KAFKA_ROUTE_005,
                () -> validator.validate(properties));
        assertFalse(exception.getMessage().contains("mock-secret-value"));
    }

    @Test
    public void testSaslProtocolRequiresMechanism() {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        properties.getSources().get("default").getSecurity().setSecurityProtocol("SASL_SSL");

        assertErrorCode(ErrorCode.KAFKA_ROUTE_012, () -> validator.validate(properties));
    }

    @Test
    public void testInvalidProducerOption() {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        properties.getSources().get("default").getProducer().setAcks("bad");

        assertErrorCode(ErrorCode.KAFKA_ROUTE_005, () -> validator.validate(properties));
    }

    @Test
    public void testInvalidRouteType() {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        properties.getRules().get(0).setType("bad");

        assertErrorCode(ErrorCode.KAFKA_ROUTE_007, () -> validator.validate(properties));
    }

    @Test
    public void testInvalidRegexRule() {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        properties.getRules().clear();
        properties.getRules().add(KafkaRouteTestDataHelper.rule("[", RouteMatchType.REGEX.getCode(), "event", 1));

        assertErrorCode(ErrorCode.KAFKA_ROUTE_004, () -> validator.validate(properties));
    }

    private SimpleKafkaRouteException assertErrorCode(String errorCode, Executable executable) {
        SimpleKafkaRouteException exception = assertThrows(SimpleKafkaRouteException.class, executable);
        assertEquals(errorCode, exception.getErrorCode());
        return exception;
    }
}
