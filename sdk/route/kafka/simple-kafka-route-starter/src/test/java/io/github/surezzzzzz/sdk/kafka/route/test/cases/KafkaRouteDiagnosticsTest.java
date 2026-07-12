package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteConfiguration;
import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.diagnostic.DefaultKafkaRouteDiagnostics;
import io.github.surezzzzzz.sdk.kafka.route.diagnostic.KafkaRouteDiagnostics;
import io.github.surezzzzzz.sdk.kafka.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteBrokerDiagnosticResult;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteDiagnosticStatus;
import io.github.surezzzzzz.sdk.kafka.route.test.factory.MockKafkaConsumerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.test.factory.MockKafkaProducerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.test.support.KafkaRouteTestDataHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka route broker 诊断测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaRouteDiagnosticsTest {

    @Test
    public void testDiagnosticsDisabledDoesNotProbeBrokerAndReturnsEmptyResult() {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        properties.getDiagnostics().setEnable(false);
        DefaultKafkaRouteDiagnostics diagnostics = new DefaultKafkaRouteDiagnostics(properties);

        diagnostics.afterSingletonsInstantiated();
        Map<String, KafkaRouteBrokerDiagnosticResult> results = diagnostics.getDiagnosticResults();
        log.info("诊断关闭时结果数量: {}", results.size());

        assertTrue(results.isEmpty());
        assertNull(diagnostics.getDiagnosticResult("default"));
    }

    @Test
    public void testStartupCheckDisabledDoesNotProbeBrokerAndReturnsEmptyResult() {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        properties.getDiagnostics().setStartupCheck(false);
        DefaultKafkaRouteDiagnostics diagnostics = new DefaultKafkaRouteDiagnostics(properties);

        diagnostics.afterSingletonsInstantiated();
        Map<String, KafkaRouteBrokerDiagnosticResult> results = diagnostics.getDiagnosticResults();
        log.info("启动探测关闭时结果数量: {}", results.size());

        assertTrue(results.isEmpty());
    }

    @Test
    public void testDiagnosticsFailureDoesNotBlockStartupWhenFailFastDisabled() {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        properties.getDiagnostics().setFailFast(false);
        properties.getDiagnostics().setTimeoutMs(200L);
        DefaultKafkaRouteDiagnostics diagnostics = new DefaultKafkaRouteDiagnostics(properties);

        diagnostics.afterSingletonsInstantiated();
        Map<String, KafkaRouteBrokerDiagnosticResult> results = diagnostics.getDiagnosticResults();
        log.info("fail-fast 关闭时诊断结果: {}", results);

        assertEquals(2, results.size());
        assertEquals(KafkaRouteDiagnosticStatus.FAILED, results.get("default").getStatus());
        assertEquals(KafkaRouteDiagnosticStatus.FAILED, results.get("event").getStatus());
        assertNotNull(diagnostics.getDiagnosticResult("default"));
    }

    @Test
    public void testDiagnosticsFailureThrowsWhenFailFastEnabled() {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        properties.getDiagnostics().setFailFast(true);
        properties.getDiagnostics().setTimeoutMs(200L);
        DefaultKafkaRouteDiagnostics diagnostics = new DefaultKafkaRouteDiagnostics(properties);

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                diagnostics::afterSingletonsInstantiated);
        log.info("fail-fast 开启时诊断异常 errorCode={}", exception.getErrorCode());

        assertEquals(ErrorCode.KAFKA_ROUTE_013, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("default"));
        assertTrue(exception.getMessage().contains("event"));
        assertFalse(exception.getMessage().contains("127.0.0.1:9092"),
                "fail-fast 异常消息不得输出 bootstrap.servers 真实值");
        assertFalse(exception.getMessage().contains("127.0.0.1:9093"),
                "fail-fast 异常消息不得输出 bootstrap.servers 真实值");
    }

    @Test
    public void testCustomDiagnosticsBeanOverridesDefaultDiagnostics() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleKafkaRouteConfiguration.class))
                .withUserConfiguration(CustomDiagnosticsConfiguration.class)
                .withBean(io.github.surezzzzzz.sdk.kafka.route.factory.KafkaProducerFactoryFactory.class,
                        MockKafkaProducerFactoryFactory::new)
                .withBean(io.github.surezzzzzz.sdk.kafka.route.factory.KafkaConsumerFactoryFactory.class,
                        MockKafkaConsumerFactoryFactory::new)
                .withPropertyValues(
                        "io.github.surezzzzzz.sdk.kafka.route.enable=true",
                        "io.github.surezzzzzz.sdk.kafka.route.default-source=default",
                        "io.github.surezzzzzz.sdk.kafka.route.sources.default.bootstrap-servers[0]=127.0.0.1:65535",
                        "io.github.surezzzzzz.sdk.kafka.route.diagnostics.fail-fast=true",
                        "io.github.surezzzzzz.sdk.kafka.route.diagnostics.timeout-ms=200"
                )
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertSame(context.getBean(CustomDiagnosticsConfiguration.class).customKafkaRouteDiagnostics(),
                            context.getBean(KafkaRouteDiagnostics.class));
                    assertTrue(context.getBean(KafkaRouteDiagnostics.class).getDiagnosticResults().isEmpty());
                });
    }

    @Configuration
    static class CustomDiagnosticsConfiguration {

        private final KafkaRouteDiagnostics diagnostics = new KafkaRouteDiagnostics() {
            @Override
            public Map<String, KafkaRouteBrokerDiagnosticResult> getDiagnosticResults() {
                return java.util.Collections.emptyMap();
            }

            @Override
            public KafkaRouteBrokerDiagnosticResult getDiagnosticResult(String datasourceKey) {
                return null;
            }
        };

        @Bean
        public KafkaRouteDiagnostics customKafkaRouteDiagnostics() {
            return diagnostics;
        }
    }
}
