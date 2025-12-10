package io.github.surezzzzzz.sdk.limiter.redis.smart.configuration;

import io.github.surezzzzzz.sdk.limiter.redis.smart.SmartRedisLimiterPackage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import javax.annotation.PostConstruct;

/**
 * @author: Sure.
 * @description 智能Redis限流器自动配置
 * @Date: 2024/12/XX XX:XX
 */
@Configuration
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.limiter.redis.smart",
        name = "enable",
        havingValue = "true"
)
@AutoConfigureAfter(RedisAutoConfiguration.class)
@ComponentScan(
        basePackageClasses = SmartRedisLimiterPackage.class,
        includeFilters = @ComponentScan.Filter(SmartRedisLimiterComponent.class),
        useDefaultFilters = false
)
@EnableAspectJAutoProxy
@Slf4j
public class SmartRedisLimiterAutoConfiguration {

    @PostConstruct
    public void init() {
        log.info("===== SmartRedisLimiter 自动配置加载成功 =====");
    }

    /**
     * 创建专用的RedisTemplate
     */
    @Bean
    public RedisTemplate<String, String> smartRedisLimiterRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        return redisTemplate;
    }

}
