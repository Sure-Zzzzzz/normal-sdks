package io.github.surezzzzzz.sdk.messaging.kafka.publisher.support;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.configuration.SimpleKafkaPublisherProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.SimpleKafkaPublisherConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.support.KafkaPublishStringHelper;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Kafka 发布 header Helper
 *
 * @author surezzzzzz
 */
public final class KafkaPublishHeaderHelper {

    private KafkaPublishHeaderHelper() {
        throw new UnsupportedOperationException(SimpleKafkaPublisherConstant.UTILITY_CLASS_MESSAGE);
    }

    /**
     * 标准化 header 名称
     *
     * @param headerName header 名称
     * @return 标准化后的 header 名称
     */
    public static String normalizeHeaderName(String headerName) {
        return KafkaPublishStringHelper.trimToNull(headerName);
    }

    /**
     * 判断是否为保留 header
     *
     * @param headerName header 名称
     * @param properties 配置属性
     * @return true 保留，false 非保留
     */
    public static boolean isReservedHeader(String headerName, SimpleKafkaPublisherProperties properties) {
        if (!properties.getHeaders().isEnableDefaultHeaders()) {
            return false;
        }
        String normalized = normalizeForCompare(headerName);
        if (normalized == null) {
            return false;
        }
        return reservedHeaders(properties).contains(normalized);
    }

    /**
     * 获取保留 header 名称集合
     *
     * @param properties 配置属性
     * @return 保留 header 名称集合
     */
    public static Set<String> reservedHeaders(SimpleKafkaPublisherProperties properties) {
        Set<String> headers = new LinkedHashSet<>();
        SimpleKafkaPublisherProperties.HeaderConfig config = properties.getHeaders();
        if (!config.isEnableDefaultHeaders()) {
            return headers;
        }
        addNormalized(headers, config.getMessageIdHeader());
        addNormalized(headers, config.getMessageTypeHeader());
        addNormalized(headers, config.getTraceIdHeader());
        addNormalized(headers, config.getSourceHeader());
        addNormalized(headers, config.getPublishedAtHeader());
        return headers;
    }

    private static void addNormalized(Set<String> headers, String headerName) {
        String normalized = normalizeForCompare(headerName);
        if (normalized != null) {
            headers.add(normalized);
        }
    }

    private static String normalizeForCompare(String headerName) {
        String normalized = normalizeHeaderName(headerName);
        if (normalized == null) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
