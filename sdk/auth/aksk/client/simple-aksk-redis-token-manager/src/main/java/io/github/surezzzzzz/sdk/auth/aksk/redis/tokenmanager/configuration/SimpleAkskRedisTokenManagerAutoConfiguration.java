package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.DefaultSecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.SimpleAkskRedisTokenManagerPackage;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.annotation.SimpleAkskRedisTokenManagerComponent;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import javax.annotation.PostConstruct;

/**
 * Simple AKSK Redis Token Manager Auto Configuration
 *
 * <p>Redis Token Manager 的自动配置类，基于 SmartCacheManager 提供 L1+L2 两级缓存。
 *
 * <p>启用条件：
 * <ul>
 *   <li>{@code io.github.surezzzzzz.sdk.auth.aksk.client.enable=true}</li>
 *   <li>存在 RedisConnectionFactory</li>
 *   <li>存在 SmartCacheManager Bean（来自 smart-cache-starter）</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = SimpleAkskClientCoreConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
@ConditionalOnClass({RedisConnectionFactory.class, SmartCacheManager.class})
@EnableConfigurationProperties({SimpleAkskClientCoreProperties.class, SimpleAkskRedisTokenManagerProperties.class})
@ComponentScan(
        basePackageClasses = SimpleAkskRedisTokenManagerPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleAkskRedisTokenManagerComponent.class),
        useDefaultFilters = false
)
public class SimpleAkskRedisTokenManagerAutoConfiguration {

    @PostConstruct
    public void init() {
        log.info("===== Simple AKSK Redis Token Manager 自动配置加载成功 =====");
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

    /**
     * TokenRefreshExecutor — 供 RedisTokenManager 和 TokenCachePreloadHandler 共享
     */
    @Bean
    @ConditionalOnMissingBean(TokenRefreshExecutor.class)
    public TokenRefreshExecutor tokenRefreshExecutor(
            SimpleAkskClientCoreProperties coreProperties,
            TaskRetryExecutor retryExecutor) {
        return new TokenRefreshExecutor(coreProperties, retryExecutor);
    }
}

