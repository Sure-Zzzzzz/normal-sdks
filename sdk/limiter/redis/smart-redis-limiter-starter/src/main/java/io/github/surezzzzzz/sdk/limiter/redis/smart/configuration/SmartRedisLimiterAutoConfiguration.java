package io.github.surezzzzzz.sdk.limiter.redis.smart.configuration;

import io.github.surezzzzzz.sdk.limiter.redis.smart.SmartRedisLimiterPackage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterAlgorithmFactory;
import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiterComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterConfigurationException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.execution.SmartRedisLimiterExecutionCoordinator;
import io.github.surezzzzzz.sdk.limiter.redis.smart.executor.RouteSmartRedisLimiterRedisExecutor;
import io.github.surezzzzzz.sdk.limiter.redis.smart.executor.SmartRedisLimiterRedisExecutor;
import io.github.surezzzzzz.sdk.limiter.redis.smart.executor.SmartRedisLimiterTimeoutExecutor;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.*;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.client.HttpSmartRedisLimiterPolicyClient;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.client.SmartRedisLimiterPolicyClient;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.json.JacksonSmartRedisLimiterPolicyJsonCodec;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.json.SmartRedisLimiterPolicyJsonCodec;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.util.ClassUtils;

import javax.annotation.PostConstruct;

/**
 * 智能 Redis 限流器自动配置
 *
 * @author surezzzzzz
 */
