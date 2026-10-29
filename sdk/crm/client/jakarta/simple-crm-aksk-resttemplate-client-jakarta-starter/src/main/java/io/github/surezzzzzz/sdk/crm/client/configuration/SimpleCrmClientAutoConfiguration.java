package io.github.surezzzzzz.sdk.crm.client.configuration;

import io.github.surezzzzzz.sdk.crm.client.CrmClient;
import io.github.surezzzzzz.sdk.crm.client.resttemplate.CrmRestTemplateClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * CRM 客户端 jakarta 传输自动配置：传输与认证全归 AKSK 底座 {@code akskClientRestTemplate}，
 * 本模块零自建池/拦截器/执行器；底座缺席时整链退场。
 *
 * @author surezzzzzz
 */
@AutoConfiguration
@ConditionalOnClass({RestTemplate.class, CrmClient.class})
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.crm.client",
        name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SimpleCrmClientProperties.class)
public class SimpleCrmClientAutoConfiguration {

    /**
     * 默认客户端（宿主自带 CrmClient 时让位）。
     *
     * @param akskClientRestTemplate AKSK 底座认证传输
     * @param properties 配置
     * @return CRM 客户端
     */
    @Bean
    @ConditionalOnMissingBean(CrmClient.class)
    @ConditionalOnClass(name = "io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client"
            + ".configuration.SimpleAkskRestTemplateRedisClientAutoConfiguration")
    public CrmClient crmClient(
            @Qualifier("akskClientRestTemplate") RestTemplate akskClientRestTemplate,
            SimpleCrmClientProperties properties) {
        return new CrmRestTemplateClient(akskClientRestTemplate, properties.getBaseUrl());
    }
}
