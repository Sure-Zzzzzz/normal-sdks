package io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.constant.SimpleAkskRestTemplateConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple AKSK RestTemplate Redis Client Properties
 *
 * <p>RestTemplate 相关配置
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = SimpleAkskRestTemplateConstant.CONFIG_PREFIX)
public class SimpleAkskRestTemplateProperties {

    /**
     * 是否创建 akskClientRestTemplate Bean
     */
    private boolean enable = false;

    /**
     * 最大连接数
     */
    private int maxTotal = SimpleAkskRestTemplateConstant.DEFAULT_MAX_TOTAL;

    /**
     * 每个路由的最大连接数
     */
    private int maxPerRoute = SimpleAkskRestTemplateConstant.DEFAULT_MAX_PER_ROUTE;

    /**
     * 连接超时（毫秒）
     */
    private int connectTimeout = SimpleAkskRestTemplateConstant.DEFAULT_CONNECT_TIMEOUT_MS;

    /**
     * 读取超时（毫秒）
     */
    private int readTimeout = SimpleAkskRestTemplateConstant.DEFAULT_READ_TIMEOUT_MS;
}
