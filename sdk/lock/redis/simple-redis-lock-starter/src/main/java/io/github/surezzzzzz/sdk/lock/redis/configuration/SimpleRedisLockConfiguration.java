package io.github.surezzzzzz.sdk.lock.redis.configuration;

import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLockPackage;
import io.github.surezzzzzz.sdk.lock.redis.annotation.SimpleRedisLockComponent;
import io.github.surezzzzzz.sdk.lock.redis.constant.SimpleRedisLockConstant;
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
 * Simple Redis Lock 默认自动配置
 *
 * @author surezzzzzz
 */
@Configuration
@EnableConfigurationProperties(SimpleRedisLockProperties.class)
@ComponentScan(
        basePackageClasses = SimpleRedisLockPackage.class,
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(SimpleRedisLockComponent.class)
)
public class SimpleRedisLockConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = SimpleRedisLockConstant.SIMPLE_REDIS_LOCK_REDIS_TEMPLATE_BEAN_NAME)
    @ConditionalOnProperty(
            prefix = SimpleRedisLockConstant.ROUTE_CONFIG_PREFIX,
            name = SimpleRedisLockConstant.PROPERTY_ENABLE,
            havingValue = SimpleRedisLockConstant.PROPERTY_VALUE_FALSE,
            matchIfMissing = true
    )
    public StringRedisTemplate simpleRedisLockRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        StringRedisTemplate redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        return redisTemplate;
    }

    @Bean
    @ConditionalOnMissingBean(RedisLockExecutor.class)
    @ConditionalOnProperty(
            prefix = SimpleRedisLockConstant.ROUTE_CONFIG_PREFIX,
            name = SimpleRedisLockConstant.PROPERTY_ENABLE,
            havingValue = SimpleRedisLockConstant.PROPERTY_VALUE_FALSE,
            matchIfMissing = true
    )
    public RedisLockExecutor defaultRedisLockExecutor(StringRedisTemplate simpleRedisLockRedisTemplate) {
        return new DefaultRedisLockExecutor(simpleRedisLockRedisTemplate);
    }
}
