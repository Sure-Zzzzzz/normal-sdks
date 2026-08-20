package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.summary;

import lombok.Builder;
import lombok.Getter;

/**
 * Elasticsearch 集群安全摘要。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class ElasticsearchSummaryResponse {

    /**
     * 数据源标识。
     */
    private final String datasourceKey;
    /**
     * 配置版本。
     */
    private final String configuredVersion;
    /**
     * 探测版本。
     */
    private final String detectedVersion;
    /**
     * 有效版本。
     */
    private final String effectiveVersion;
    /**
     * 配置与探测版本是否不一致。
     */
    private final boolean versionMismatch;
    /**
     * 是否已完成版本探测。
     */
    private final boolean detected;
}
