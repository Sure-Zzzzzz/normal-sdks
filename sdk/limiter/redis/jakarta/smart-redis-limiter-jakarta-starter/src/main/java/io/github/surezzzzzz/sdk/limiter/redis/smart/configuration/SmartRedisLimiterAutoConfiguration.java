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
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
            ObjectProvider<SmartRedisLimiterPolicyResolver> policyResolver,
            ObjectProvider<io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedExecutionEngine> typedEngine,
            ObjectProvider<io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedFactProvider> typedFactProviders,
            ObjectProvider<io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedPolicySnapshotStore> typedSnapshotStore,
            ObjectProvider<io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedGateDeclaration> typedGateDeclarations) {
        boolean typedEnabled = properties.getTyped() != null
                && Boolean.TRUE.equals(properties.getTyped().getEnabled());
        if (!typedEnabled) {
            return new SmartRedisLimiterExecutionCoordinator(
                    properties, algorithmFactory, snapshotStore, policyResolver);
        }
        return new SmartRedisLimiterExecutionCoordinator(
                properties,
                algorithmFactory,
                snapshotStore,
                policyResolver,
                typedEngine.getIfAvailable(),
                typedFactProviders.orderedStream()
                        .collect(java.util.stream.Collectors.toList()),
                typedSnapshotStore.getIfAvailable(),
                typedGateDeclarations.orderedStream()
                        .collect(java.util.stream.Collectors.toList()));
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
     * 类型化多维门禁装配：typed.enabled=true 时生效。
     * 门禁声明由配置构建（已过 PostConstruct 校验）；身份维度（USER/SERVICE/CREDENTIAL/CUSTOMER/CUSTOM）
     * 缺事实提供方 Bean 时启动响亮失败（缺 SPI 启动失败，不静默跳过）。
     */
    @Configuration
    @ConditionalOnProperty(
            prefix = io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant.CONFIG_PREFIX + ".typed",
            name = "enabled",
            havingValue = "true"
    )
    public static class TypedGateConfiguration {

        /**
         * 创建门禁声明列表（配置驱动，维度查重在配置校验完成）
         *
         * @param properties 限流器配置
         * @return 门禁声明列表
         */
        @Bean
        @ConditionalOnMissingBean(io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedGateDeclaration.class)
        public java.util.List<io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedGateDeclaration> smartRedisLimiterTypedGateDeclarations(
                SmartRedisLimiterProperties properties) {
            java.util.List<io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedGateDeclaration> declarations =
                    new java.util.ArrayList<>();
            for (SmartRedisLimiterProperties.TypedGateConfig gate : properties.getTyped().getGates()) {
                declarations.add(new io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedGateDeclaration(
                        io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterDataDimension
                                .fromCode(gate.getDimension()),
                        gate.getNamespace(), gate.getCustomType(), gate.getLimits()));
            }
            return declarations;
        }

        /**
         * 创建类型化快照存储
         *
         * @return 类型化快照存储
         */
        @Bean
        @ConditionalOnMissingBean(io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedPolicySnapshotStore.class)
        public io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedPolicySnapshotStore smartRedisLimiterTypedPolicySnapshotStore() {
            return new io.github.surezzzzzz.sdk.limiter.redis.smart.typed.AtomicSmartRedisLimiterTypedPolicySnapshotStore();
        }

        /**
         * 创建类型化执行引擎
         *
         * @param properties       限流器配置
         * @param algorithmFactory 算法工厂
         * @return 类型化执行引擎
         */
        @Bean
        @ConditionalOnMissingBean(io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedExecutionEngine.class)
        public io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedExecutionEngine smartRedisLimiterTypedExecutionEngine(
                SmartRedisLimiterProperties properties,
                SmartRedisLimiterAlgorithmFactory algorithmFactory) {
            return new io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedExecutionEngine(
                    properties, algorithmFactory);
        }

        /**
         * 创建类型化刷新管理器（有策略客户端时；散形态无 client 制品则不装配，走本地声明）
         *
         * @param properties         限流器配置
         * @param managementClient   策略客户端（可选）
         * @param typedSnapshotStore 类型化快照存储
         * @return 类型化刷新管理器
         */
        @Bean
        @ConditionalOnMissingBean(io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedPolicyRefreshManager.class)
        @ConditionalOnBean(io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.SmartRedisLimiterManagementClient.class)
        public io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedPolicyRefreshManager smartRedisLimiterTypedPolicyRefreshManager(
                SmartRedisLimiterProperties properties,
                io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.SmartRedisLimiterManagementClient managementClient,
                io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedPolicySnapshotStore typedSnapshotStore) {
            return new io.github.surezzzzzz.sdk.limiter.redis.smart.typed.DefaultSmartRedisLimiterTypedPolicyRefreshManager(
                    properties, managementClient, typedSnapshotStore);
        }
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
         * 创建远程策略刷新管理器（策略客户端由 management client 传输件提供；
         * 类路径无 client 制品的散形态在此响亮失败，不静默退回本地）
         *
         * @param properties        限流器配置
         * @param policyClient      策略客户端（ObjectProvider：由 client 传输件装配）
         * @param snapshotValidator 快照校验器
         * @param snapshotStore     快照存储
         * @return 刷新管理器
         */
        @Bean
        @ConditionalOnMissingBean(SmartRedisLimiterPolicyRefreshManager.class)
        public SmartRedisLimiterPolicyRefreshManager smartRedisLimiterPolicyRefreshManager(
                SmartRedisLimiterProperties properties,
                ObjectProvider<io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.SmartRedisLimiterManagementClient> policyClient,
                SmartRedisLimiterPolicySnapshotValidator snapshotValidator,
                SmartRedisLimiterPolicySnapshotStore snapshotStore) {
            io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.SmartRedisLimiterManagementClient client =
                    policyClient.getIfAvailable();
            if (client == null) {
                throw new io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterConfigurationException(
                        io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorCode.CONFIG_VALIDATION_FAILED,
                        String.format(io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorMessage.CONFIG_VALIDATION_FAILED,
                                "remote-policy.enable=true 需要 management client 传输制品提供策略客户端；"
                                        + "散形态请关闭 remote-policy 并仅使用本地限额"));
            }
            return new DefaultSmartRedisLimiterPolicyRefreshManager(
                    properties, client, snapshotValidator, snapshotStore);
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
