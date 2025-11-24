package io.github.surezzzzzz.sdk.retry.redis.configuration;

import io.github.surezzzzzz.sdk.retry.redis.RedisRetryPackage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 重试自动配置
 *
 * @author: Sure.
 * @Date: 2025/3/11
 */
@Configuration
@ConditionalOnClass(RedisTemplate.class)
@EnableConfigurationProperties(RedisRetryProperties.class)
@ComponentScan(
        basePackageClasses = RedisRetryPackage.class,
        includeFilters = @ComponentScan.Filter(RedisRetryComponent.class)
)
@Slf4j
public class RedisRetryAutoConfiguration {

    /**
     * Redis 重试专用模板
     */
    @Bean("redisRetryTemplate")
    @ConditionalOnMissingBean(name = "redisRetryTemplate")
    public RedisTemplate<String, String> redisRetryTemplate(RedisConnectionFactory redisConnectionFactory) {
        log.info("创建 Redis 重试专用模板，连接工厂 {}", redisConnectionFactory.getClass().getSimpleName());

        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new StringRedisSerializer());
        redisTemplate.afterPropertiesSet();

        log.debug("Redis 重试模板配置完成: key序列化器{}, value序列化器{}",
                redisTemplate.getKeySerializer().getClass().getSimpleName(),
                redisTemplate.getValueSerializer().getClass().getSimpleName());

        return redisTemplate;
    }
}