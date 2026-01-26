package io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.configuration;

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
@ConfigurationProperties(prefix = "io.github.surezzzzzz.sdk.auth.aksk.client.resttemplate")
public class SimpleAkskRestTemplateProperties {

    /**
     * 是否创建 akskClientRestTemplate Bean
     */
    private boolean enable = false;

    /**
     * 最大连接数
     */
    private int maxTotal = 100;

    /**
     * 每个路由的最大连接数
     */
    private int maxPerRoute = 20;

    /**
     * 连接超时（毫秒）
     */
    private int connectTimeout = 5000;

    /**
     * 读取超时（毫秒）
     */
    private int readTimeout = 30000;
}
