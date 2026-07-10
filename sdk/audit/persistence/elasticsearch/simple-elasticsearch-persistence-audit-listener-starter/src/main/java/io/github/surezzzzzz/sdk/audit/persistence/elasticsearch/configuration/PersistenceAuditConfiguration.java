package io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.configuration;

import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.PersistenceAuditPackage;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.annotation.PersistenceAuditComponent;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.constant.PersistenceAuditConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * ES Persistence 审计监听器自动配置
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(PersistenceAuditProperties.class)
@ComponentScan(
        basePackageClasses = PersistenceAuditPackage.class,
        includeFilters = @ComponentScan.Filter(PersistenceAuditComponent.class)
)
@ConditionalOnProperty(prefix = PersistenceAuditConstant.CONFIG_PREFIX,
        name = PersistenceAuditConstant.CONFIG_ENABLE,
        havingValue = "true")
public class PersistenceAuditConfiguration {

    @Bean(name = PersistenceAuditConstant.EXECUTOR_BEAN_NAME)
    @ConditionalOnMissingBean(name = PersistenceAuditConstant.EXECUTOR_BEAN_NAME)
    public Executor esPersistenceAuditExecutor(PersistenceAuditProperties properties) {
        PersistenceAuditProperties.Executor config = properties.getExecutor();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(config.getCoreSize());
        executor.setMaxPoolSize(config.getMaxSize());
        executor.setQueueCapacity(config.getQueueCapacity());
        executor.setKeepAliveSeconds(config.getKeepAliveSeconds());
        executor.setThreadNamePrefix(PersistenceAuditConstant.DEFAULT_EXECUTOR_THREAD_NAME_PREFIX);
        executor.setRejectedExecutionHandler(buildRejectPolicy(config.getRejectPolicy()));
        executor.initialize();

        log.info("EsPersistenceAudit executor initialized: coreSize={}, maxSize={}, queueCapacity={}, rejectPolicy={}",
                config.getCoreSize(), config.getMaxSize(), config.getQueueCapacity(), config.getRejectPolicy());
        return executor;
    }

    private RejectedExecutionHandler buildRejectPolicy(String policy) {
        String upperPolicy = policy == null ? PersistenceAuditConstant.REJECT_POLICY_CALLER_RUNS
                : policy.toUpperCase();
        switch (upperPolicy) {
            case PersistenceAuditConstant.REJECT_POLICY_DISCARD:
                return new ThreadPoolExecutor.DiscardPolicy();
            case PersistenceAuditConstant.REJECT_POLICY_DISCARD_OLDEST:
                return new ThreadPoolExecutor.DiscardOldestPolicy();
            case PersistenceAuditConstant.REJECT_POLICY_ABORT:
                return new ThreadPoolExecutor.AbortPolicy();
            case PersistenceAuditConstant.REJECT_POLICY_CALLER_RUNS:
            default:
                return new ThreadPoolExecutor.CallerRunsPolicy();
        }
    }
}
