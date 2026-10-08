package io.github.surezzzzzz.sdk.iam.resttemplate.client.configuration;

import io.github.surezzzzzz.sdk.iam.resttemplate.client.SimpleIamClientRestTemplatePackage;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.annotation.SimpleIamClientRestTemplateComponent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * IAM Client RestTemplate 传输自动配置（直接引用 aksk 底座）。
 *
 * <p>启用条件：{@code io.github.surezzzzzz.sdk.iam.client.enable=true} 且存在 RestTemplate 类。
 * 传输与认证全部复用底座 {@code akskClientRestTemplate}；客户端经组件扫描注册，底座模板缺失时
 * 启动即报缺 Bean（响亮失败，不静默）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.iam.client", name = "enable", havingValue = "true")
@ConditionalOnClass(RestTemplate.class)
@EnableConfigurationProperties(SimpleIamClientRestTemplateProperties.class)
@ComponentScan(
        basePackageClasses = SimpleIamClientRestTemplatePackage.class,
        includeFilters = @ComponentScan.Filter(SimpleIamClientRestTemplateComponent.class),
        useDefaultFilters = false
)
public class SimpleIamClientRestTemplateAutoConfiguration {

    /**
     * 自动配置加载成功的最小埋点（与 aksk 底座形态一致）。
     */
    @PostConstruct
    public void init() {
        log.info("===== IAM RestTemplate Client 自动配置加载成功 =====");
    }
}
