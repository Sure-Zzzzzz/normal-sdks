package io.github.surezzzzzz.sdk.audit.search.elasticsearch.configuration;

import io.github.surezzzzzz.sdk.audit.search.elasticsearch.SimpleElasticsearchAuditListenerPackage;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.annotation.SimpleElasticsearchAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.constant.SimpleElasticsearchAuditListenerConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Simple Elasticsearch Audit Listener Auto Configuration
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SimpleElasticsearchAuditListenerProperties.class)
@ComponentScan(
        basePackageClasses = SimpleElasticsearchAuditListenerPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleElasticsearchAuditListenerComponent.class)
)
public class SimpleElasticsearchAuditListenerConfiguration {

    @Bean(name = SimpleElasticsearchAuditListenerConstant.EXECUTOR_BEAN_NAME)
    @ConditionalOnMissingBean(name = SimpleElasticsearchAuditListenerConstant.EXECUTOR_BEAN_NAME)
    public Executor esAuditExecutor(SimpleElasticsearchAuditListenerProperties properties) {
        SimpleElasticsearchAuditListenerProperties.Executor config = properties.getExecutor();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(config.getCoreSize());
        executor.setMaxPoolSize(config.getMaxSize());
        executor.setQueueCapacity(config.getQueueCapacity());
        executor.setKeepAliveSeconds(config.getKeepAliveSeconds());
        executor.setThreadNamePrefix(SimpleElasticsearchAuditListenerConstant.DEFAULT_EXECUTOR_THREAD_NAME_PREFIX);
        executor.setRejectedExecutionHandler(buildRejectPolicy(config.getRejectPolicy()));
        executor.initialize();

        log.info("EsAudit executor initialized: coreSize={}, maxSize={}, queueCapacity={}, rejectPolicy={}",
                config.getCoreSize(), config.getMaxSize(), config.getQueueCapacity(), config.getRejectPolicy());
        return executor;
    }

    private RejectedExecutionHandler buildRejectPolicy(String policy) {
        switch (policy.toUpperCase()) {
            case SimpleElasticsearchAuditListenerConstant.REJECT_POLICY_DISCARD:
                return new ThreadPoolExecutor.DiscardPolicy();
            case SimpleElasticsearchAuditListenerConstant.REJECT_POLICY_DISCARD_OLDEST:
                return new ThreadPoolExecutor.DiscardOldestPolicy();
            case SimpleElasticsearchAuditListenerConstant.REJECT_POLICY_ABORT:
                return new ThreadPoolExecutor.AbortPolicy();
            case SimpleElasticsearchAuditListenerConstant.REJECT_POLICY_CALLER_RUNS:
            default:
                return new ThreadPoolExecutor.CallerRunsPolicy();
        }
    }
}
