package io.github.surezzzzzz.sdk.limiter.redis.configuration;


import io.github.surezzzzzz.sdk.limiter.redis.RedisLimiterPackage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/12/11 11:26
 */
@Configuration
@ComponentScan(
        basePackageClasses = RedisLimiterPackage.class,
        includeFilters = @ComponentScan.Filter(RedisLimiterComponent.class)
)
@EnableScheduling
public class SimpleRedisLimiterConfiguration {

    @Bean
    public RedisTemplate<String, String> simpleRedisLimiterRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        return redisTemplate;
    }
}
