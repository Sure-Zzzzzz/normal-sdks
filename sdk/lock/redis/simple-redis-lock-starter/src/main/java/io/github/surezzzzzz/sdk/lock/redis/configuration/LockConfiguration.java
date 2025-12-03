package io.github.surezzzzzz.sdk.lock.redis.configuration;

import io.github.surezzzzzz.sdk.lock.redis.LockPackage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/12/11 11:26
 */
@Configuration
@ComponentScan(
        basePackageClasses = LockPackage.class,
        includeFilters = @ComponentScan.Filter(LockComponent.class)
)
public class LockConfiguration {

    @Bean
    public RedisTemplate<String, String> simpleRedisLockRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        return redisTemplate;
    }
}
