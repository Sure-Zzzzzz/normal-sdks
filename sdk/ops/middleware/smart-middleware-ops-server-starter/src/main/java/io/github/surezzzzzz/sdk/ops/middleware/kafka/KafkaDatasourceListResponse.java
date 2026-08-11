package io.github.surezzzzzz.sdk.ops.middleware.kafka;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Kafka 数据源清单响应。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class KafkaDatasourceListResponse {

    /**
     * 数据源诊断安全投影。
     */
    private final List<KafkaDatasourceResponse> items;
}
