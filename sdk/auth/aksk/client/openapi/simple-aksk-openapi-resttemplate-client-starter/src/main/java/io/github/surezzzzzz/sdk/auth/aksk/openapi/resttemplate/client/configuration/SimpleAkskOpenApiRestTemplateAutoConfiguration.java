package io.github.surezzzzzz.sdk.auth.aksk.openapi.resttemplate.client.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.openapi.resttemplate.client.SimpleAkskOpenApiRestTemplatePackage;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.resttemplate.client.annotation.SimpleAkskOpenApiRestTemplateComponent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.web.client.RestTemplate;

/**
 * AKSK OpenAPI RestTemplate 客户端自动配置（javax 线，spring.factories 注册）。
 *
 * <p>启用条件：底座总开关 {@code io.github.surezzzzzz.sdk.auth.aksk.client.enable=true} 且
 * RestTemplate 类在场——复用底座开关，本模块零新增配置键。组件扫描只认本模块自定义注解标记的组件，
 * 不做全局扫描；底座 {@code akskClientRestTemplate} 缺失时由注入点启动即报缺 Bean（响亮失败）。</p>
 *
 * @author surezzzzzz
 */
@Configuration
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.auth.aksk.client",
        name = "enable", havingValue = "true")
@ConditionalOnClass(RestTemplate.class)
@ComponentScan(
        basePackageClasses = SimpleAkskOpenApiRestTemplatePackage.class,
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ANNOTATION,
                classes = SimpleAkskOpenApiRestTemplateComponent.class),
        useDefaultFilters = false
)
public class SimpleAkskOpenApiRestTemplateAutoConfiguration {
}
