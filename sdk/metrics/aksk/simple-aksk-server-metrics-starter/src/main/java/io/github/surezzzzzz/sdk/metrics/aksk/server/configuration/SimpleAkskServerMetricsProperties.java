package io.github.surezzzzzz.sdk.metrics.aksk.server.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AKSK Server Metrics Properties
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = "io.github.surezzzzzz.sdk.metrics.aksk.server")
public class SimpleAkskServerMetricsProperties {

    /**
     * 是否启用指标采集，默认开启
     */
    private boolean enable = true;
}
