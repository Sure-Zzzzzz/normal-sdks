package io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.configuration;

import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.SimpleIamOidcAdapterPackage;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.annotation.SimpleIamOidcAdapterComponent;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.service.MemoryPendingStateStore;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.service.PendingStateStore;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.service.RedisPendingStateStore;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalBrowserLoginProvider;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Simple IAM OIDC Adapter Auto Configuration
 *
 * <p>引依赖即装配，无 enable 开关；宿主不引入本 starter 即不装配，
 * 引入后必填配置校验启动期快速失败。仅扫描本模块自定义注解标记的组件；
 * 提供方的 RestTemplate / NimbusJwtDecoder 组装与 iss/aud 校验器配置在组件构造内
 * 自治完成。授权暂存上下文的部署形态选型（多实例 Redis / 单实例内存）依赖运行时
 * bean 可用性判断，保留 @Bean 装配。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@ConditionalOnClass(ExternalBrowserLoginProvider.class)
@EnableConfigurationProperties(SimpleIamOidcAdapterProperties.class)
@ComponentScan(
        basePackageClasses = SimpleIamOidcAdapterPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleIamOidcAdapterComponent.class),
        useDefaultFilters = false
)
public class SimpleIamOidcAdapterAutoConfiguration {

    /**
     * 授权暂存上下文按部署形态选型：宿主存在 RedisRouteTemplate bean
     * （多实例部署，redis-route 已启用）走 Redis 共享存储；缺失时退回实例内内存实现，
     * 单实例语义不变。
     *
     * <p>选型必须在运行时以 {@link ObjectProvider#getIfAvailable()} 判断——
     * 组件扫描阶段的条件注解无法可靠感知宿主 bean（装配顺序不定），
     * 故此处保留 @Bean；两种 store 实现不挂组件注解，防止精准扫描双注册。</p>
     *
     * @param redisRouteTemplateProvider redis-route 门面（可能缺失）
     * @return 授权暂存上下文
     */
    @Bean
    public PendingStateStore simpleIamOidcPendingStateStore(
            ObjectProvider<RedisRouteTemplate> redisRouteTemplateProvider) {
        RedisRouteTemplate redisRouteTemplate = redisRouteTemplateProvider.getIfAvailable();
        boolean shared = redisRouteTemplate != null;
        log.info("OIDC 授权暂存上下文选型：{}（{}部署形态）",
                shared ? "Redis 共享存储" : "实例内内存",
                shared ? "多实例" : "单实例");
        return shared ? new RedisPendingStateStore(redisRouteTemplate)
                : new MemoryPendingStateStore();
    }
}
