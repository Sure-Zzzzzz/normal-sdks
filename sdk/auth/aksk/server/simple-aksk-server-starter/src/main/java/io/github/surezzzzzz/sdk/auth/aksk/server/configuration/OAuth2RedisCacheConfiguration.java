package io.github.surezzzzzz.sdk.auth.aksk.server.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.server.support.RedisKeyHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * OAuth2 Redis Cache Configuration
 * 配置Redis缓存，用于OAuth2授权信息的缓存
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@EnableCaching
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.auth.aksk.server.redis",
        name = "enabled",
        havingValue = "true"
)
public class OAuth2RedisCacheConfiguration {

    private final SimpleAkskServerProperties properties;

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 使用String序列化器作为key序列化器
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // 使用JSON序列化器作为value序列化器
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        log.info("Redis template configured successfully");
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 获取应用标识
        String me = properties.getRedis().getToken().getMe();

        // 获取JWT过期时间作为缓存TTL
        Integer ttlSeconds = properties.getJwt().getExpiresIn();

        // 配置key前缀: sure-auth-aksk:{me}:
        String keyPrefix = String.format(RedisKeyHelper.REDIS_KEY_PREFIX_TEMPLATE, me);

        // 配置缓存
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(ttlSeconds))
                .computePrefixWith(cacheName -> keyPrefix + cacheName + "::")
                .disableCachingNullValues();

        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .transactionAware()
                .build();

        log.info("Redis cache manager configured: prefix={}, ttl={}s", keyPrefix, ttlSeconds);
        return cacheManager;
    }
}
