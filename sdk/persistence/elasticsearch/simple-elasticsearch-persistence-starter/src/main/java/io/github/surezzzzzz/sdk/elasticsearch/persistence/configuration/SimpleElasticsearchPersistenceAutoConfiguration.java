package io.github.surezzzzzz.sdk.elasticsearch.persistence.configuration;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.SimpleElasticsearchPersistencePackage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.annotation.SimpleElasticsearchPersistenceComponent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.SimpleElasticsearchPersistenceConstant;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.DefaultPersistenceEngine;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.PersistenceEngine;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.executor.PersistenceExecutorRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Simple Elasticsearch Persistence Auto Configuration
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SimpleElasticsearchPersistenceProperties.class)
@ComponentScan(
        basePackageClasses = SimpleElasticsearchPersistencePackage.class,
        includeFilters = @ComponentScan.Filter(SimpleElasticsearchPersistenceComponent.class)
)
@ConditionalOnProperty(prefix = SimpleElasticsearchPersistenceConstant.CONFIG_PREFIX,
        name = SimpleElasticsearchPersistenceConstant.CONFIG_ENABLE, havingValue = "true")
public class SimpleElasticsearchPersistenceAutoConfiguration {

    @Bean(name = SimpleElasticsearchPersistenceConstant.ASYNC_EXECUTOR_BEAN_NAME)
    @ConditionalOnMissingBean(name = SimpleElasticsearchPersistenceConstant.ASYNC_EXECUTOR_BEAN_NAME)
    public Executor esPersistenceAsyncExecutor(SimpleElasticsearchPersistenceProperties properties) {
        SimpleElasticsearchPersistenceProperties.Async config = properties.getAsync();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(config.getCoreSize());
        executor.setMaxPoolSize(config.getMaxSize());
        executor.setQueueCapacity(config.getQueueCapacity());
        executor.setThreadNamePrefix(SimpleElasticsearchPersistenceConstant.ASYNC_EXECUTOR_THREAD_NAME_PREFIX);
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean(PersistenceEngine.class)
    public PersistenceEngine persistenceEngine(PersistenceExecutorRegistry executorRegistry,
                                               Executor esPersistenceAsyncExecutor) {
        log.info("初始化 DefaultPersistenceEngine");
        return new DefaultPersistenceEngine(executorRegistry, esPersistenceAsyncExecutor);
    }
}
