package io.github.surezzzzzz.sdk.cache.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.surezzzzzz.sdk.cache.SmartCachePackage;
import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheComponent;
import io.github.surezzzzzz.sdk.cache.constant.ErrorCode;
import io.github.surezzzzzz.sdk.cache.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import io.github.surezzzzzz.sdk.cache.exception.CacheConfigurationException;
import io.github.surezzzzzz.sdk.cache.layer.L2Cache;
import io.github.surezzzzzz.sdk.cache.pubsub.CacheInvalidationListener;
import io.github.surezzzzzz.sdk.cache.serializer.JacksonSmartCacheSerializer;
import io.github.surezzzzzz.sdk.cache.serializer.PackageSmartCacheTypeValidator;
import io.github.surezzzzzz.sdk.cache.serializer.SmartCacheSerializer;
import io.github.surezzzzzz.sdk.cache.serializer.SmartCacheTypeValidator;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Smart Cache 自动配置
 *
 * @author surezzzzzz
 */
@Configuration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(SmartCacheProperties.class)
@AutoConfigureAfter(name = {
        SmartCacheConstant.REDIS_ROUTE_CONFIGURATION_CLASS_NAME,
        SmartCacheConstant.SIMPLE_REDIS_LOCK_CONFIGURATION_CLASS_NAME
})
@ConditionalOnProperty(prefix = SmartCacheConstant.CONFIG_PREFIX, name = SmartCacheConstant.PROPERTY_ENABLED,
        havingValue = SmartCacheConstant.PROPERTY_VALUE_TRUE, matchIfMissing = true)
@ComponentScan(
        basePackageClasses = SmartCachePackage.class,
        includeFilters = @ComponentScan.Filter(SmartCacheComponent.class),
        useDefaultFilters = false
)
public class SmartCacheConfiguration {

    @Bean(name = SmartCacheConstant.SMART_CACHE_OBJECT_MAPPER_BEAN_NAME)
    @ConditionalOnMissingBean(name = SmartCacheConstant.SMART_CACHE_OBJECT_MAPPER_BEAN_NAME)
    public ObjectMapper smartCacheObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

    @Bean
    @ConditionalOnMissingBean
    public SmartCacheTypeValidator smartCacheTypeValidator(SmartCacheProperties properties) {
        return new PackageSmartCacheTypeValidator(properties.getSerializer().getTrustedPackages());
    }

    @Bean
    @ConditionalOnMissingBean
    public SmartCacheSerializer smartCacheSerializer(
            @org.springframework.beans.factory.annotation.Qualifier(SmartCacheConstant.SMART_CACHE_OBJECT_MAPPER_BEAN_NAME)
            ObjectMapper smartCacheObjectMapper,
            SmartCacheTypeValidator smartCacheTypeValidator) {
        return new JacksonSmartCacheSerializer(smartCacheObjectMapper, smartCacheTypeValidator);
    }

    @Bean
    public org.springframework.beans.factory.InitializingBean smartCacheRouteConfigurationValidator(
            SmartCacheProperties properties,
            org.springframework.beans.factory.ObjectProvider<RedisRouteTemplate> redisRouteTemplateProvider) {
        return () -> {
            if (redisRouteTemplateProvider.getIfAvailable() != null) {
                return;
            }
            if (properties.getL2().isEnabled()) {
                throw new CacheConfigurationException(
                        ErrorCode.SMART_CACHE_ROUTE_MISSING,
                        ErrorMessage.SMART_CACHE_ROUTE_MISSING
                );
            }
            if (SmartCacheConstant.CONSISTENCY_MODE_STRONG.equals(properties.getConsistency().getMode())) {
                throw new CacheConfigurationException(
                        ErrorCode.SMART_CACHE_ROUTE_MISSING,
                        ErrorMessage.SMART_CACHE_STRONG_CONSISTENCY_ROUTE_MISSING
                );
            }
        };
    }

    @Bean(name = SmartCacheConstant.SMART_CACHE_PRELOAD_EXECUTOR_BEAN_NAME)
    @ConditionalOnMissingBean(name = SmartCacheConstant.SMART_CACHE_PRELOAD_EXECUTOR_BEAN_NAME)
    public ThreadPoolExecutor smartCachePreloadExecutor(SmartCacheProperties properties) {
        SmartCacheProperties.L2Config.PreloadConfig preload = properties.getL2().getPreload();
        return createExecutor(preload.getExecutorThreads(), preload.getExecutorQueueCapacity(),
                SmartCacheConstant.PRELOAD_THREAD_NAME_PREFIX);
    }

    @Bean(name = SmartCacheConstant.SMART_CACHE_WARMUP_EXECUTOR_BEAN_NAME)
    @ConditionalOnMissingBean(name = SmartCacheConstant.SMART_CACHE_WARMUP_EXECUTOR_BEAN_NAME)
    public ThreadPoolExecutor smartCacheWarmUpExecutor(SmartCacheProperties properties) {
        SmartCacheProperties.WarmUpConfig warmUp = properties.getWarmUp();
        return createExecutor(warmUp.getExecutorThreads(), warmUp.getExecutorQueueCapacity(),
                SmartCacheConstant.WARMUP_THREAD_NAME_PREFIX);
    }

    @Bean
    @ConditionalOnBean(RedisRouteTemplate.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = SmartCacheConstant.CONFIG_PREFIX + ".l2", name = SmartCacheConstant.PROPERTY_ENABLED,
            havingValue = SmartCacheConstant.PROPERTY_VALUE_TRUE, matchIfMissing = true)
    public L2Cache l2Cache(SmartCacheProperties properties,
                           RedisRouteTemplate redisRouteTemplate,
                           SmartCacheSerializer smartCacheSerializer) {
        return new L2Cache(properties, redisRouteTemplate, smartCacheSerializer);
    }

    @Bean
    @ConditionalOnBean(RedisRouteTemplate.class)
    @ConditionalOnMissingBean
    public CacheInvalidationListener cacheInvalidationListener() {
        return new CacheInvalidationListener();
    }

    private ThreadPoolExecutor createExecutor(int threadCount, int queueCapacity, String threadNamePrefix) {
        AtomicInteger threadCounter = new AtomicInteger(0);
        return new ThreadPoolExecutor(
                threadCount,
                threadCount,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, threadNamePrefix + threadCounter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
