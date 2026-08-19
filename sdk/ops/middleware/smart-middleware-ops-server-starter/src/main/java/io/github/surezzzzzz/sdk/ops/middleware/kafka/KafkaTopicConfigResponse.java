package io.github.surezzzzzz.sdk.ops.middleware.kafka;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Kafka Topic 固定白名单配置响应。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class KafkaTopicConfigResponse {

    private final String topic;
    private final List<Item> items;

    @Getter
    @Builder
    public static class Item {
        private final String name;
        private final String value;
        private final String source;
        private final Boolean readOnly;
    }
}
