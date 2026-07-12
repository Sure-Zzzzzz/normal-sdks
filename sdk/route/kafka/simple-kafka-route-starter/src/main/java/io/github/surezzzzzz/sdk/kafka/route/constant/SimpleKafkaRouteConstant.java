package io.github.surezzzzzz.sdk.kafka.route.constant;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Simple Kafka Route 常量
 *
 * @author surezzzzzz
 */
public final class SimpleKafkaRouteConstant {

    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.kafka.route";
    public static final String CONFIG_PREFIX_DIAGNOSTICS = CONFIG_PREFIX + ".diagnostics";

    public static final String DEFAULT_DATASOURCE_KEY = "default";
    public static final int DEFAULT_RULE_PRIORITY = 1000;

    public static final String DEFAULT_KEY_SERIALIZER = "org.apache.kafka.common.serialization.StringSerializer";
    public static final String DEFAULT_VALUE_SERIALIZER = "org.apache.kafka.common.serialization.StringSerializer";
    public static final String DEFAULT_KEY_DESERIALIZER = "org.apache.kafka.common.serialization.StringDeserializer";
    public static final String DEFAULT_VALUE_DESERIALIZER = "org.apache.kafka.common.serialization.StringDeserializer";

    public static final boolean DEFAULT_DIAGNOSTICS_ENABLE = true;
    public static final boolean DEFAULT_DIAGNOSTICS_STARTUP_CHECK = true;
    public static final boolean DEFAULT_DIAGNOSTICS_FAIL_FAST = false;
    public static final long DEFAULT_DIAGNOSTICS_TIMEOUT_MS = 3000L;
    public static final boolean DEFAULT_DIAGNOSTICS_LOG_SUMMARY = true;
    public static final int MAX_DIAGNOSTIC_THREAD_COUNT = 8;
    public static final String DIAGNOSTIC_CLIENT_ID_TEMPLATE = "kafka-route-diagnostic-%s";
    public static final String DIAGNOSTIC_THREAD_NAME_PREFIX = "kafka-route-diagnostic-";

    /**
     * 兼容层反射方法名常量，集中管理跨 Spring Kafka 版本的 API 名称
     */
    public static final String REFLECT_METHOD_CLOSE = "close";
    public static final String REFLECT_METHOD_DESTROY = "destroy";
    public static final String REFLECT_METHOD_GET = "get";
    public static final String REFLECT_METHOD_IS_TRANSACTIONAL = "isTransactional";
    public static final String REFLECT_METHOD_GET_TRANSACTION_ID_PREFIX = "getTransactionIdPrefix";
    public static final String REFLECT_METHOD_SET_TRANSACTION_ID_PREFIX = "setTransactionIdPrefix";
    public static final String REFLECT_METHOD_GET_CONFIGURATION_PROPERTIES = "getConfigurationProperties";
    public static final String REFLECT_METHOD_DESCRIBE_CLUSTER = "describeCluster";
    public static final String REFLECT_METHOD_DESCRIBE_FEATURES = "describeFeatures";
    public static final String REFLECT_METHOD_FEATURE_METADATA = "featureMetadata";
    public static final String REFLECT_METHOD_CLUSTER_ID = "clusterId";
    public static final String REFLECT_METHOD_NODES = "nodes";
    public static final String REFLECT_METHOD_CONTROLLER = "controller";

    public static final int MIN_PORT = 1;
    public static final int MAX_PORT = 65535;

    public static final String PROPERTY_BOOTSTRAP_SERVERS = "bootstrap.servers";
    public static final String PROPERTY_CLIENT_ID = "client.id";
    public static final String PROPERTY_KEY_SERIALIZER = "key.serializer";
    public static final String PROPERTY_VALUE_SERIALIZER = "value.serializer";
    public static final String PROPERTY_KEY_DESERIALIZER = "key.deserializer";
    public static final String PROPERTY_VALUE_DESERIALIZER = "value.deserializer";
    public static final String PROPERTY_ACKS = "acks";
    public static final String PROPERTY_RETRIES = "retries";
    public static final String PROPERTY_BATCH_SIZE = "batch.size";
    public static final String PROPERTY_LINGER_MS = "linger.ms";
    public static final String PROPERTY_BUFFER_MEMORY = "buffer.memory";
    public static final String PROPERTY_COMPRESSION_TYPE = "compression.type";
    public static final String PROPERTY_ENABLE_IDEMPOTENCE = "enable.idempotence";
    public static final String PROPERTY_REQUEST_TIMEOUT_MS = "request.timeout.ms";
    public static final String PROPERTY_DELIVERY_TIMEOUT_MS = "delivery.timeout.ms";
    public static final String PROPERTY_DEFAULT_API_TIMEOUT_MS = "default.api.timeout.ms";
    public static final String PROPERTY_GROUP_ID = "group.id";
    public static final String PROPERTY_AUTO_OFFSET_RESET = "auto.offset.reset";
    public static final String PROPERTY_ENABLE_AUTO_COMMIT = "enable.auto.commit";
    public static final String PROPERTY_MAX_POLL_RECORDS = "max.poll.records";
    public static final String PROPERTY_TRANSACTIONAL_ID = "transactional.id";

