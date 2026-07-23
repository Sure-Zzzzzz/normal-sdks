package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteConfiguration;
import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import io.github.surezzzzzz.sdk.kafka.route.diagnostic.DefaultKafkaRouteDiagnostics;
import io.github.surezzzzzz.sdk.kafka.route.diagnostic.KafkaRouteDiagnostics;
import io.github.surezzzzzz.sdk.kafka.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteBrokerCapability;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteBrokerDiagnosticResult;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteDiagnosticStatus;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaAdminCompatibilityHelper;
import io.github.surezzzzzz.sdk.kafka.route.test.factory.MockKafkaConsumerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.test.factory.MockKafkaProducerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.test.support.KafkaRouteTestDataHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka route broker 诊断测试
 *
 * @author surezzzzzz
 */
@Slf4j
@ResourceLock("default-locale")
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
    public void testSuccessResultReportsUnknownWhenFeaturesNull() throws Exception {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        DefaultKafkaRouteDiagnostics diagnostics = new DefaultKafkaRouteDiagnostics(properties);
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("diagnostic-client");

        KafkaRouteBrokerDiagnosticResult result = invokeSuccessResult(diagnostics, "diagnostic",
                null, config);
        log.info("feature API 不可用时诊断结果: {}", result);

        assertEquals(KafkaRouteDiagnosticStatus.SUCCESS, result.getStatus());
        assertEquals(KafkaRouteBrokerCapability.SUPPORTED, result.getAdminApiLevel(),
                "describeCluster 成功后基础 Admin API 应视为可用");
        assertEquals(KafkaRouteBrokerCapability.UNKNOWN, result.getTransactionSupported());
        assertEquals(KafkaRouteBrokerCapability.UNKNOWN, result.getIdempotenceSupported());
        assertEquals(KafkaRouteBrokerCapability.UNKNOWN, result.getZstdSupported());
        assertEquals("mock-cluster", result.getClusterId());
        assertEquals(1, result.getNodeCount());
        assertTrue(result.isControllerVisible());
        assertNull(result.getFailureReason());
    }

    @Test
    public void testSuccessResultReportsSupportedWhenFeaturesAvailable() throws Exception {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        DefaultKafkaRouteDiagnostics diagnostics = new DefaultKafkaRouteDiagnostics(properties);
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("diagnostic-client");

        KafkaRouteBrokerDiagnosticResult result = invokeSuccessResult(diagnostics, "diagnostic",
                new Object(), config);
        log.info("feature API 可用时诊断结果: {}", result);

        assertEquals(KafkaRouteDiagnosticStatus.SUCCESS, result.getStatus());
        assertEquals(KafkaRouteBrokerCapability.SUPPORTED, result.getAdminApiLevel());
        assertEquals(KafkaRouteBrokerCapability.SUPPORTED, result.getTransactionSupported());
        assertEquals(KafkaRouteBrokerCapability.SUPPORTED, result.getIdempotenceSupported());
        assertEquals(KafkaRouteBrokerCapability.SUPPORTED, result.getZstdSupported());
        assertEquals("mock-cluster", result.getClusterId());
        assertEquals(1, result.getNodeCount());
        assertTrue(result.isControllerVisible());
        assertNull(result.getFailureReason());
    }

    @Test
    public void testSuccessResultWarnsWhenTransactionRequiredButFeatureUnknown() throws Exception {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        DefaultKafkaRouteDiagnostics diagnostics = new DefaultKafkaRouteDiagnostics(properties);
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("diagnostic-client");
        config.getProducer().setTransactionIdPrefix("mock-tx-");

        KafkaRouteBrokerDiagnosticResult result = invokeSuccessResult(diagnostics, "diagnostic",
                null, config);
        log.info("事务能力未知时诊断结果: {}", result);

        assertEquals(KafkaRouteDiagnosticStatus.WARN, result.getStatus());
        assertEquals(KafkaRouteBrokerCapability.UNKNOWN, result.getTransactionSupported());
    }

    @Test
    public void testSuccessResultWarnsWhenTypedIdempotenceRequiredButFeatureUnknown() throws Exception {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        DefaultKafkaRouteDiagnostics diagnostics = new DefaultKafkaRouteDiagnostics(properties);
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("diagnostic-client");
        config.getProducer().setEnableIdempotence(Boolean.TRUE);

        KafkaRouteBrokerDiagnosticResult result = invokeSuccessResult(diagnostics, "diagnostic",
                null, config);
        log.info("幂等能力未知时诊断结果: {}", result);

        assertEquals(KafkaRouteDiagnosticStatus.WARN, result.getStatus());
        assertEquals(KafkaRouteBrokerCapability.UNKNOWN, result.getIdempotenceSupported());
    }

    @Test
    public void testSuccessResultWarnsWhenRawIdempotenceRequiredButFeatureUnknown() throws Exception {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        DefaultKafkaRouteDiagnostics diagnostics = new DefaultKafkaRouteDiagnostics(properties);
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("diagnostic-client");
        config.getProducer().getProperties().put(SimpleKafkaRouteConstant.PROPERTY_ENABLE_IDEMPOTENCE,
                SimpleKafkaRouteConstant.BOOLEAN_TRUE);

        KafkaRouteBrokerDiagnosticResult result = invokeSuccessResult(diagnostics, "diagnostic",
                null, config);
        log.info("raw properties 幂等能力未知时诊断结果: {}", result);

        assertEquals(KafkaRouteDiagnosticStatus.WARN, result.getStatus());
        assertEquals(KafkaRouteBrokerCapability.UNKNOWN, result.getIdempotenceSupported());
    }

    @Test
    public void testLocaleRootKeepsDiagnosticCapabilityAndSensitiveMessageMatching() throws Exception {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
            DefaultKafkaRouteDiagnostics diagnostics = new DefaultKafkaRouteDiagnostics(properties);
            SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("diagnostic-client");
            config.getProducer().setCompressionType("GZIP");
            log.info("土耳其 Locale 下准备验证诊断压缩类型和敏感消息匹配");

            KafkaRouteBrokerDiagnosticResult result = invokeSuccessResult(diagnostics, "diagnostic", null, config);
            assertEquals(KafkaRouteDiagnosticStatus.SUCCESS, result.getStatus());
            Method method = DefaultKafkaRouteDiagnostics.class.getDeclaredMethod("containsSensitiveFragment", String.class);
            method.setAccessible(true);
            assertEquals(Boolean.TRUE, method.invoke(diagnostics, "MOCK-SASL.JAAS.CONFIG"));
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    public void testSuccessResultWarnsWhenTypedZstdRequiredButFeatureUnknown() throws Exception {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        DefaultKafkaRouteDiagnostics diagnostics = new DefaultKafkaRouteDiagnostics(properties);
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("diagnostic-client");
        config.getProducer().setCompressionType(SimpleKafkaRouteConstant.COMPRESSION_TYPE_ZSTD);

        KafkaRouteBrokerDiagnosticResult result = invokeSuccessResult(diagnostics, "diagnostic",
                null, config);
        log.info("zstd 能力未知时诊断结果: {}", result);

        assertEquals(KafkaRouteDiagnosticStatus.WARN, result.getStatus());
        assertEquals(KafkaRouteBrokerCapability.UNKNOWN, result.getZstdSupported());
    }

    @Test
    public void testSuccessResultWarnsWhenRawZstdOverridesTypedConfigAndFeatureUnknown() throws Exception {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        DefaultKafkaRouteDiagnostics diagnostics = new DefaultKafkaRouteDiagnostics(properties);
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("diagnostic-client");
        config.getProducer().setCompressionType(SimpleKafkaRouteConstant.COMPRESSION_TYPE_GZIP);
        config.getProducer().getProperties().put(SimpleKafkaRouteConstant.PROPERTY_COMPRESSION_TYPE,
                SimpleKafkaRouteConstant.COMPRESSION_TYPE_ZSTD);

        KafkaRouteBrokerDiagnosticResult result = invokeSuccessResult(diagnostics, "diagnostic",
                null, config);
        log.info("raw properties zstd 覆盖 typed 配置时诊断结果: {}", result);

        assertEquals(KafkaRouteDiagnosticStatus.WARN, result.getStatus());
        assertEquals(KafkaRouteBrokerCapability.UNKNOWN, result.getZstdSupported());
    }

    @Test
    public void testSuccessResultUsesDatasourceRawPropertiesForCapabilityWarning() throws Exception {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        DefaultKafkaRouteDiagnostics diagnostics = new DefaultKafkaRouteDiagnostics(properties);
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("diagnostic-client");
        config.getProperties().put(SimpleKafkaRouteConstant.PROPERTY_COMPRESSION_TYPE,
                SimpleKafkaRouteConstant.COMPRESSION_TYPE_ZSTD);

        KafkaRouteBrokerDiagnosticResult result = invokeSuccessResult(diagnostics, "diagnostic",
                null, config);
        log.info("datasource raw properties zstd 能力未知时诊断结果: {}", result);

        assertEquals(KafkaRouteDiagnosticStatus.WARN, result.getStatus());
        assertEquals(KafkaRouteBrokerCapability.UNKNOWN, result.getZstdSupported());
    }

    @Test
    public void testSuccessResultWarnsWhenDatasourceRawIdempotenceRequiredAndFeatureUnknown() throws Exception {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        DefaultKafkaRouteDiagnostics diagnostics = new DefaultKafkaRouteDiagnostics(properties);
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("diagnostic-client");
        config.getProperties().put(SimpleKafkaRouteConstant.PROPERTY_ENABLE_IDEMPOTENCE,
                SimpleKafkaRouteConstant.BOOLEAN_TRUE);

        KafkaRouteBrokerDiagnosticResult result = invokeSuccessResult(diagnostics, "diagnostic",
                null, config);
        log.info("datasource raw properties 幂等能力未知时诊断结果: {}", result);

        assertEquals(KafkaRouteDiagnosticStatus.WARN, result.getStatus());
        assertEquals(KafkaRouteBrokerCapability.UNKNOWN, result.getIdempotenceSupported());
    }

    @Test
    public void testSuccessResultDoesNotWarnWhenRawPropertiesDisableTypedIdempotence() throws Exception {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        DefaultKafkaRouteDiagnostics diagnostics = new DefaultKafkaRouteDiagnostics(properties);
        SimpleKafkaRouteProperties.DataSourceConfig config = KafkaRouteTestDataHelper.source("diagnostic-client");
        config.getProducer().setEnableIdempotence(Boolean.TRUE);
        config.getProducer().getProperties().put(SimpleKafkaRouteConstant.PROPERTY_ENABLE_IDEMPOTENCE,
                SimpleKafkaRouteConstant.BOOLEAN_FALSE);

        KafkaRouteBrokerDiagnosticResult result = invokeSuccessResult(diagnostics, "diagnostic",
                null, config);
        log.info("raw properties 关闭 typed 幂等配置时诊断结果: {}", result);

        assertEquals(KafkaRouteDiagnosticStatus.SUCCESS, result.getStatus());
        assertEquals(KafkaRouteBrokerCapability.UNKNOWN, result.getIdempotenceSupported());
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

    private KafkaRouteBrokerDiagnosticResult invokeSuccessResult(DefaultKafkaRouteDiagnostics diagnostics,
                                                                 String datasourceKey,
                                                                 Object features,
                                                                 SimpleKafkaRouteProperties.DataSourceConfig config) throws Exception {
        Method method = DefaultKafkaRouteDiagnostics.class.getDeclaredMethod("successResult",
                String.class, Map.class, Object.class, SimpleKafkaRouteProperties.DataSourceConfig.class);
        method.setAccessible(true);
        return (KafkaRouteBrokerDiagnosticResult) method.invoke(diagnostics, datasourceKey, clusterDesc(), features, config);
    }

    private Map<String, Object> clusterDesc() {
        Map<String, Object> clusterDesc = new LinkedHashMap<>();
        clusterDesc.put(KafkaAdminCompatibilityHelper.CLUSTER_ID, "mock-cluster");
        clusterDesc.put(KafkaAdminCompatibilityHelper.NODE_COUNT, 1);
        clusterDesc.put(KafkaAdminCompatibilityHelper.CONTROLLER_VISIBLE, Boolean.TRUE);
        return clusterDesc;
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
