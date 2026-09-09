package io.github.surezzzzzz.sdk.auth.iam.adapter.captcha.configuration;

import io.github.surezzzzzz.sdk.auth.iam.adapter.captcha.SimpleIamCaptchaAdapterPackage;
import io.github.surezzzzzz.sdk.auth.iam.adapter.captcha.annotation.SimpleIamCaptchaAdapterComponent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Simple IAM Captcha Adapter Auto Configuration
 *
 * <p>引依赖即装配，无 enable 开关；宿主不引入本 starter 即不装配。
 * 将通用 captcha 模块的 {@code CaptchaProvider} 桥接为 iam-core 登录链路
 * 契约。业务方需要滑块 / 行为码 / 云验证码时直接实现 iam-core 契约，
 * 并移除本 adapter 依赖（不同实现不同引用，无让位场景）。
 * 仅扫描本模块自定义注解标记的组件。
 *
 * @author surezzzzzz
 */
@Configuration
@ConditionalOnClass(io.github.surezzzzzz.sdk.auth.iam.core.spi.CaptchaProvider.class)
@ComponentScan(
        basePackageClasses = SimpleIamCaptchaAdapterPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleIamCaptchaAdapterComponent.class),
        useDefaultFilters = false
)
public class SimpleIamCaptchaAdapterAutoConfiguration {
}
