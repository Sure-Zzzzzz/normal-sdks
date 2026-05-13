package io.github.surezzzzzz.sdk.metrics.limiter.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SmartRedisLimiter 限流指标配置
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = "io.github.surezzzzzz.sdk.metrics.limiter")
public class SmartRedisLimiterMetricsProperties {

    /**
     * 是否启用指标采集，默认开启
     */
    private boolean enable = true;
}
