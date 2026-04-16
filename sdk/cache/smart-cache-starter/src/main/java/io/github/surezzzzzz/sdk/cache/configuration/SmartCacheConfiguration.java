package io.github.surezzzzzz.sdk.cache.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.surezzzzzz.sdk.cache.SmartCachePackage;
import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Smart Cache Auto Configuration
 * <p>
 * 自动配置类
 * </p>
 *
 * @author Sure
 */
@Slf4j
@Configuration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(SmartCacheProperties.class)
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(
        basePackageClasses = SmartCachePackage.class,
        includeFilters = @ComponentScan.Filter(SmartCacheComponent.class)
)
public class SmartCacheConfiguration {

    @Bean
    @ConditionalOnClass(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(name = "smartCacheRedisTemplate")
    public RedisTemplate<String, Object> smartCacheRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 使用 String 序列化器作为 key 序列化器
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // 使用 JSON 序列化器作为 value 序列化器
        // 注册 JavaTimeModule 支持 Java 8 时间类型（Instant、LocalDateTime 等）
        // 禁用 WRITE_DATES_AS_TIMESTAMPS，时间序列化为 ISO 字符串而非数组
        // 启用 DefaultTyping 写入 @class 字段，确保反序列化时能正确还原具体类型
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL
        );
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Redis 消息监听容器
     * 用于 Pub/Sub 功能，强一致性模式需要
     * <p>
     * 注意：容器会在CacheInvalidationListener的@PostConstruct中手动启动
     * 这样可以捕获启动时的连接异常，避免阻止Spring容器启动
     */
    @Bean(destroyMethod = "destroy")
    @ConditionalOnClass(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(RedisMessageListenerContainer.class)
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // 添加错误处理器，防止运行时错误导致容器崩溃
        container.setErrorHandler(t -> {
            log.warn("Redis Pub/Sub error occurred: {}", t.getMessage());
        });

        // 设置恢复间隔，当连接失败时会自动重试
        container.setRecoveryInterval(5000L);

        return container;
    }
}
