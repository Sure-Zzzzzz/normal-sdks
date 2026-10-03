package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.DefaultSecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.manager.RedisTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.preload.TokenCachePreloadHandler;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheConfiguration;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.RedisConnectionFactory;

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
@AutoConfiguration(after = SmartCacheConfiguration.class)
@ConditionalOnProperty(prefix = SimpleAkskClientCoreConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
@ConditionalOnClass({RedisConnectionFactory.class, SmartCacheManager.class})
@EnableConfigurationProperties({SimpleAkskClientCoreProperties.class, SimpleAkskRedisTokenManagerProperties.class})
public class SimpleAkskRedisTokenManagerAutoConfiguration {
    /**
     * SecurityContextProvider（默认实现）
     */
    @Bean
    @ConditionalOnBean(SmartCacheManager.class)
    @ConditionalOnMissingBean(SecurityContextProvider.class)
    public SecurityContextProvider securityContextProvider() {
        return new DefaultSecurityContextProvider();
    }

    /**
     * TokenRefreshExecutor — 供 RedisTokenManager 和 TokenCachePreloadHandler 共享
     */
    @Bean
    @ConditionalOnBean({SmartCacheManager.class, TaskRetryExecutor.class})
    @ConditionalOnMissingBean(TokenRefreshExecutor.class)
    public TokenRefreshExecutor tokenRefreshExecutor(
            SimpleAkskClientCoreProperties coreProperties,
            TaskRetryExecutor retryExecutor) {
        return new TokenRefreshExecutor(coreProperties, retryExecutor);
    }

    /**
     * SB3 的 OnBean 条件处于 Bean 注册阶段，不能与 ComponentScan 放在同一配置类。
     * 因此显式注册内部组件，仍保留调用方覆盖 TokenManager 的扩展边界。
     */
    @Bean
    @ConditionalOnBean({SmartCacheManager.class, SmartCacheProperties.class,
            TokenRefreshExecutor.class, SimpleRedisLock.class})
    @ConditionalOnMissingBean(TokenManager.class)
    public RedisTokenManager redisTokenManager(
            SecurityContextProvider securityContextProvider,
            SmartCacheManager cacheManager,
            SimpleAkskRedisTokenManagerProperties properties,
            SmartCacheProperties smartCacheProperties,
            TokenRefreshExecutor tokenRefreshExecutor,
            SimpleRedisLock redisLock) {
        return new RedisTokenManager(securityContextProvider, cacheManager, properties,
                smartCacheProperties, tokenRefreshExecutor, redisLock);
    }

    @Bean
    @ConditionalOnBean({SmartCacheManager.class, TokenRefreshExecutor.class})
    @ConditionalOnMissingBean(TokenCachePreloadHandler.class)
    public TokenCachePreloadHandler tokenCachePreloadHandler(
            TokenRefreshExecutor tokenRefreshExecutor,
            SimpleAkskRedisTokenManagerProperties properties,
            @Lazy SmartCacheManager cacheManager) {
        return new TokenCachePreloadHandler(tokenRefreshExecutor, properties, cacheManager);
    }
}
