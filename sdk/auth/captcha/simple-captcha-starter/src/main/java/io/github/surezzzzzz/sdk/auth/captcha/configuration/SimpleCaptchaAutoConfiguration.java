package io.github.surezzzzzz.sdk.auth.captcha.configuration;

import io.github.surezzzzzz.sdk.auth.captcha.SimpleCaptchaPackage;
import io.github.surezzzzzz.sdk.auth.captcha.annotation.SimpleCaptchaComponent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Simple Captcha Auto Configuration
 *
 * <p>默认图片实现与 Redis 挑战存储均为组件装配（{@code @SimpleCaptchaComponent}
 * 精准扫描）；存储强依赖 redis-route，宿主无 route bean 时启动快速失败
 * （无内存模式）。自定义验证码实现的接入方不引本模块运行时
 * （compileOnly 取 spi 接口），不同实现不同引用，无让位场景。
 * 被动功能模块无 enable 开关——不想用不引包即可
 * （同 simple-prometheus-client-starter 形态）。
 *
 * @author surezzzzzz
 */
@Configuration
@EnableConfigurationProperties(SimpleCaptchaProperties.class)
@ComponentScan(
        basePackageClasses = SimpleCaptchaPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleCaptchaComponent.class),
        useDefaultFilters = false
)
public class SimpleCaptchaAutoConfiguration {
}