    public static final String PROPERTY_SECURITY_PROTOCOL = "security.protocol";
    public static final String PROPERTY_SASL_MECHANISM = "sasl.mechanism";
    public static final String PROPERTY_SASL_JAAS_CONFIG = "sasl.jaas.config";
    public static final String PROPERTY_SSL_TRUSTSTORE_LOCATION = "ssl.truststore.location";
    public static final String PROPERTY_SSL_TRUSTSTORE_PASSWORD = "ssl.truststore.password";
    public static final String PROPERTY_SSL_KEYSTORE_LOCATION = "ssl.keystore.location";
    public static final String PROPERTY_SSL_KEYSTORE_PASSWORD = "ssl.keystore.password";
    public static final String PROPERTY_SSL_KEY_PASSWORD = "ssl.key.password";

    public static final String MASKED_VALUE = "[MASKED]";
    public static final String SENSITIVE_KEY_FRAGMENT_PASSWORD = "password";
    public static final String SENSITIVE_KEY_FRAGMENT_CREDENTIAL = "credential";
    public static final String SENSITIVE_KEY_FRAGMENT_SASL_JAAS_CONFIG = "sasl.jaas.config";
    public static final String SENSITIVE_KEY_FRAGMENT_JAAS_CONFIG = "jaas.config";
    public static final String SENSITIVE_KEY_FRAGMENT_SSL_KEY = "ssl.key";
    public static final String SENSITIVE_KEY_FRAGMENT_SECRET = "secret";

    public static final Set<String> SENSITIVE_KEY_FRAGMENTS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            SENSITIVE_KEY_FRAGMENT_PASSWORD,
            SENSITIVE_KEY_FRAGMENT_CREDENTIAL,
            SENSITIVE_KEY_FRAGMENT_SASL_JAAS_CONFIG,
            SENSITIVE_KEY_FRAGMENT_JAAS_CONFIG,
            SENSITIVE_KEY_FRAGMENT_SSL_KEY,
            SENSITIVE_KEY_FRAGMENT_SECRET
    )));

    public static final Set<String> RESERVED_PROPERTY_KEYS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            PROPERTY_BOOTSTRAP_SERVERS,
            PROPERTY_CLIENT_ID,
            PROPERTY_KEY_SERIALIZER,
            PROPERTY_VALUE_SERIALIZER,
            PROPERTY_KEY_DESERIALIZER,
            PROPERTY_VALUE_DESERIALIZER,
            PROPERTY_TRANSACTIONAL_ID,
            PROPERTY_GROUP_ID,
            PROPERTY_SECURITY_PROTOCOL,
            PROPERTY_SASL_MECHANISM,
            PROPERTY_SASL_JAAS_CONFIG,
            PROPERTY_SSL_TRUSTSTORE_LOCATION,
            PROPERTY_SSL_TRUSTSTORE_PASSWORD,
            PROPERTY_SSL_KEYSTORE_LOCATION,
            PROPERTY_SSL_KEYSTORE_PASSWORD,
            PROPERTY_SSL_KEY_PASSWORD
    )));

    public static final Set<String> VALID_ACKS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "0", "1", "all", "-1"
    )));

    public static final Set<String> VALID_COMPRESSION_TYPES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "none", "gzip", "snappy", "lz4", "zstd"
    )));

    public static final Set<String> VALID_SECURITY_PROTOCOLS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "PLAINTEXT", "SSL", "SASL_PLAINTEXT", "SASL_SSL"
    )));

    public static final Set<String> VALID_AUTO_OFFSET_RESET = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "earliest", "latest", "none"
    )));

    private SimpleKafkaRouteConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}
