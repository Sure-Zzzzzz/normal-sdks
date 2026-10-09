package io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration;

import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterDirectoryProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.SmartRedisLimiterManagementPackage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.annotation.SmartRedisLimiterManagementComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.directory.ConfigurationSmartRedisLimiterDirectoryProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.event.AfterCommitSmartRedisLimiterManagementEventPublisher;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.event.AfterCommitTypedSmartRedisLimiterManagementEventPublisher;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.event.SmartRedisLimiterManagementEventPublisher;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.repository.JdbcSmartRedisLimiterPolicyRepository;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.repository.JdbcSmartRedisLimiterTypedRuleRepository;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.repository.SmartRedisLimiterPolicyRepository;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.repository.SmartRedisLimiterTypedRuleRepository;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.security.SecurityContextSmartRedisLimiterManagementOperatorProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.security.SmartRedisLimiterManagementOperatorProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.service.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * SmartRedisLimiter Management 自动配置
 *
 * @author surezzzzzz
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SmartRedisLimiterManagementProperties.class)
@ConditionalOnProperty(
        prefix = SmartRedisLimiterManagementConstant.CONFIG_PREFIX,
        name = SmartRedisLimiterManagementConstant.CONFIG_FIELD_ENABLE,
        havingValue = "true")
@ComponentScan(
        basePackageClasses = SmartRedisLimiterManagementPackage.class,
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ANNOTATION,
                classes = SmartRedisLimiterManagementComponent.class),
        useDefaultFilters = false)
@Import({
        SmartRedisLimiterManagementConfigurationValidator.class,
        SmartRedisLimiterManagementApiSecurityConfiguration.class,
        SmartRedisLimiterManagementSecurityConfiguration.class,
        SmartRedisLimiterManagementRestSecurityConfiguration.class,
        SmartRedisLimiterManagementWebMvcConfiguration.class,
        SmartRedisLimiterManagementPortalConfiguration.class
})
public class SmartRedisLimiterManagementAutoConfiguration {

    /**
     * 创建默认密码编码器
     */
    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder smartRedisLimiterManagementPasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * 创建默认 JDBC Repository
     */
    @Bean
    @ConditionalOnMissingBean(SmartRedisLimiterPolicyRepository.class)
    public SmartRedisLimiterPolicyRepository smartRedisLimiterPolicyRepository(
            NamedParameterJdbcTemplate jdbcTemplate) {
        return new JdbcSmartRedisLimiterPolicyRepository(jdbcTemplate);
    }

    /**
     * 创建 commit 后事件发布器
     */
    @Bean
    @ConditionalOnMissingBean(SmartRedisLimiterManagementEventPublisher.class)
    public SmartRedisLimiterManagementEventPublisher smartRedisLimiterManagementEventPublisher(
            ApplicationEventPublisher applicationEventPublisher) {
        return new AfterCommitSmartRedisLimiterManagementEventPublisher(applicationEventPublisher);
    }

    /**
     * 创建策略管理服务
     */
    @Bean
    @ConditionalOnMissingBean(SmartRedisLimiterPolicyManagementService.class)
    public SmartRedisLimiterPolicyManagementService smartRedisLimiterPolicyManagementService(
            SmartRedisLimiterPolicyRepository repository,
            SmartRedisLimiterManagementEventPublisher eventPublisher,
            SmartRedisLimiterManagementProperties properties) {
        return new DefaultSmartRedisLimiterPolicyManagementService(
                repository, eventPublisher, properties);
    }

    /**
     * 创建快照服务
     */
    @Bean
    @ConditionalOnMissingBean(SmartRedisLimiterPolicySnapshotService.class)
    public SmartRedisLimiterPolicySnapshotService smartRedisLimiterPolicySnapshotService(
            SmartRedisLimiterPolicyRepository repository,
            SmartRedisLimiterManagementProperties properties) {
        return new DefaultSmartRedisLimiterPolicySnapshotService(repository, properties);
    }

    /**
     * 创建默认操作人 Provider
     */
    @Bean
    @ConditionalOnMissingBean(SmartRedisLimiterManagementOperatorProvider.class)
    public SmartRedisLimiterManagementOperatorProvider smartRedisLimiterManagementOperatorProvider(
            SmartRedisLimiterManagementProperties properties) {
        return new SecurityContextSmartRedisLimiterManagementOperatorProvider(properties);
    }

    /**
     * 创建 v2 类型化规则 Repository
     */
    @Bean
    @ConditionalOnMissingBean(SmartRedisLimiterTypedRuleRepository.class)
    public SmartRedisLimiterTypedRuleRepository smartRedisLimiterTypedRuleRepository(
            NamedParameterJdbcTemplate jdbcTemplate) {
        return new JdbcSmartRedisLimiterTypedRuleRepository(jdbcTemplate);
    }

    /**
     * 创建配置式目录提供方（宿主自有目录实现时以自有 Bean 覆盖）
     */
    @Bean
    @ConditionalOnMissingBean(SmartRedisLimiterDirectoryProvider.class)
    public SmartRedisLimiterDirectoryProvider smartRedisLimiterDirectoryProvider(
            SmartRedisLimiterManagementProperties properties) {
        return new ConfigurationSmartRedisLimiterDirectoryProvider(properties);
    }

    /**
     * 创建类型化事件发布器
     */
    @Bean
    @ConditionalOnMissingBean(DefaultSmartRedisLimiterTypedPolicyManagementService.TypedEventPublisher.class)
    public DefaultSmartRedisLimiterTypedPolicyManagementService.TypedEventPublisher
    smartRedisLimiterTypedEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new AfterCommitTypedSmartRedisLimiterManagementEventPublisher(applicationEventPublisher);
    }

    /**
     * 创建 v2 类型化规则管理服务
     */
    @Bean
    @ConditionalOnMissingBean(SmartRedisLimiterTypedPolicyManagementService.class)
    public SmartRedisLimiterTypedPolicyManagementService smartRedisLimiterTypedPolicyManagementService(
            SmartRedisLimiterTypedRuleRepository repository,
            SmartRedisLimiterPolicyRepository policyRepository,
            SmartRedisLimiterDirectoryProvider directoryProvider,
            DefaultSmartRedisLimiterTypedPolicyManagementService.TypedEventPublisher eventPublisher,
            SmartRedisLimiterManagementProperties properties) {
        return new DefaultSmartRedisLimiterTypedPolicyManagementService(
                repository, policyRepository, directoryProvider, eventPublisher,
                properties.getPage().getDefaultSize(), properties.getPage().getMaxSize());
    }
}
