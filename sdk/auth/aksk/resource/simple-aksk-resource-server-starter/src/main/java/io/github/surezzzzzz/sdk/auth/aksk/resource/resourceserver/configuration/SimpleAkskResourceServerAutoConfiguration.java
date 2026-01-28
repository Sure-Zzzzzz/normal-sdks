package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.aspect.SimpleAkskSecurityAspect;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.SimpleAkskResourceServerPackage;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.annotation.SimpleAkskResourceServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.constant.SimpleAkskResourceServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.provider.AkskJwtContextProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

import javax.annotation.PostConstruct;

/**
 * Simple AKSK Resource Server 自动配置
 *
 * <p>自动配置 JWT 验证和权限注解支持。
 *
 * <p>配置项：
 * <ul>
 *   <li>io.github.surezzzzzz.sdk.auth.aksk.resource.server.enabled: 是否启用（默认：true）</li>
 *   <li>io.github.surezzzzzz.sdk.auth.aksk.resource.server.jwt.public-key: JWT 公钥字符串</li>
 *   <li>io.github.surezzzzzz.sdk.auth.aksk.resource.server.jwt.public-key-location: JWT 公钥文件路径</li>
 *   <li>io.github.surezzzzzz.sdk.auth.aksk.resource.server.security.protected-paths: 需要保护的路径</li>
 *   <li>io.github.surezzzzzz.sdk.auth.aksk.resource.server.security.permit-all-paths: 白名单路径</li>
 * </ul>
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(SimpleAkskResourceServerProperties.class)
@ComponentScan(
        basePackageClasses = SimpleAkskResourceServerPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleAkskResourceServerComponent.class)
)
@Import(ResourceServerSecurityConfiguration.class)
@ConditionalOnProperty(
        prefix = SimpleAkskResourceServerConstant.CONFIG_PREFIX,
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SimpleAkskResourceServerAutoConfiguration {

    private final SimpleAkskResourceServerProperties properties;

    public SimpleAkskResourceServerAutoConfiguration(SimpleAkskResourceServerProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        log.info("Simple AKSK Resource Server Starter initialized");
        log.info("Protected paths: {}", properties.getSecurity().getProtectedPaths());
        log.info("Permit all paths: {}", properties.getSecurity().getPermitAllPaths());
    }

    /**
     * 注册 SimpleAkskSecurityAspect
     * <p>
     * 使用 AkskJwtContextProvider 作为 SimpleAkskSecurityContextProvider 实现
     * </p>
     *
     * @return SimpleAkskSecurityAspect
     */
    @Bean
    public SimpleAkskSecurityAspect akskSecurityAspect() {
        log.info("SimpleAkskSecurityAspect registered with AkskJwtContextProvider");
        return new SimpleAkskSecurityAspect(new AkskJwtContextProvider());
    }
}
