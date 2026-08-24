package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.SimpleAkskResourceServerPackage;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.annotation.SimpleAkskResourceServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.constant.SimpleAkskResourceServerConstant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Simple AKSK Resource Server 自动配置。
 *
 * <p>自动配置 AKSK 认证适配器及模块组件扫描。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties(SimpleAkskResourceServerProperties.class)
@ComponentScan(
        basePackageClasses = SimpleAkskResourceServerPackage.class,
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(SimpleAkskResourceServerComponent.class)
)
@Import(AkskResourceAuthenticationConfiguration.class)
@ConditionalOnProperty(
        prefix = SimpleAkskResourceServerConstant.CONFIG_PREFIX,
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SimpleAkskResourceServerAutoConfiguration {

}
