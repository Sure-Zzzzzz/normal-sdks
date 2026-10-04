package io.github.surezzzzzz.sdk.kms.resttemplate.client.configuration;

import io.github.surezzzzzz.sdk.kms.client.constant.SimpleKmsClientConstant;
import io.github.surezzzzzz.sdk.kms.resttemplate.client.SimpleKmsClientRestTemplatePackage;
import io.github.surezzzzzz.sdk.kms.resttemplate.client.annotation.SimpleKmsClientRestTemplateComponent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * KMS Client RestTemplate 自动配置（直接引用 aksk 底座）。
 *
 * <p>启用条件：{@code io.github.surezzzzzz.sdk.kms.client.enable=true} 且存在 RestTemplate 类。
 * 传输与认证全部复用底座 {@code akskClientRestTemplate}；客户端 {@code KmsRestTemplateClient}
 * 经组件扫描注册，底座 RestTemplate 缺失时启动即报缺 Bean（响亮失败，不静默）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = SimpleKmsClientConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
@ConditionalOnClass(RestTemplate.class)
@EnableConfigurationProperties(KmsClientRestTemplateProperties.class)
@ComponentScan(
        basePackageClasses = SimpleKmsClientRestTemplatePackage.class,
        includeFilters = @ComponentScan.Filter(SimpleKmsClientRestTemplateComponent.class),
        useDefaultFilters = false
)
public class KmsClientRestTemplateAutoConfiguration {

    /**
     * 自动配置加载成功的最小埋点（与 aksk 底座形态一致）。
     */
    @PostConstruct
    public void init() {
        log.info("===== KMS RestTemplate Client 自动配置加载成功 =====");
    }
}
