package io.github.surezzzzzz.sdk.auth.aksk.openapi.resttemplate.client.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.constant.SimpleAkskOpenApiClientConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AKSK 管理 OpenAPI RestTemplate 配置
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimpleAkskOpenApiClientConstant.CONFIG_PREFIX)
public class SimpleAkskOpenApiRestTemplateProperties {

    /**
     * AKP Profile；空或默认值使用底层默认 AKP
     */
    private String akskProfile;
}
