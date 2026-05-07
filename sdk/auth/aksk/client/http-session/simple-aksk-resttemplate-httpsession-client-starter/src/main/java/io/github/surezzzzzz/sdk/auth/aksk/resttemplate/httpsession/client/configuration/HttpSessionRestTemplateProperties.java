package io.github.surezzzzzz.sdk.auth.aksk.resttemplate.httpsession.client.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.httpsession.client.constant.HttpSessionRestTemplateConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple AKSK RestTemplate HttpSession Client Properties
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = HttpSessionRestTemplateConstant.CONFIG_PREFIX)
public class HttpSessionRestTemplateProperties {

    private boolean enable = false;

    private int maxTotal = HttpSessionRestTemplateConstant.DEFAULT_MAX_TOTAL;

    private int maxPerRoute = HttpSessionRestTemplateConstant.DEFAULT_MAX_PER_ROUTE;

    private int connectTimeout = HttpSessionRestTemplateConstant.DEFAULT_CONNECT_TIMEOUT_MS;

    private int readTimeout = HttpSessionRestTemplateConstant.DEFAULT_READ_TIMEOUT_MS;
}
