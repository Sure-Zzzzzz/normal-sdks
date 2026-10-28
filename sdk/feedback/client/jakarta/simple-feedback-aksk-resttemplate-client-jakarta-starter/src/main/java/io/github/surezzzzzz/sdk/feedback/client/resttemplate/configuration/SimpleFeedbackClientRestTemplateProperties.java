package io.github.surezzzzzz.sdk.feedback.client.resttemplate.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Feedback 机器客户端配置。 */
@Data
@ConfigurationProperties(prefix = "io.github.surezzzzzz.sdk.feedback.client")
public class SimpleFeedbackClientRestTemplateProperties {
    /** 是否装配默认客户端。 */
    private boolean enable = true;
    /** Feedback Server 基地址。 */
    private String baseUrl;
}
