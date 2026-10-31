package io.github.surezzzzzz.sdk.license.server.limiter.configuration;

import io.github.surezzzzzz.sdk.license.server.limiter.SimpleLicenseLimiterPolicyAdaptorPackage;
import io.github.surezzzzzz.sdk.license.server.limiter.annotation.SimpleLicenseLimiterPolicyAdaptorComponent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * License Limiter Policy Adaptor 自动配置。
 *
 * <p>引用即装配，不提供 enable 开关或 MissingBean 回落。宿主引入本件即明确选择
 * limiter Management 履约形态，缺少 mutation client 时启动应直接失败。</p>
 *
 * @author surezzzzzz
 */
@Configuration
@EnableConfigurationProperties(SimpleLicenseLimiterPolicyAdaptorProperties.class)
@ComponentScan(
        basePackageClasses = SimpleLicenseLimiterPolicyAdaptorPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleLicenseLimiterPolicyAdaptorComponent.class),
        useDefaultFilters = false
)
public class SimpleLicenseLimiterPolicyAdaptorAutoConfiguration {
}
