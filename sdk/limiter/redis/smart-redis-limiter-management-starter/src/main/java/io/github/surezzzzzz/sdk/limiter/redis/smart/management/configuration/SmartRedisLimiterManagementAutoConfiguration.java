package io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.SmartRedisLimiterManagementPackage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.annotation.SmartRedisLimiterManagementComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.event.AfterCommitSmartRedisLimiterManagementEventPublisher;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.event.SmartRedisLimiterManagementEventPublisher;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.repository.JdbcSmartRedisLimiterPolicyRepository;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.repository.SmartRedisLimiterPolicyRepository;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.security.SecurityContextSmartRedisLimiterManagementOperatorProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.security.SmartRedisLimiterManagementOperatorProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.service.DefaultSmartRedisLimiterPolicyManagementService;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.service.DefaultSmartRedisLimiterPolicySnapshotService;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.service.SmartRedisLimiterPolicyManagementService;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.service.SmartRedisLimiterPolicySnapshotService;
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
        SmartRedisLimiterManagementWebMvcConfiguration.class
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
            SmartRedisLimiterPolicyRepository repository) {
        return new DefaultSmartRedisLimiterPolicySnapshotService(repository);
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
}
