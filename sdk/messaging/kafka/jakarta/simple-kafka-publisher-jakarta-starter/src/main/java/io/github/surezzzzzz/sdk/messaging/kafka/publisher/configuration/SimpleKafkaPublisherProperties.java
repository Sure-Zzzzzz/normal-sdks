package io.github.surezzzzzz.sdk.messaging.kafka.publisher.configuration;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.SimpleKafkaPublisherConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple Kafka Publisher 配置属性
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimpleKafkaPublisherConstant.CONFIG_PREFIX)
public class SimpleKafkaPublisherProperties {

    /**
     * 是否启用 publisher
     */
    private boolean enable = SimpleKafkaPublisherConstant.DEFAULT_ENABLE;

    /**
     * 应用名称
     */
    private String appName = SimpleKafkaPublisherConstant.DEFAULT_APP_NAME;

    /**
     * 默认 topic
     */
    private String defaultTopic;

    /**
     * envelope 配置
     */
    private EnvelopeConfig envelope = new EnvelopeConfig();

    /**
     * header 配置
     */
    private HeaderConfig headers = new HeaderConfig();

    /**
     * 发送配置
     */
    private SendConfig send = new SendConfig();

    /**
     * envelope 配置
     */
    @Data
    public static class EnvelopeConfig {

        /**
         * 是否启用 envelope
         */
        private boolean enable = SimpleKafkaPublisherConstant.DEFAULT_ENVELOPE_ENABLE;

        /**
         * 是否允许 null payload
         */
        private boolean includeNullPayload = SimpleKafkaPublisherConstant.DEFAULT_INCLUDE_NULL_PAYLOAD;
    }

    /**
     * header 配置
     */
    @Data
    public static class HeaderConfig {

        /**
         * 是否启用默认 header
         */
        private boolean enableDefaultHeaders = SimpleKafkaPublisherConstant.DEFAULT_ENABLE_DEFAULT_HEADERS;

        /**
         * 是否允许覆盖默认 header
         */
        private boolean allowHeaderOverride = SimpleKafkaPublisherConstant.DEFAULT_ALLOW_HEADER_OVERRIDE;

        /**
         * traceId header 名称
         */
        private String traceIdHeader = SimpleKafkaPublisherConstant.DEFAULT_HEADER_TRACE_ID;

        /**
         * messageId header 名称
         */
        private String messageIdHeader = SimpleKafkaPublisherConstant.DEFAULT_HEADER_MESSAGE_ID;

        /**
         * messageType header 名称
         */
        private String messageTypeHeader = SimpleKafkaPublisherConstant.DEFAULT_HEADER_MESSAGE_TYPE;

        /**
         * source header 名称
         */
        private String sourceHeader = SimpleKafkaPublisherConstant.DEFAULT_HEADER_SOURCE;

        /**
         * publishedAt header 名称
         */
        private String publishedAtHeader = SimpleKafkaPublisherConstant.DEFAULT_HEADER_PUBLISHED_AT;
    }

    /**
     * 发送配置
     */
    @Data
    public static class SendConfig {

        /**
         * 同步等待超时时间
         */
        private long timeoutMs = SimpleKafkaPublisherConstant.DEFAULT_SEND_TIMEOUT_MS;
    }
}
