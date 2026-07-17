package io.github.surezzzzzz.sdk.retry.redis.smart.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import io.github.surezzzzzz.sdk.retry.redis.smart.SmartRedisRetryPackage;
import io.github.surezzzzzz.sdk.retry.redis.smart.annotation.SmartRedisRetryComponent;
import io.github.surezzzzzz.sdk.retry.redis.smart.clock.RetryClock;
import io.github.surezzzzzz.sdk.retry.redis.smart.clock.SystemRetryClock;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.RedisFailureStrategy;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.SmartRedisRetryConstant;
import io.github.surezzzzzz.sdk.retry.redis.smart.engine.DefaultSmartRedisRetryEngine;
import io.github.surezzzzzz.sdk.retry.redis.smart.engine.SmartRedisRetryEngine;
import io.github.surezzzzzz.sdk.retry.redis.smart.exception.RetryValidationException;
import io.github.surezzzzzz.sdk.retry.redis.smart.listener.NoopSmartRedisRetryListener;
import io.github.surezzzzzz.sdk.retry.redis.smart.listener.SmartRedisRetryListener;
import io.github.surezzzzzz.sdk.retry.redis.smart.policy.DefaultRetryPolicyResolver;
import io.github.surezzzzzz.sdk.retry.redis.smart.policy.RetryPolicyResolver;
import io.github.surezzzzzz.sdk.retry.redis.smart.script.LuaRedisRetryScriptExecutor;
import io.github.surezzzzzz.sdk.retry.redis.smart.script.RedisRetryScriptExecutor;
import io.github.surezzzzzz.sdk.retry.redis.smart.serializer.JacksonRetryContextSerializer;
import io.github.surezzzzzz.sdk.retry.redis.smart.serializer.RetryContextSerializer;
import io.github.surezzzzzz.sdk.retry.redis.smart.support.RetryInfoConvertHelper;
import io.github.surezzzzzz.sdk.retry.redis.smart.support.RetryKeyHelper;
import io.github.surezzzzzz.sdk.retry.redis.smart.validator.*;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * Smart Redis Retry 自动配置
 *
 * @author surezzzzzz
 */
@Configuration
@AutoConfigureAfter(name = SmartRedisRetryConstant.REDIS_ROUTE_AUTO_CONFIGURATION_CLASS_NAME)
@ConditionalOnClass({RedisRouteTemplate.class, ObjectMapper.class})
@EnableConfigurationProperties(SmartRedisRetryProperties.class)
@ComponentScan(
        basePackageClasses = SmartRedisRetryPackage.class,
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(SmartRedisRetryComponent.class)
)
@ConditionalOnProperty(prefix = SmartRedisRetryConstant.CONFIG_PREFIX,
        name = SmartRedisRetryConstant.PROPERTY_ENABLE,
        havingValue = SmartRedisRetryConstant.PROPERTY_TRUE,
        matchIfMissing = true)
public class SmartRedisRetryAutoConfiguration {

    /**
     * 注册系统时钟。
     *
     * @return 系统时钟
     */
    @Bean
    @ConditionalOnMissingBean
    public RetryClock retryClock() {
        return new SystemRetryClock();
    }

    /**
     * 注册默认空监听器。
     *
     * @return 默认重试监听器
     */
    @Bean
    @ConditionalOnMissingBean
    public SmartRedisRetryListener smartRedisRetryListener() {
        return new NoopSmartRedisRetryListener();
    }

    /**
     * 注册默认重试策略解析器。
     *
     * @param properties Smart Redis Retry 配置
     * @return 重试策略解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public RetryPolicyResolver retryPolicyResolver(SmartRedisRetryProperties properties) {
        return new DefaultRetryPolicyResolver(properties);
    }

    /**
     * 注册 Jackson 上下文序列化器。
     *
     * @return 重试上下文序列化器
     */
    @Bean
    @ConditionalOnMissingBean
    public RetryContextSerializer retryContextSerializer() {
        return new JacksonRetryContextSerializer(new ObjectMapper());
    }

    /**
     * 注册重试失败请求校验器。
     *
     * @param properties           Smart Redis Retry 配置
     * @param retryPolicyValidator 重试策略校验器
     * @return 重试失败请求校验器
     */
    @Bean
    @ConditionalOnMissingBean
    public RetryFailureValidator retryFailureValidator(SmartRedisRetryProperties properties,
                                                       RetryPolicyValidator retryPolicyValidator) {
        return new RetryFailureValidator(properties, retryPolicyValidator);
    }

    /**
     * 注册重试上下文校验器。
     *
     * @param properties Smart Redis Retry 配置
     * @param serializer 重试上下文序列化器
     * @return 重试上下文校验器
     */
    @Bean
    @ConditionalOnMissingBean
    public RetryContextValidator retryContextValidator(SmartRedisRetryProperties properties,
                                                       RetryContextSerializer serializer) {
        return new RetryContextValidator(properties, serializer);
    }

    /**
     * 注册扫描请求校验器。
     *
     * @param properties Smart Redis Retry 配置
     * @return 扫描请求校验器
     */
    @Bean
    @ConditionalOnMissingBean
    public RetryScanRequestValidator retryScanRequestValidator(SmartRedisRetryProperties properties) {
        return new RetryScanRequestValidator(properties);
    }