@Configuration
@EnableConfigurationProperties(SmartRedisLimiterProperties.class)
@ConditionalOnProperty(
        prefix = SmartRedisLimiterConstant.CONFIG_PREFIX,
        name = "enable",
        havingValue = "true"
)
@AutoConfigureAfter(name = SmartRedisLimiterConstant.REDIS_ROUTE_CONFIGURATION_CLASS_NAME)
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
     * 检查 Redis Route class 与 Bean 是否完整存在
     *
     * @param applicationContext Spring 上下文
     * @return 依赖检查标记
     */
    @Bean
    public SmartRedisLimiterRouteDependencyChecker smartRedisLimiterRouteDependencyChecker(
            ApplicationContext applicationContext) {
        ClassLoader classLoader = applicationContext.getClassLoader();
        if (!ClassUtils.isPresent(SmartRedisLimiterConstant.REDIS_ROUTE_TEMPLATE_CLASS_NAME, classLoader)) {
            throw routeDependencyException(null);
        }
        try {
            Class<?> redisRouteTemplateClass = ClassUtils.forName(
                    SmartRedisLimiterConstant.REDIS_ROUTE_TEMPLATE_CLASS_NAME, classLoader);
            String[] beanNames = applicationContext.getBeanNamesForType(redisRouteTemplateClass, false, false);
            if (beanNames.length == 0) {
                throw routeDependencyException(null);
            }
        } catch (ClassNotFoundException e) {
            throw routeDependencyException(e);
        }
        return new SmartRedisLimiterRouteDependencyChecker();
    }

    /**
     * 创建统一超时保护执行器
     *
     * @param properties 限流配置
     * @return 超时保护执行器
     */
    @Bean
    @ConditionalOnMissingBean(SmartRedisLimiterTimeoutExecutor.class)
    public SmartRedisLimiterTimeoutExecutor smartRedisLimiterTimeoutExecutor(SmartRedisLimiterProperties properties) {
        return new SmartRedisLimiterTimeoutExecutor(properties);
    }

    /**
     * 创建 Aspect 与 Interceptor 共用的请求执行协调器
     *
     * @param properties       限流器配置
     * @param algorithmFactory 算法工厂
     * @param snapshotStore    可选远程快照存储
     * @param policyResolver   可选远程策略解析器
     * @return 请求执行协调器
     */
    @Bean
    @ConditionalOnMissingBean(SmartRedisLimiterExecutionCoordinator.class)
    public SmartRedisLimiterExecutionCoordinator smartRedisLimiterExecutionCoordinator(
            SmartRedisLimiterProperties properties,
            SmartRedisLimiterAlgorithmFactory algorithmFactory,
            ObjectProvider<SmartRedisLimiterPolicySnapshotStore> snapshotStore,
            ObjectProvider<SmartRedisLimiterPolicyResolver> policyResolver) {
        return new SmartRedisLimiterExecutionCoordinator(
                properties,
                algorithmFactory,
                snapshotStore,
                policyResolver);
    }

    private SmartRedisLimiterConfigurationException routeDependencyException(Throwable cause) {
        if (cause == null) {
            return new SmartRedisLimiterConfigurationException(
                    ErrorCode.CONFIG_REDIS_ROUTE_TEMPLATE_MISSING,
                    ErrorMessage.CONFIG_REDIS_ROUTE_TEMPLATE_MISSING);
        }
        return new SmartRedisLimiterConfigurationException(
                ErrorCode.CONFIG_REDIS_ROUTE_TEMPLATE_MISSING,
                ErrorMessage.CONFIG_REDIS_ROUTE_TEMPLATE_MISSING,
                cause);
    }

    /**
     * 远程动态策略配置，仅在远程策略开关开启时创建网络与调度资源
     */
    @Configuration
    @ConditionalOnProperty(
            prefix = SmartRedisLimiterStarterConstant.CONFIG_PREFIX_REMOTE_POLICY,
            name = SmartRedisLimiterStarterConstant.CONFIG_FIELD_ENABLE,
            havingValue = "true"
    )
    public static class RemotePolicyConfiguration {

        /**
         * 创建 SDK 独立 JSON 编解码器
         *
         * @return JSON 编解码器
         */
        @Bean
        @ConditionalOnMissingBean(SmartRedisLimiterPolicyJsonCodec.class)
        public SmartRedisLimiterPolicyJsonCodec smartRedisLimiterPolicyJsonCodec() {
            return new JacksonSmartRedisLimiterPolicyJsonCodec();
        }

        /**
         * 创建远程策略 HTTP 客户端
         *
         * @param properties 限流器配置
         * @param jsonCodec  JSON 编解码器
         * @return 远程策略客户端
         */
        @Bean
        @ConditionalOnMissingBean(SmartRedisLimiterPolicyClient.class)
        public SmartRedisLimiterPolicyClient smartRedisLimiterPolicyClient(
                SmartRedisLimiterProperties properties,
                SmartRedisLimiterPolicyJsonCodec jsonCodec) {
            return new HttpSmartRedisLimiterPolicyClient(properties, jsonCodec);
        }

        /**
         * 创建快照校验器
         *
         * @param properties 限流器配置
         * @return 快照校验器
         */
        @Bean
        @ConditionalOnMissingBean(SmartRedisLimiterPolicySnapshotValidator.class)
        public SmartRedisLimiterPolicySnapshotValidator smartRedisLimiterPolicySnapshotValidator(
                SmartRedisLimiterProperties properties) {
            return new DefaultSmartRedisLimiterPolicySnapshotValidator(properties);
        }

        /**
         * 创建原子快照存储
         *
         * @return 快照存储
         */
        @Bean
        @ConditionalOnMissingBean(SmartRedisLimiterPolicySnapshotStore.class)
        public SmartRedisLimiterPolicySnapshotStore smartRedisLimiterPolicySnapshotStore() {
            return new AtomicSmartRedisLimiterPolicySnapshotStore();
        }

        /**
         * 创建默认策略解析器
         *
         * @return 策略解析器
         */
        @Bean
        @ConditionalOnMissingBean(SmartRedisLimiterPolicyResolver.class)
        public SmartRedisLimiterPolicyResolver smartRedisLimiterPolicyResolver() {
            return new DefaultSmartRedisLimiterPolicyResolver();
        }

        /**
         * 创建远程策略刷新管理器
         *
         * @param properties        限流器配置
         * @param policyClient      远程策略客户端
         * @param snapshotValidator 快照校验器
         * @param snapshotStore     快照存储
         * @return 刷新管理器
         */
        @Bean
        @ConditionalOnMissingBean(SmartRedisLimiterPolicyRefreshManager.class)
        public SmartRedisLimiterPolicyRefreshManager smartRedisLimiterPolicyRefreshManager(
                SmartRedisLimiterProperties properties,
                SmartRedisLimiterPolicyClient policyClient,
                SmartRedisLimiterPolicySnapshotValidator snapshotValidator,
                SmartRedisLimiterPolicySnapshotStore snapshotStore) {
            return new DefaultSmartRedisLimiterPolicyRefreshManager(
                    properties, policyClient, snapshotValidator, snapshotStore);
        }

        /**
         * 暴露刷新状态提供接口
         *
         * @param refreshManager 刷新管理器
         * @return 刷新状态提供接口
         */
        @Bean
        @ConditionalOnMissingBean(SmartRedisLimiterPolicyRefreshStateProvider.class)
        public SmartRedisLimiterPolicyRefreshStateProvider smartRedisLimiterPolicyRefreshStateProvider(
                SmartRedisLimiterPolicyRefreshManager refreshManager) {
            return refreshManager;
        }
    }

    /**
     * Redis Route 类型相关配置，仅在 route class 存在时加载
     */
    @Configuration
    @ConditionalOnClass(name = SmartRedisLimiterConstant.REDIS_ROUTE_TEMPLATE_CLASS_NAME)
    public static class RedisRouteExecutorConfiguration {

        /**
         * 创建 Redis Route 原生执行器
         *
         * @param redisRouteTemplateProvider Redis Route 门面
         * @param properties                 限流配置
         * @return Redis 执行器
         */
        @Bean
        @ConditionalOnMissingBean(SmartRedisLimiterRedisExecutor.class)
        public SmartRedisLimiterRedisExecutor smartRedisLimiterRedisExecutor(
                ObjectProvider<RedisRouteTemplate> redisRouteTemplateProvider,
                SmartRedisLimiterProperties properties) {
            RedisRouteTemplate redisRouteTemplate = redisRouteTemplateProvider.getIfAvailable();
            if (redisRouteTemplate == null) {
                throw new SmartRedisLimiterConfigurationException(
                        ErrorCode.CONFIG_REDIS_ROUTE_TEMPLATE_MISSING,
                        ErrorMessage.CONFIG_REDIS_ROUTE_TEMPLATE_MISSING);
            }
            return new RouteSmartRedisLimiterRedisExecutor(redisRouteTemplate, properties);
        }
    }
}
