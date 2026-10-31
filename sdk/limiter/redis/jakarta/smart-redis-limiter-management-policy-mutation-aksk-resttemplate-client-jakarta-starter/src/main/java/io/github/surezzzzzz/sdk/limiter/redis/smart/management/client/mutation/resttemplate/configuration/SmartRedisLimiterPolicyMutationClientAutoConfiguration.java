package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.resttemplate.configuration;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.resttemplate.SmartRedisLimiterPolicyMutationClientRestTemplatePackage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.resttemplate.annotation.SmartRedisLimiterPolicyMutationClientRestTemplateComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.support.JacksonPolicyMutationJsonCodec;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.support.PolicyMutationJsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

/**
 * 管理面策略变更客户端 RestTemplate 自动配置
 *
 * <p>启用条件：{@code io.github.surezzzzzz.sdk.limiter.management.policy-mutation-client.enable=true}
 * 且存在 RestTemplate 类。传输与认证复用 AKSK 底座 {@code akskClientRestTemplate}，缺失时启动即
 * 报缺 Bean（响亮失败，不静默降级）；宿主注册自定义
 * {@link io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.SmartRedisLimiterPolicyMutationClient}
 * Bean 时组件装配自然退让。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@ConditionalOnClass(RestTemplate.class)
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.limiter.management.policy-mutation-client",
        name = "enable", havingValue = "true")
@EnableConfigurationProperties(SmartRedisLimiterPolicyMutationClientProperties.class)
@ComponentScan(
        basePackageClasses = SmartRedisLimiterPolicyMutationClientRestTemplatePackage.class,
        includeFilters = @ComponentScan.Filter(SmartRedisLimiterPolicyMutationClientRestTemplateComponent.class),
        useDefaultFilters = false
)
public class SmartRedisLimiterPolicyMutationClientAutoConfiguration {

    /**
     * 自动配置加载成功的最小埋点（与家族件形态一致）
     */
    @PostConstruct
    public void init() {
        log.debug("限流管理策略变更客户端自动配置已加载");
    }

    /**
     * 装配独立 JSON 编解码器（宿主可替换）
     */
    @Bean
    @ConditionalOnMissingBean(PolicyMutationJsonCodec.class)
    public PolicyMutationJsonCodec policyMutationJsonCodec() {
        return new JacksonPolicyMutationJsonCodec();
    }
}
