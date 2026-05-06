package io.github.surezzzzzz.sdk.auth.aksk.httpsession.tokenmanager.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.DefaultSecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.httpsession.tokenmanager.SimpleAkskHttpSessionTokenManagerPackage;
import io.github.surezzzzzz.sdk.auth.aksk.httpsession.tokenmanager.annotation.SimpleAkskHttpSessionTokenManagerComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpSession;

/**
 * Simple AKSK HttpSession Token Manager Auto Configuration
 * <p>
 * HttpSession Token Manager 的自动配置类
 * <p>
 * 启用条件：
 * <ul>
 *   <li>io.github.surezzzzzz.sdk.auth.aksk.client.enable=true</li>
 *   <li>存在 HttpSession 类</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = SimpleAkskClientCoreConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
@ConditionalOnClass(HttpSession.class)
@EnableConfigurationProperties({SimpleAkskClientCoreProperties.class, SimpleAkskHttpSessionTokenManagerProperties.class})
@ComponentScan(
        basePackageClasses = SimpleAkskHttpSessionTokenManagerPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleAkskHttpSessionTokenManagerComponent.class),
        useDefaultFilters = false
)
public class SimpleAkskHttpSessionTokenManagerAutoConfiguration {

    @PostConstruct
    public void init() {
        log.info("===== Simple AKSK HttpSession Token Manager 自动配置加载成功 =====");
    }

    /**
     * SecurityContextProvider（默认实现）
     */
    @Bean
    @ConditionalOnMissingBean(SecurityContextProvider.class)
    public SecurityContextProvider securityContextProvider() {
        log.info("Creating DefaultSecurityContextProvider");
        return new DefaultSecurityContextProvider();
    }
}
