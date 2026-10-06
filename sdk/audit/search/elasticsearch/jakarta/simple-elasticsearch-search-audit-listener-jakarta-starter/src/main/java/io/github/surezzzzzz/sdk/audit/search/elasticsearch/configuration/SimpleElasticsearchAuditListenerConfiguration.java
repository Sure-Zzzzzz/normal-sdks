package io.github.surezzzzzz.sdk.audit.search.elasticsearch.configuration;

import io.github.surezzzzzz.sdk.audit.search.elasticsearch.SimpleElasticsearchAuditListenerPackage;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.annotation.SimpleElasticsearchAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.constant.SimpleElasticsearchAuditListenerConstant;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.handler.EsAuditHandler;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.listener.EsAuditEventListener;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.provider.EsAuditTraceIdProvider;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.provider.EsAuditUserProvider;
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
 * Elasticsearch Search Jakarta 审计自动配置
 *
 * @author surezzzzzz
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(SimpleElasticsearchAuditListenerProperties.class)
@ComponentScan(
        basePackageClasses = SimpleElasticsearchAuditListenerPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleElasticsearchAuditListenerComponent.class),
        useDefaultFilters = false
)
@ConditionalOnProperty(prefix = SimpleElasticsearchAuditListenerConstant.CONFIG_PREFIX,
        name = SimpleElasticsearchAuditListenerConstant.CONFIG_ENABLE, havingValue = "true")
public class SimpleElasticsearchAuditListenerConfiguration {

    /**
     * 创建由 Spring 管理初始化和关闭的专用审计线程池。
     */
    @Bean(name = SimpleElasticsearchAuditListenerConstant.EXECUTOR_BEAN_NAME)
    @ConditionalOnMissingBean(name = SimpleElasticsearchAuditListenerConstant.EXECUTOR_BEAN_NAME)
    public ThreadPoolTaskExecutor esAuditExecutor(SimpleElasticsearchAuditListenerProperties properties) {
        SimpleElasticsearchAuditListenerProperties.Executor config = properties.getExecutor();
        Assert.notNull(config, ErrorMessage.INVALID_EXECUTOR);
        Assert.isTrue(config.getCoreSize() > 0 && config.getMaxSize() >= config.getCoreSize()
                && config.getQueueCapacity() >= 0 && config.getKeepAliveSeconds() >= 0, ErrorMessage.INVALID_EXECUTOR);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(config.getCoreSize());
        executor.setMaxPoolSize(config.getMaxSize());
        executor.setQueueCapacity(config.getQueueCapacity());
        executor.setKeepAliveSeconds(config.getKeepAliveSeconds());
        executor.setThreadNamePrefix(SimpleElasticsearchAuditListenerConstant.DEFAULT_EXECUTOR_THREAD_NAME_PREFIX);
        executor.setRejectedExecutionHandler(buildRejectPolicy(config.getRejectPolicy()));


        log.info("EsAudit executor initialized: coreSize={}, maxSize={}, queueCapacity={}, rejectPolicy={}",
                config.getCoreSize(), config.getMaxSize(), config.getQueueCapacity(), config.getRejectPolicy());
        return executor;
    }

    /**
     * 在扫描 Handler 后装配监听器；宿主可以替换监听器和同名执行器。
     */
    @Bean
    @ConditionalOnBean(EsAuditHandler.class)
    @ConditionalOnMissingBean(EsAuditEventListener.class)
    public EsAuditEventListener esAuditEventListener(List<EsAuditHandler> handlers,
                                                     ObjectProvider<EsAuditUserProvider> users, ObjectProvider<EsAuditTraceIdProvider> traces,
                                                     SimpleElasticsearchAuditListenerProperties properties,
                                                     @Qualifier(SimpleElasticsearchAuditListenerConstant.EXECUTOR_BEAN_NAME) Executor executor) {
        return new EsAuditEventListener(handlers, users.getIfAvailable(), traces.getIfAvailable(), executor);
    }

    private RejectedExecutionHandler buildRejectPolicy(String policy) {
        String value = policy == null ? SimpleElasticsearchAuditListenerConstant.REJECT_POLICY_CALLER_RUNS : policy.trim().toUpperCase(Locale.ROOT);
        final RejectedExecutionHandler delegate;
        switch (value) {
            case SimpleElasticsearchAuditListenerConstant.REJECT_POLICY_DISCARD:
                delegate = new ThreadPoolExecutor.DiscardPolicy();
                break;
            case SimpleElasticsearchAuditListenerConstant.REJECT_POLICY_DISCARD_OLDEST:
                delegate = new ThreadPoolExecutor.DiscardOldestPolicy();
                break;
            case SimpleElasticsearchAuditListenerConstant.REJECT_POLICY_ABORT:
                delegate = new ThreadPoolExecutor.AbortPolicy();
                break;
            case SimpleElasticsearchAuditListenerConstant.REJECT_POLICY_CALLER_RUNS:
                delegate = new ThreadPoolExecutor.CallerRunsPolicy();
                break;
            default:
                throw new IllegalArgumentException(ErrorMessage.INVALID_REJECT_POLICY);
        }
        return (task, pool) -> {
            log.warn("审计任务被拒绝 policy={} shutdown={}", value, pool.isShutdown());
            // 零容量队列没有最旧任务，避免 DiscardOldestPolicy 递归重提。
            if (pool.isShutdown() || (SimpleElasticsearchAuditListenerConstant.REJECT_POLICY_DISCARD_OLDEST.equals(value)
                    && pool.getQueue().poll() == null)) {
                if (SimpleElasticsearchAuditListenerConstant.REJECT_POLICY_ABORT.equals(value))
                    delegate.rejectedExecution(task, pool);
                return;
            }
            if (SimpleElasticsearchAuditListenerConstant.REJECT_POLICY_DISCARD_OLDEST.equals(value)) pool.execute(task);
            else delegate.rejectedExecution(task, pool);
        };
    }
}
