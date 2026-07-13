package io.github.surezzzzzz.sdk.kafka.route.diagnostic;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import io.github.surezzzzzz.sdk.kafka.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteBrokerCapability;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteBrokerDiagnosticResult;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteDiagnosticStatus;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaAdminCompatibilityHelper;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaRoutePropertyMerger;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaRouteSensitiveLogHelper;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaRouteStringHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 默认 Kafka route 诊断实现
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultKafkaRouteDiagnostics implements KafkaRouteDiagnostics, SmartInitializingSingleton {

    private final SimpleKafkaRouteProperties properties;
    private final Map<String, KafkaRouteBrokerDiagnosticResult> diagnosticResults = new ConcurrentHashMap<>();
    private volatile boolean completed = false;

    @Override
    public void afterSingletonsInstantiated() {
        SimpleKafkaRouteProperties.DiagnosticsConfig diagnostics = diagnosticsConfig();
        if (!diagnostics.isEnable() || !diagnostics.isStartupCheck()) {
            completed = true;
            return;
        }
        if (properties.getSources() == null || properties.getSources().isEmpty()) {
            completed = true;
            return;
        }
        runDiagnostics(diagnostics);
    }

    @Override
    public Map<String, KafkaRouteBrokerDiagnosticResult> getDiagnosticResults() {
        if (!completed) {
            return Collections.emptyMap();
        }
        Map<String, KafkaRouteBrokerDiagnosticResult> orderedResults = new LinkedHashMap<>();
        if (properties.getSources() != null) {
            for (String datasourceKey : properties.getSources().keySet()) {
                KafkaRouteBrokerDiagnosticResult result = diagnosticResults.get(datasourceKey);
                if (result != null) {
                    orderedResults.put(datasourceKey, result);
                }
            }
        }
        return Collections.unmodifiableMap(orderedResults);
    }

    @Override
    public KafkaRouteBrokerDiagnosticResult getDiagnosticResult(String datasourceKey) {
        if (!completed) {
            return null;
        }
        return diagnosticResults.get(datasourceKey);
    }

    private void runDiagnostics(SimpleKafkaRouteProperties.DiagnosticsConfig diagnostics) {
        int threadCount = Math.min(properties.getSources().size(), SimpleKafkaRouteConstant.MAX_DIAGNOSTIC_THREAD_COUNT);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount, new DiagnosticThreadFactory());
        for (Map.Entry<String, SimpleKafkaRouteProperties.DataSourceConfig> entry : properties.getSources().entrySet()) {
            final String datasourceKey = entry.getKey();
            final SimpleKafkaRouteProperties.DataSourceConfig dataSourceConfig = entry.getValue();
            executorService.submit(() -> diagnosticResults.put(datasourceKey,
                    probeDatasource(datasourceKey, dataSourceConfig, diagnostics)));
        }
        executorService.shutdown();
        try {
            boolean terminated = executorService.awaitTermination(diagnostics.getTimeoutMs() + 1000L, TimeUnit.MILLISECONDS);
            if (!terminated) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
        completed = true;
        failFastIfNecessary(diagnostics);
    }

    private KafkaRouteBrokerDiagnosticResult probeDatasource(String datasourceKey,
                                                             SimpleKafkaRouteProperties.DataSourceConfig config,
                                                             SimpleKafkaRouteProperties.DiagnosticsConfig diagnostics) {
        Object adminClient = null;
        try {
            Map<String, Object> adminConfig = createAdminConfig(datasourceKey, config, diagnostics);
            log.debug("Kafka route datasource [{}] 诊断配置: {}", datasourceKey,
                    KafkaRouteSensitiveLogHelper.maskSensitiveKeys(adminConfig));
            adminClient = KafkaAdminCompatibilityHelper.createAdminClient(adminConfig);
            Map<String, Object> clusterDesc = KafkaAdminCompatibilityHelper.describeCluster(adminClient, diagnostics.getTimeoutMs());
            if (clusterDesc.isEmpty()) {
                throw new IllegalStateException(ErrorMessage.DIAGNOSTICS_DESCRIBE_CLUSTER_EMPTY);
            }
            Object features = KafkaAdminCompatibilityHelper.describeFeaturesIfAvailable(adminClient, diagnostics.getTimeoutMs());
            KafkaRouteBrokerDiagnosticResult result = successResult(datasourceKey, clusterDesc, features, config);
            logDiagnosticSummary(result, diagnostics);
            return result;
        } catch (RuntimeException e) {
            KafkaRouteBrokerDiagnosticResult result = failedResult(datasourceKey, e);
            log.warn("Kafka route datasource [{}] 诊断失败，bootstrapServerCount=[{}]，exception=[{}]，message=[{}]",
                    datasourceKey,
                    config.getBootstrapServers() == null ? 0 : config.getBootstrapServers().size(),
                    e.getClass().getSimpleName(), safeMessage(e));
            return result;
        } finally {
            KafkaAdminCompatibilityHelper.closeAdminClient(adminClient);
        }
    }

    private Map<String, Object> createAdminConfig(String datasourceKey,
                                                  SimpleKafkaRouteProperties.DataSourceConfig config,
                                                  SimpleKafkaRouteProperties.DiagnosticsConfig diagnostics) {
        Map<String, Object> adminConfig = new LinkedHashMap<>(
                KafkaRoutePropertyMerger.mergeBaseProperties(datasourceKey, config));
        adminConfig.put(SimpleKafkaRouteConstant.PROPERTY_BOOTSTRAP_SERVERS, config.getBootstrapServers());
        adminConfig.put(SimpleKafkaRouteConstant.PROPERTY_CLIENT_ID,
                String.format(SimpleKafkaRouteConstant.DIAGNOSTIC_CLIENT_ID_TEMPLATE, datasourceKey));
        adminConfig.put(SimpleKafkaRouteConstant.PROPERTY_REQUEST_TIMEOUT_MS, Long.valueOf(diagnostics.getTimeoutMs()).intValue());
        adminConfig.put(SimpleKafkaRouteConstant.PROPERTY_DEFAULT_API_TIMEOUT_MS, Long.valueOf(diagnostics.getTimeoutMs()).intValue());
        return adminConfig;
    }

    private KafkaRouteBrokerDiagnosticResult successResult(String datasourceKey, Map<String, Object> clusterDesc,
                                                           Object features,
                                                           SimpleKafkaRouteProperties.DataSourceConfig config) {
        KafkaRouteBrokerCapability adminApiLevel = KafkaRouteBrokerCapability.SUPPORTED;
        KafkaRouteBrokerCapability capabilityInferredFromFeatureApi = features == null
                ? KafkaRouteBrokerCapability.UNKNOWN : KafkaRouteBrokerCapability.SUPPORTED;
        KafkaRouteBrokerCapability idempotenceSupported = capabilityInferredFromFeatureApi;
        KafkaRouteBrokerCapability zstdSupported = capabilityInferredFromFeatureApi;
        KafkaRouteBrokerCapability transactionSupported = capabilityInferredFromFeatureApi;
        KafkaRouteDiagnosticStatus status = hasCapabilityWarning(config, transactionSupported,
                idempotenceSupported, zstdSupported)
                ? KafkaRouteDiagnosticStatus.WARN : KafkaRouteDiagnosticStatus.SUCCESS;
        return KafkaRouteBrokerDiagnosticResult.builder()
                .datasourceKey(datasourceKey)
                .status(status)
                .clusterId(KafkaAdminCompatibilityHelper.extractClusterId(clusterDesc))
                .nodeCount(KafkaAdminCompatibilityHelper.extractNodeCount(clusterDesc))
                .controllerVisible(KafkaAdminCompatibilityHelper.extractControllerVisible(clusterDesc))
                .transactionSupported(transactionSupported)
                .idempotenceSupported(idempotenceSupported)
                .zstdSupported(zstdSupported)
                .adminApiLevel(adminApiLevel)
                .build();
    }

    private boolean hasCapabilityWarning(SimpleKafkaRouteProperties.DataSourceConfig config,
                                         KafkaRouteBrokerCapability transactionSupported,
                                         KafkaRouteBrokerCapability idempotenceSupported,
                                         KafkaRouteBrokerCapability zstdSupported) {
        if (config == null) {
            return false;
        }
        SimpleKafkaRouteProperties.ProducerConfig producer = config.getProducer() == null
                ? new SimpleKafkaRouteProperties.ProducerConfig() : config.getProducer();
        if (KafkaRouteStringHelper.hasText(producer.getTransactionIdPrefix())
                && transactionSupported != KafkaRouteBrokerCapability.SUPPORTED) {
            return true;
        }
        if (isEffectiveIdempotenceEnabled(config, producer)
                && idempotenceSupported != KafkaRouteBrokerCapability.SUPPORTED) {
            return true;
        }
        return isEffectiveZstdCompression(config, producer)
                && zstdSupported != KafkaRouteBrokerCapability.SUPPORTED;
    }

    private boolean isEffectiveIdempotenceEnabled(SimpleKafkaRouteProperties.DataSourceConfig config,
                                                  SimpleKafkaRouteProperties.ProducerConfig producer) {
        Object value = getEffectiveProducerProperty(config, producer,
                SimpleKafkaRouteConstant.PROPERTY_ENABLE_IDEMPOTENCE);
        if (value instanceof Boolean) {
            return Boolean.TRUE.equals(value);
        }
        return value != null && SimpleKafkaRouteConstant.BOOLEAN_TRUE.equalsIgnoreCase(String.valueOf(value).trim());
    }

    private boolean isEffectiveZstdCompression(SimpleKafkaRouteProperties.DataSourceConfig config,
                                               SimpleKafkaRouteProperties.ProducerConfig producer) {
        Object value = getEffectiveProducerProperty(config, producer,
                SimpleKafkaRouteConstant.PROPERTY_COMPRESSION_TYPE);
        return value != null && SimpleKafkaRouteConstant.COMPRESSION_TYPE_ZSTD.equalsIgnoreCase(String.valueOf(value).trim());
    }

    private Object getEffectiveProducerProperty(SimpleKafkaRouteProperties.DataSourceConfig config,
                                                SimpleKafkaRouteProperties.ProducerConfig producer,
                                                String key) {
        Object value = hasRawProperty(config.getProperties(), key) ? rawProperty(config.getProperties(), key) : null;
        Object producerTypedValue = producerTypedProperty(producer, key);
        if (producerTypedValue != null) {
            value = producerTypedValue;
        }
        return hasRawProperty(producer.getProperties(), key) ? rawProperty(producer.getProperties(), key) : value;
    }

    private Object producerTypedProperty(SimpleKafkaRouteProperties.ProducerConfig producer, String key) {
        if (SimpleKafkaRouteConstant.PROPERTY_COMPRESSION_TYPE.equals(key)) {
            return KafkaRouteStringHelper.hasText(producer.getCompressionType())
                    ? producer.getCompressionType().trim().toLowerCase() : null;
        }
        if (SimpleKafkaRouteConstant.PROPERTY_ENABLE_IDEMPOTENCE.equals(key)) {
            return producer.getEnableIdempotence();
        }
        return null;
    }

    private boolean hasRawProperty(Map<String, String> properties, String key) {
        return rawPropertyEntry(properties, key) != null;
    }

    private Object rawProperty(Map<String, String> properties, String key) {
        Map.Entry<String, String> entry = rawPropertyEntry(properties, key);
        return entry == null ? null : entry.getValue();
    }

    private Map.Entry<String, String> rawPropertyEntry(Map<String, String> properties, String key) {
        if (properties == null || properties.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (KafkaRouteStringHelper.hasText(entry.getKey())
                    && key.equals(entry.getKey().trim())) {
                return entry;
            }
        }
        return null;
    }

    private KafkaRouteBrokerDiagnosticResult failedResult(String datasourceKey, RuntimeException e) {
        return KafkaRouteBrokerDiagnosticResult.builder()
                .datasourceKey(datasourceKey)
                .status(KafkaRouteDiagnosticStatus.FAILED)
                .nodeCount(-1)
                .controllerVisible(false)
                .transactionSupported(KafkaRouteBrokerCapability.UNKNOWN)
                .idempotenceSupported(KafkaRouteBrokerCapability.UNKNOWN)
                .zstdSupported(KafkaRouteBrokerCapability.UNKNOWN)
                .adminApiLevel(KafkaRouteBrokerCapability.UNKNOWN)
                .failureReason(safeMessage(e))
                .build();
    }

    private void logDiagnosticSummary(KafkaRouteBrokerDiagnosticResult result,
                                      SimpleKafkaRouteProperties.DiagnosticsConfig diagnostics) {
        if (!diagnostics.isLogSummary()) {
            return;
        }
        if (result.getStatus() == KafkaRouteDiagnosticStatus.WARN) {
            log.warn("Kafka route datasource [{}] 诊断完成但存在 capability 告警，clusterId=[{}]，nodeCount=[{}]，controllerVisible=[{}]，transactionSupported=[{}]，idempotenceSupported=[{}]，zstdSupported=[{}]，adminApiLevel=[{}]",
                    result.getDatasourceKey(), result.getClusterId(), result.getNodeCount(), result.isControllerVisible(),
                    result.getTransactionSupported(), result.getIdempotenceSupported(), result.getZstdSupported(), result.getAdminApiLevel());
            return;
        }
        log.info("Kafka route datasource [{}] 诊断完成，clusterId=[{}]，nodeCount=[{}]，controllerVisible=[{}]，transactionSupported=[{}]，idempotenceSupported=[{}]，zstdSupported=[{}]，adminApiLevel=[{}]",
                result.getDatasourceKey(), result.getClusterId(), result.getNodeCount(), result.isControllerVisible(),
                result.getTransactionSupported(), result.getIdempotenceSupported(), result.getZstdSupported(), result.getAdminApiLevel());
    }

    private void failFastIfNecessary(SimpleKafkaRouteProperties.DiagnosticsConfig diagnostics) {
        if (!diagnostics.isFailFast()) {
            return;
        }
        List<String> failedDatasourceKeys = new ArrayList<>();
        for (Map.Entry<String, KafkaRouteBrokerDiagnosticResult> entry : diagnosticResults.entrySet()) {
            if (entry.getValue().getStatus() == KafkaRouteDiagnosticStatus.FAILED) {
                failedDatasourceKeys.add(entry.getKey());
            }
        }
        if (!failedDatasourceKeys.isEmpty()) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_013,
                    String.format(ErrorMessage.DIAGNOSTICS_FAIL_FAST, failedDatasourceKeys));
        }
    }

    private SimpleKafkaRouteProperties.DiagnosticsConfig diagnosticsConfig() {
        return properties.getDiagnostics() == null
                ? new SimpleKafkaRouteProperties.DiagnosticsConfig() : properties.getDiagnostics();
    }

    private String safeMessage(RuntimeException e) {
        String message = e.getMessage();
        if (!KafkaRouteStringHelper.hasText(message)) {
            return e.getClass().getSimpleName();
        }
        if (containsSensitiveFragment(message)) {
            return SimpleKafkaRouteConstant.MASKED_VALUE;
        }
        return message;
    }

    private boolean containsSensitiveFragment(String message) {
        String lowerMessage = message.toLowerCase();
        for (String fragment : SimpleKafkaRouteConstant.SENSITIVE_KEY_FRAGMENTS) {
            if (lowerMessage.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static class DiagnosticThreadFactory implements ThreadFactory {

        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable,
                    SimpleKafkaRouteConstant.DIAGNOSTIC_THREAD_NAME_PREFIX + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
