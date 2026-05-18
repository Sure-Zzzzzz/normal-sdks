package io.github.surezzzzzz.sdk.metrics.aksk.resource.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AKSK Resource Metrics Properties
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = "io.github.surezzzzzz.sdk.metrics.aksk.resource")
public class SimpleAkskResourceMetricsProperties {

    /**
     * 是否启用指标采集，默认开启
     */
    private boolean enable = true;
}