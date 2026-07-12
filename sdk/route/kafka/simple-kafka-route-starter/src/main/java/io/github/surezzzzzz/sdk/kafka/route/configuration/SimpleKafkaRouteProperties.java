package io.github.surezzzzzz.sdk.kafka.route.configuration;

import io.github.surezzzzzz.sdk.kafka.route.constant.RouteMatchType;
import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple Kafka Route 配置
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimpleKafkaRouteConstant.CONFIG_PREFIX)
public class SimpleKafkaRouteProperties {

    /**
     * 是否启用路由
     */
    private boolean enable = false;

    /**
     * 默认数据源 key
     */
    private String defaultSource = SimpleKafkaRouteConstant.DEFAULT_DATASOURCE_KEY;

    /**
     * 数据源配置
     */
    private Map<String, DataSourceConfig> sources = new HashMap<>();

    /**
     * 路由规则
     */
    private List<RouteRule> rules = new ArrayList<>();

    /**
     * Broker 诊断配置
     */
    private DiagnosticsConfig diagnostics = new DiagnosticsConfig();

    /**
     * Kafka broker 诊断配置
     */
    @Data
    public static class DiagnosticsConfig {

        /**
         * 是否启用诊断能力
         */
        private boolean enable = SimpleKafkaRouteConstant.DEFAULT_DIAGNOSTICS_ENABLE;

        /**
         * 是否在核心 Bean 就绪后执行启动探测
         */
        private boolean startupCheck = SimpleKafkaRouteConstant.DEFAULT_DIAGNOSTICS_STARTUP_CHECK;

        /**
         * broker 不可达时是否阻断启动
         */
        private boolean failFast = SimpleKafkaRouteConstant.DEFAULT_DIAGNOSTICS_FAIL_FAST;

        /**
         * 单 datasource 探测超时时间（毫秒）
         */
        private long timeoutMs = SimpleKafkaRouteConstant.DEFAULT_DIAGNOSTICS_TIMEOUT_MS;

        /**
         * 探测成功时是否打印摘要日志
         */
        private boolean logSummary = SimpleKafkaRouteConstant.DEFAULT_DIAGNOSTICS_LOG_SUMMARY;
    }

    /**
     * Kafka 数据源配置
     */
    @Getter
    @Setter
    @ToString(exclude = {"security", "properties"})
    public static class DataSourceConfig {

        /**
         * Kafka bootstrap servers
         */
        private List<String> bootstrapServers = new ArrayList<>();

        /**
         * datasource 级默认 client id
         */
        private String clientId;

        /**
         * datasource 级公共 Kafka properties
         */
        private Map<String, String> properties = new HashMap<>();

        /**
         * 安全配置
         */
        private SecurityConfig security = new SecurityConfig();

        /**
         * producer 配置
         */
        private ProducerConfig producer = new ProducerConfig();

        /**
         * consumer 配置
         */
        private ConsumerConfig consumer = new ConsumerConfig();
    }

    /**
     * Kafka 安全配置
     */
    @Getter
    @Setter
    @ToString(exclude = {
            "saslJaasConfig",
            "sslTrustStorePassword",
            "sslKeyStorePassword",
            "sslKeyPassword"
    })
    public static class SecurityConfig {

        private String securityProtocol;
        private String saslMechanism;
        private String saslJaasConfig;
        private String sslTrustStoreLocation;
        private String sslTrustStorePassword;
        private String sslKeyStoreLocation;
        private String sslKeyStorePassword;
        private String sslKeyPassword;
    }

    /**
     * Kafka producer 配置
     */
    @Getter
    @Setter
    @ToString(exclude = "properties")
    public static class ProducerConfig {

        private String clientId;
        private String keySerializer = SimpleKafkaRouteConstant.DEFAULT_KEY_SERIALIZER;
        private String valueSerializer = SimpleKafkaRouteConstant.DEFAULT_VALUE_SERIALIZER;
        private String acks;
        private Integer retries;
        private Integer batchSize;
        private Long lingerMs;
        private Long bufferMemory;
        private String compressionType;
        private Boolean enableIdempotence;
        private Integer requestTimeoutMs;
        private Integer deliveryTimeoutMs;
        private String transactionIdPrefix;
        private Map<String, String> properties = new HashMap<>();
    }

    /**
     * Kafka consumer 配置
     */
    @Getter
    @Setter
    @ToString(exclude = "properties")
    public static class ConsumerConfig {

        private String clientId;
        private String keyDeserializer = SimpleKafkaRouteConstant.DEFAULT_KEY_DESERIALIZER;
        private String valueDeserializer = SimpleKafkaRouteConstant.DEFAULT_VALUE_DESERIALIZER;
        private String groupId;
        private String autoOffsetReset;
        private Boolean enableAutoCommit;
        private Integer maxPollRecords;
        private Map<String, String> properties = new HashMap<>();
    }

    /**
     * 路由规则配置
     */
    @Data
    public static class RouteRule {

        /**
         * 匹配表达式
         */
        private String pattern;

        /**
         * 匹配类型
         */
        private String type = RouteMatchType.EXACT.getCode();

        /**
         * 目标数据源
         */
        private String datasource;

        /**
         * 优先级，数字越小越优先
         */
        private int priority = SimpleKafkaRouteConstant.DEFAULT_RULE_PRIORITY;

        /**
         * 是否启用
         */
        private boolean enable = true;
    }
}