    /**
     * 注册请求校验链。
     *
     * @param validators 全部请求校验器
     * @return 请求校验链
     */
    @Bean
    @ConditionalOnMissingBean
    public RetryRequestValidatorChain retryRequestValidatorChain(List<RetryRequestValidator<?>> validators) {
        return new RetryRequestValidatorChain(validators);
    }

    /**
     * 注册 Redis Key 构建器。
     *
     * @param properties Smart Redis Retry 配置
     * @return Redis Key 构建器
     */
    @Bean
    @ConditionalOnMissingBean
    public RetryKeyHelper retryKeyHelper(SmartRedisRetryProperties properties) {
        return new RetryKeyHelper(properties);
    }

    /**
     * 注册 Lua 脚本执行器。
     *
     * @param serializer             重试上下文序列化器
     * @param retryInfoConvertHelper 重试状态转换器
     * @return Redis 重试脚本执行器
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisRetryScriptExecutor redisRetryScriptExecutor(RetryContextSerializer serializer,
                                                             RetryInfoConvertHelper retryInfoConvertHelper) {
        return new LuaRedisRetryScriptExecutor(serializer, retryInfoConvertHelper);
    }

    /**
     * 注册 Smart Redis Retry 引擎。
     *
     * @param redisRouteTemplate     Redis 路由模板
     * @param properties             Smart Redis Retry 配置
     * @param retryPolicyResolver    重试策略解析器
     * @param listener               重试监听器
     * @param retryClock             重试时钟
     * @param retryKeyHelper         Redis Key 构建器
     * @param retryInfoConvertHelper 重试状态转换器
     * @param retryContextSerializer 重试上下文序列化器
     * @param validatorChain         请求校验链
     * @param retryPolicyValidator   重试策略校验器
     * @param scriptExecutor         Redis 重试脚本执行器
     * @return Smart Redis Retry 引擎
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RedisRouteTemplate.class)
    public SmartRedisRetryEngine smartRedisRetryEngine(RedisRouteTemplate redisRouteTemplate,
                                                       SmartRedisRetryProperties properties,
                                                       RetryPolicyResolver retryPolicyResolver,
                                                       SmartRedisRetryListener listener,
                                                       RetryClock retryClock,
                                                       RetryKeyHelper retryKeyHelper,
                                                       RetryInfoConvertHelper retryInfoConvertHelper,
                                                       RetryContextSerializer retryContextSerializer,
                                                       RetryRequestValidatorChain validatorChain,
                                                       RetryPolicyValidator retryPolicyValidator,
                                                       RedisRetryScriptExecutor scriptExecutor) {
        validateProperties(properties, retryPolicyValidator);
        return new DefaultSmartRedisRetryEngine(redisRouteTemplate, properties, retryPolicyResolver, listener,
                retryClock, retryKeyHelper, retryInfoConvertHelper, retryContextSerializer,
                validatorChain, retryPolicyValidator, scriptExecutor);
    }

    private void validateProperties(SmartRedisRetryProperties properties, RetryPolicyValidator retryPolicyValidator) {
        if (!RedisFailureStrategy.isValid(properties.getGuard().getRedisFailureStrategy())) {
            throw new RetryValidationException(ErrorCode.REDIS_FAILURE_STRATEGY_INVALID,
                    String.format(ErrorMessage.REDIS_FAILURE_STRATEGY_INVALID,
                            Arrays.toString(RedisFailureStrategy.getAllCodes())));
        }
        long recordTtlSeconds = properties.getRedis().getRecordTtlSeconds();
        if (recordTtlSeconds <= 0L) {
            throw new RetryValidationException(ErrorCode.RECORD_TTL_INVALID,
                    ErrorMessage.RECORD_TTL_INVALID);
        }
        if (recordTtlSeconds > SmartRedisRetryConstant.MAX_RECORD_TTL_SECONDS) {
            throw new RetryValidationException(ErrorCode.RECORD_TTL_TOO_LARGE,
                    ErrorMessage.RECORD_TTL_TOO_LARGE);
        }
        if (properties.getRedis().getScanCount() <= SmartRedisRetryConstant.ARRAY_INITIAL_INDEX) {
            throw new RetryValidationException(ErrorCode.SCAN_COUNT_INVALID, ErrorMessage.SCAN_COUNT_INVALID);
        }
        if (properties.getGuard().getMaxRetryKeyLength() <= SmartRedisRetryConstant.ARRAY_INITIAL_INDEX) {
            throw new RetryValidationException(ErrorCode.MAX_RETRY_KEY_LENGTH_INVALID,
                    ErrorMessage.MAX_RETRY_KEY_LENGTH_INVALID);
        }
        if (properties.getGuard().getMaxContextJsonLength() <= SmartRedisRetryConstant.ARRAY_INITIAL_INDEX) {
            throw new RetryValidationException(ErrorCode.MAX_CONTEXT_JSON_LENGTH_INVALID,
                    ErrorMessage.MAX_CONTEXT_JSON_LENGTH_INVALID);
        }
        retryPolicyValidator.validate(properties.getPolicy().getDefaultPolicy());
        for (io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryPolicy scenePolicy
                : properties.getPolicy().getScene().values()) {
            retryPolicyValidator.validate(scenePolicy);
        }
    }
}
