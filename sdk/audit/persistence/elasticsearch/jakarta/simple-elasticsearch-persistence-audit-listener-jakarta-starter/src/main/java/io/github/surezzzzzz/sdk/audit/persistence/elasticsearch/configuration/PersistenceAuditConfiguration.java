package io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.configuration;

import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.PersistenceAuditPackage;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.annotation.PersistenceAuditComponent;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.constant.PersistenceAuditConstant;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.handler.EsPersistenceAuditHandler;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.listener.EsPersistenceAuditEventListener;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.provider.EsPersistenceAuditTraceIdProvider;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.provider.EsPersistenceAuditUserProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * ES Persistence 审计监听器自动配置
 *
 * @author surezzzzzz
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(PersistenceAuditProperties.class)
@ComponentScan(
        basePackageClasses = PersistenceAuditPackage.class,
        includeFilters = @ComponentScan.Filter(PersistenceAuditComponent.class),
        useDefaultFilters = false
)
@ConditionalOnProperty(prefix = PersistenceAuditConstant.CONFIG_PREFIX,
        name = PersistenceAuditConstant.CONFIG_ENABLE,
        havingValue = "true")
public class PersistenceAuditConfiguration {

    /**
     * 创建由 Spring 管理初始化和关闭的专用审计线程池。
     */
    @Bean(name = PersistenceAuditConstant.EXECUTOR_BEAN_NAME)
    @ConditionalOnMissingBean(name = PersistenceAuditConstant.EXECUTOR_BEAN_NAME)
    public ThreadPoolTaskExecutor esPersistenceAuditExecutor(PersistenceAuditProperties properties) {
        PersistenceAuditProperties.Executor config = properties.getExecutor();
        Assert.notNull(config, ErrorMessage.INVALID_EXECUTOR);
        Assert.isTrue(config.getCoreSize() > 0 && config.getMaxSize() >= config.getCoreSize()
                && config.getQueueCapacity() >= 0 && config.getKeepAliveSeconds() >= 0, ErrorMessage.INVALID_EXECUTOR);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(config.getCoreSize());
        executor.setMaxPoolSize(config.getMaxSize());
        executor.setQueueCapacity(config.getQueueCapacity());
        executor.setKeepAliveSeconds(config.getKeepAliveSeconds());
        executor.setThreadNamePrefix(PersistenceAuditConstant.DEFAULT_EXECUTOR_THREAD_NAME_PREFIX);
        executor.setRejectedExecutionHandler(buildRejectPolicy(config.getRejectPolicy()));


        log.info("EsPersistenceAudit executor initialized: coreSize={}, maxSize={}, queueCapacity={}, rejectPolicy={}",
                config.getCoreSize(), config.getMaxSize(), config.getQueueCapacity(), config.getRejectPolicy());
        return executor;
    }

    /**
     * 在扫描 Handler 后装配监听器；宿主可以替换监听器和同名执行器。
     */
    @Bean
    @ConditionalOnBean(EsPersistenceAuditHandler.class)
    @ConditionalOnMissingBean(EsPersistenceAuditEventListener.class)
    public EsPersistenceAuditEventListener esPersistenceAuditEventListener(List<EsPersistenceAuditHandler> handlers,
                                                                           ObjectProvider<EsPersistenceAuditUserProvider> users, ObjectProvider<EsPersistenceAuditTraceIdProvider> traces,
                                                                           PersistenceAuditProperties properties,
                                                                           @Qualifier(PersistenceAuditConstant.EXECUTOR_BEAN_NAME) Executor executor) {
        return new EsPersistenceAuditEventListener(handlers, users.getIfAvailable(), traces.getIfAvailable(), properties, executor);
    }

    private RejectedExecutionHandler buildRejectPolicy(String policy) {
        String value = policy == null ? PersistenceAuditConstant.REJECT_POLICY_CALLER_RUNS : policy.trim().toUpperCase(Locale.ROOT);
        final RejectedExecutionHandler delegate;
        switch (value) {
            case PersistenceAuditConstant.REJECT_POLICY_DISCARD:
                delegate = new ThreadPoolExecutor.DiscardPolicy();
                break;
            case PersistenceAuditConstant.REJECT_POLICY_DISCARD_OLDEST:
                delegate = new ThreadPoolExecutor.DiscardOldestPolicy();
                break;
            case PersistenceAuditConstant.REJECT_POLICY_ABORT:
                delegate = new ThreadPoolExecutor.AbortPolicy();
                break;
            case PersistenceAuditConstant.REJECT_POLICY_CALLER_RUNS:
                delegate = new ThreadPoolExecutor.CallerRunsPolicy();
                break;
            default:
                throw new IllegalArgumentException(ErrorMessage.INVALID_REJECT_POLICY);
        }
        return (task, pool) -> {
            log.warn("审计任务被拒绝 policy={} shutdown={}", value, pool.isShutdown());
            // 零容量队列没有最旧任务，避免 DiscardOldestPolicy 递归重提。
            if (pool.isShutdown() || (PersistenceAuditConstant.REJECT_POLICY_DISCARD_OLDEST.equals(value)
                    && pool.getQueue().poll() == null)) {
                if (PersistenceAuditConstant.REJECT_POLICY_ABORT.equals(value)) delegate.rejectedExecution(task, pool);
                return;
            }
            if (PersistenceAuditConstant.REJECT_POLICY_DISCARD_OLDEST.equals(value)) pool.execute(task);
            else delegate.rejectedExecution(task, pool);
        };
    }
}
