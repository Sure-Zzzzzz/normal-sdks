package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.resttemplate.configuration;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.resttemplate.SmartRedisLimiterManagementClientRestTemplatePackage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.resttemplate.annotation.SmartRedisLimiterManagementClientRestTemplateComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.support.JacksonSmartRedisLimiterManagementJsonCodec;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.support.SmartRedisLimiterManagementJsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;

/**
 * 限流管理客户端 RestTemplate 自动配置
 * <p>启用条件：{@code io.github.surezzzzzz.sdk.limiter.management.client.enable=true} 且存在
 * RestTemplate 类。客户端经组件扫描注册，传输与认证复用 AKSK 底座 {@code akskClientRestTemplate}，
 * 缺失时启动即报缺 Bean（响亮失败，不静默降级）；宿主注册自定义
 * {@link io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.SmartRedisLimiterManagementClient}
 * Bean 时组件装配自然退让。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@ConditionalOnClass(RestTemplate.class)
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.limiter.management.client",
        name = "enable", havingValue = "true")
@EnableConfigurationProperties(SmartRedisLimiterManagementClientProperties.class)
@ComponentScan(
        basePackageClasses = SmartRedisLimiterManagementClientRestTemplatePackage.class,
        includeFilters = @ComponentScan.Filter(SmartRedisLimiterManagementClientRestTemplateComponent.class),
        useDefaultFilters = false
)
public class SmartRedisLimiterManagementClientAutoConfiguration {

    /**
     * 自动配置加载成功的最小埋点（与 KMS/aksk 底座形态一致）。
     */
    @PostConstruct
    public void init() {
        log.debug("限流管理 RestTemplate 客户端自动配置已加载");
    }

    /**
     * 装配独立 JSON 编解码器（宿主可替换）
     *
     * @return 编解码器
     */
    @Bean
    @ConditionalOnMissingBean(SmartRedisLimiterManagementJsonCodec.class)
    public SmartRedisLimiterManagementJsonCodec smartRedisLimiterManagementJsonCodec() {
        return new JacksonSmartRedisLimiterManagementJsonCodec();
    }
}
