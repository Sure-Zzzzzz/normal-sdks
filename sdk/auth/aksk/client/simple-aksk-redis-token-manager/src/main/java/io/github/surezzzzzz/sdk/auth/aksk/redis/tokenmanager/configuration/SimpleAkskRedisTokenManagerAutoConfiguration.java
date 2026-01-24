package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.DefaultSecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.SimpleAkskRedisTokenManagerPackage;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.annotation.SimpleAkskRedisTokenManagerComponent;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;

/**
 * Simple AKSK Redis Token Manager Auto Configuration
 * <p>
 * Redis Token Manager 的自动配置类
 * <p>
 * 启用条件：
 * <ul>
 *   <li>io.github.surezzzzzz.sdk.auth.aksk.client.enable=true</li>
 *   <li>存在 RedisConnectionFactory</li>
 *   <li>存在 SimpleRedisLock（来自 simple-redis-lock-starter）</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = SimpleAkskClientCoreConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
@ConditionalOnClass({RedisConnectionFactory.class, SimpleRedisLock.class})
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
     * 专用的 StringRedisTemplate（用于 Token 缓存）
     * <p>
     * 注意：
     * <ul>
     *   <li>使用 String 序列化器（避免序列化兼容性问题）</li>
     *   <li>不影响用户的 RedisTemplate</li>
     * </ul>
     */
    @Bean(name = "akskClientRedisTemplate")
    @ConditionalOnMissingBean(name = "akskClientRedisTemplate")
    public StringRedisTemplate akskClientRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        log.info("Creating StringRedisTemplate for AKSK client token cache");
        return new StringRedisTemplate(redisConnectionFactory);
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
