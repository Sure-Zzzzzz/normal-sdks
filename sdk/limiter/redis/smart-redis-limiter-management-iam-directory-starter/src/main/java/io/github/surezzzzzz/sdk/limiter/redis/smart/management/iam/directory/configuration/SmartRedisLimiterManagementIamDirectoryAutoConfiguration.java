package io.github.surezzzzzz.sdk.limiter.redis.smart.management.iam.directory.configuration;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.iam.directory.SmartRedisLimiterManagementIamDirectoryPackage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.iam.directory.annotation.SmartRedisLimiterManagementIamDirectoryComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * IAM 用户目录适配件自动配置（adaptor 形态：引用即装配）。
 *
 * <p>仅扫描本模块自定义注解标记的组件：目录装饰器（BeanPostProcessor）在 BeanDefinition
 * 阶段注册（早于业务 Bean 实例化），容器内每个目录提供方初始化后被包装为适配件
 * （USER 维度走 IAM，其余转发原实现）；无开关、无让位。本件只依赖目录 SPI 契约层
 * （smart-redis-limiter-core），不依赖管理面实现；IamUserClient 缺失时装饰阶段响亮失败。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@ComponentScan(
        basePackageClasses = SmartRedisLimiterManagementIamDirectoryPackage.class,
        includeFilters = @ComponentScan.Filter(SmartRedisLimiterManagementIamDirectoryComponent.class),
        useDefaultFilters = false
)
public class SmartRedisLimiterManagementIamDirectoryAutoConfiguration {

    /**
     * 自动配置加载成功的最小埋点（与家族件形态一致）
     */
    @PostConstruct
    public void init() {
        log.info("===== SmartRedisLimiterManagement IamDirectory 自动配置加载成功 =====");
    }
}
