package io.github.surezzzzzz.sdk.metrics.elasticsearch.search.configuration;

import io.github.surezzzzzz.sdk.metrics.elasticsearch.search.constant.SimpleElasticsearchSearchMetricsConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * simple-elasticsearch-search 指标配置
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = SimpleElasticsearchSearchMetricsConstant.CONFIG_PREFIX)
public class SimpleElasticsearchSearchMetricsProperties {

    /**
     * 是否启用指标采集，默认开启
     */
    private boolean enable = true;

    /**
     * 业务模块标识。
     * 优先取 spring.application.name，未配置时使用此字段，仍未配置则为 "unknown"。
     */
    private String me;
}
