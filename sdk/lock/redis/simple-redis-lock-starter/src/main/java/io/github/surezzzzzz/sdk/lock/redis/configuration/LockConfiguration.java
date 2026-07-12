package io.github.surezzzzzz.sdk.lock.redis.configuration;

import io.github.surezzzzzz.sdk.lock.redis.LockPackage;
import io.github.surezzzzzz.sdk.lock.redis.executor.DefaultRedisLockExecutor;
import io.github.surezzzzzz.sdk.lock.redis.executor.RedisLockExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 分布式锁默认自动配置（单 Redis 模式）。
 *
 * @author surezzzzzz
 */
@Configuration
@EnableConfigurationProperties(SimpleRedisLockProperties.class)
@ComponentScan(
        basePackageClasses = LockPackage.class,
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(LockComponent.class)
)
public class LockConfiguration {

    /**
     * 默认单 Redis 模板，route 模式开启时不注册，避免要求全局 RedisConnectionFactory。
     */
    @Bean
    @ConditionalOnMissingBean(name = "simpleRedisLockRedisTemplate")
    @ConditionalOnProperty(
            prefix = "io.github.surezzzzzz.sdk.lock.redis.route",
            name = "enable",
            havingValue = "false",
            matchIfMissing = true
    )
    public StringRedisTemplate simpleRedisLockRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        StringRedisTemplate redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        return redisTemplate;
    }

    /**
     * 默认单 Redis 执行器，route 模式开启时不注册。
     */
    @Bean
    @ConditionalOnMissingBean(RedisLockExecutor.class)
    @ConditionalOnProperty(
            prefix = "io.github.surezzzzzz.sdk.lock.redis.route",
            name = "enable",
            havingValue = "false",
            matchIfMissing = true
    )
    public RedisLockExecutor defaultRedisLockExecutor(StringRedisTemplate simpleRedisLockRedisTemplate) {
        return new DefaultRedisLockExecutor(simpleRedisLockRedisTemplate);
    }
}
