package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.constant.RouteMatchType;
import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import io.github.surezzzzzz.sdk.kafka.route.exception.SimpleKafkaRouteException;
import io.github.surezzzzzz.sdk.kafka.route.matcher.KafkaRoutePatternMatcher;
import io.github.surezzzzzz.sdk.kafka.route.test.support.KafkaRouteTestDataHelper;
import io.github.surezzzzzz.sdk.kafka.route.validator.DefaultKafkaRoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.Collections;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka route 配置校验器测试
 *
 * @author surezzzzzz
 */
@Slf4j
@ResourceLock("default-locale")
public class KafkaRoutePropertiesValidatorTest {

    private final DefaultKafkaRoutePropertiesValidator validator =
            new DefaultKafkaRoutePropertiesValidator(new KafkaRoutePatternMatcher());

    @Test
    public void testSourcesCannotBeEmpty() {
        assertErrorCode(ErrorCode.KAFKA_ROUTE_001, "空数据源配置", () ->
                validator.validate(new SimpleKafkaRouteProperties()));
    }

    @Test
    public void testDefaultSourceMustExist() {
        SimpleKafkaRouteProperties properties = new SimpleKafkaRouteProperties();
        properties.setDefaultSource("missing");
        properties.getSources().put("default", KafkaRouteTestDataHelper.source("default-client"));

        assertErrorCode(ErrorCode.KAFKA_ROUTE_002, "默认数据源不存在", () -> validator.validate(properties));
    }

    @Test
    public void testBootstrapServerMustBeHostPort() {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        properties.getSources().get("default").setBootstrapServers(Collections.singletonList("127.0.0.1"));

        assertErrorCode(ErrorCode.KAFKA_ROUTE_005, "非法 bootstrap server", () -> validator.validate(properties));
    }

    @Test
    public void testReservedRawPropertyDoesNotExposeValue() {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        properties.getSources().get("default").getProperties().put("sasl.jaas.config", "mock-secret-value");

        SimpleKafkaRouteException exception = assertErrorCode(ErrorCode.KAFKA_ROUTE_005,
                "保留 raw key", () -> validator.validate(properties));
        assertFalse(exception.getMessage().contains("mock-secret-value"));
    }

    @Test
    public void testConsumerControlledRawPropertyRejectedAtDatasourceAndConsumerLevels() {
        String[] controlledKeys = {
                SimpleKafkaRouteConstant.PROPERTY_GROUP_ID,
                SimpleKafkaRouteConstant.PROPERTY_AUTO_OFFSET_RESET,
                SimpleKafkaRouteConstant.PROPERTY_ENABLE_AUTO_COMMIT,
                SimpleKafkaRouteConstant.PROPERTY_MAX_POLL_RECORDS
        };
        for (String key : controlledKeys) {
            assertConsumerControlledKeyRejected(key, true);
            assertConsumerControlledKeyRejected(key, false);
        }
    }

    @Test
    public void testLocaleRootValidatesTypedConfiguration() {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
            properties.getSources().get("default").getSecurity().setSecurityProtocol("sasl_plaintext");
            properties.getSources().get("default").getSecurity().setSaslMechanism("PLAIN");
            properties.getSources().get("default").getConsumer().setAutoOffsetReset("EARLIEST");
            properties.getSources().get("default").getProducer().setAcks("ALL");
            properties.getSources().get("default").getProducer().setCompressionType("GZIP");
            log.info("土耳其 Locale 下准备校验 typed 配置：securityProtocol={}，autoOffsetReset={}，acks={}，compression={}",
                    "sasl_plaintext", "EARLIEST", "ALL", "GZIP");

            assertDoesNotThrow(() -> validator.validate(properties));
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    public void testSaslProtocolRequiresMechanism() {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        properties.getSources().get("default").getSecurity().setSecurityProtocol("SASL_SSL");

        assertErrorCode(ErrorCode.KAFKA_ROUTE_012, "SASL 缺少 mechanism", () -> validator.validate(properties));
    }

    @Test
    public void testInvalidProducerOption() {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        properties.getSources().get("default").getProducer().setAcks("bad");

        assertErrorCode(ErrorCode.KAFKA_ROUTE_005, "非法 producer acks", () -> validator.validate(properties));
    }

    @Test
    public void testInvalidRouteType() {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        properties.getRules().get(0).setType("bad");

        assertErrorCode(ErrorCode.KAFKA_ROUTE_007, "非法 route type", () -> validator.validate(properties));
    }

    @Test
    public void testInvalidRegexRule() {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        properties.getRules().clear();
        properties.getRules().add(KafkaRouteTestDataHelper.rule("[", RouteMatchType.REGEX.getCode(), "event", 1));

        assertErrorCode(ErrorCode.KAFKA_ROUTE_004, "非法 regex", () -> validator.validate(properties));
    }

    private void assertConsumerControlledKeyRejected(String key, boolean datasourceLevel) {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        String inputKey = key.toUpperCase(Locale.ROOT);
        String inputValue = "mock-value";
        if (datasourceLevel) {
            properties.getSources().get("default").getProperties().put(inputKey, inputValue);
        } else {
            properties.getSources().get("default").getConsumer().getProperties().put(inputKey, inputValue);
        }
        SimpleKafkaRouteException exception = assertErrorCode(ErrorCode.KAFKA_ROUTE_005,
                "consumer 受控 raw key，level=" + (datasourceLevel ? "datasource" : "consumer")
                        + "，key=" + inputKey,
                () -> validator.validate(properties));
        assertTrue(exception.getMessage().contains(inputKey));
        assertFalse(exception.getMessage().contains(inputValue));
    }

    private SimpleKafkaRouteException assertErrorCode(String errorCode, String scenario, Executable executable) {
        log.info("准备执行异常校验：scenario={}，expectedErrorCode={}", scenario, errorCode);
        SimpleKafkaRouteException exception = assertThrows(SimpleKafkaRouteException.class, executable);
        log.info("异常校验结果：scenario={}，actualErrorCode={}，message={}",
                scenario, exception.getErrorCode(), exception.getMessage());
        assertEquals(errorCode, exception.getErrorCode());
        return exception;
    }
}
