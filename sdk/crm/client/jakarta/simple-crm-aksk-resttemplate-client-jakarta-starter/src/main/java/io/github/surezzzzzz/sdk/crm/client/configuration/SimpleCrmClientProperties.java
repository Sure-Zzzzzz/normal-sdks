package io.github.surezzzzzz.sdk.crm.client.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple CRM Client 全局配置。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = "io.github.surezzzzzz.sdk.crm.client")
public class SimpleCrmClientProperties {

    /**
     * 是否启用（默认 true，底座缺席时由条件装配退场）。
     */
    private boolean enable = true;

    /**
     * CRM Server 基地址（如 https://crm.example.com），由宿主显式配置。
     */
    private String baseUrl;
}
