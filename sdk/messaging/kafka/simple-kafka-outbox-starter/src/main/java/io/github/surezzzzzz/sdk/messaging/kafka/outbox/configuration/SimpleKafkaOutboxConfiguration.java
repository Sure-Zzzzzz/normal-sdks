package io.github.surezzzzzz.sdk.messaging.kafka.outbox.configuration;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.SimpleKafkaOutboxPackage;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.annotation.SimpleKafkaOutboxComponent;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.cleanup.KafkaOutboxCleanup;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.SimpleKafkaOutboxConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.engine.DefaultKafkaOutboxEngine;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.engine.KafkaOutboxEngine;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.exception.KafkaOutboxConfigurationException;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.listener.KafkaOutboxEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.repository.JdbcKafkaOutboxRepository;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.repository.KafkaOutboxRepository;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.retry.DefaultKafkaOutboxRetryPolicy;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.retry.KafkaOutboxJitterGenerator;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.retry.KafkaOutboxRetryPolicy;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.serializer.KafkaOutboxMessageSerializer;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.support.KafkaOutboxStringHelper;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.trace.KafkaOutboxTraceScope;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.trace.KafkaOutboxTraceSnapshotResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.worker.DefaultKafkaOutboxWorker;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.worker.KafkaOutboxWorker;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.configuration.SimpleKafkaPublisherConfiguration;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.KafkaPublisher;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Simple Kafka Outbox 自动配置
 *
 * @author surezzzzzz
 */
@Configuration
@AutoConfigureAfter(SimpleKafkaPublisherConfiguration.class)
@ConditionalOnBean(KafkaPublisher.class)
@ConditionalOnProperty(prefix = SimpleKafkaOutboxConstant.CONFIG_PREFIX,
        name = SimpleKafkaOutboxConstant.CONFIG_PROPERTY_ENABLE,
        havingValue = SimpleKafkaOutboxConstant.BOOLEAN_TRUE)
public class SimpleKafkaOutboxConfiguration {

    /**
     * 默认 Kafka Outbox 完整链路
     *
     * <p>调用方提供自定义 Engine 后，本配置中的数据库资源、SPI、线程和清理链全部退场。
     */
    @Configuration
    @ConditionalOnMissingBean(KafkaOutboxEngine.class)
    @EnableConfigurationProperties(SimpleKafkaOutboxProperties.class)
    @ComponentScan(basePackageClasses = SimpleKafkaOutboxPackage.class, useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(SimpleKafkaOutboxComponent.class))
    public static class DefaultKafkaOutboxConfiguration {

        /**
         * 创建配置校验触发器。
         */
        @Bean
        public InitializingBean kafkaOutboxPropertiesValidationInitializer(
                SimpleKafkaOutboxProperties properties, KafkaOutboxPropertiesValidator validator) {
            return () -> validator.validate(properties);
        }

        /**
         * 选择并验证数据库资源。
         */
        @Bean
        public KafkaOutboxResource kafkaOutboxResource(ListableBeanFactory beanFactory,
                                                       SimpleKafkaOutboxProperties properties) {
            DataSource dataSource = selectDataSource(beanFactory, properties.getDataSourceBeanName());
            DataSourceTransactionManager transactionManager = selectTransactionManager(beanFactory,
                    properties.getTransactionManagerBeanName());
            if (transactionManager.getDataSource() != dataSource) {
                throw configurationError(ErrorCode.KAFKA_OUTBOX_002,
                        ErrorMessage.KAFKA_OUTBOX_002,
                        SimpleKafkaOutboxConstant.REASON_TX_DATASOURCE_MISMATCH);
            }
            return new KafkaOutboxResource(dataSource, transactionManager);
        }

        /**
         * 创建模块专用 NamedParameterJdbcTemplate。
         */
        @Bean(name = SimpleKafkaOutboxConstant.BEAN_NAMED_JDBC_TEMPLATE)
        public NamedParameterJdbcTemplate simpleKafkaOutboxNamedParameterJdbcTemplate(
                KafkaOutboxResource resource) {
            return new NamedParameterJdbcTemplate(resource.getDataSource());
        }

        /**
         * 创建模块专用 TransactionTemplate。
         */
        @Bean(name = SimpleKafkaOutboxConstant.BEAN_TRANSACTION_TEMPLATE)
        public TransactionTemplate simpleKafkaOutboxTransactionTemplate(KafkaOutboxResource resource) {
            return new TransactionTemplate(resource.getTransactionManager());
        }

        /**
         * 创建默认 Repository。
         */
        @Bean
        @ConditionalOnMissingBean(KafkaOutboxRepository.class)
        public KafkaOutboxRepository kafkaOutboxRepository(
                @Qualifier(SimpleKafkaOutboxConstant.BEAN_NAMED_JDBC_TEMPLATE)
                NamedParameterJdbcTemplate simpleKafkaOutboxNamedParameterJdbcTemplate,
                @Qualifier(SimpleKafkaOutboxConstant.BEAN_TRANSACTION_TEMPLATE)
                TransactionTemplate simpleKafkaOutboxTransactionTemplate,
                SimpleKafkaOutboxProperties properties) {
            return new JdbcKafkaOutboxRepository(simpleKafkaOutboxNamedParameterJdbcTemplate,
                    simpleKafkaOutboxTransactionTemplate, properties.getTableName());
        }

        /**
         * 创建默认重试策略。
         */
        @Bean
        @ConditionalOnMissingBean(KafkaOutboxRetryPolicy.class)
        public KafkaOutboxRetryPolicy kafkaOutboxRetryPolicy(SimpleKafkaOutboxProperties properties,
                                                             KafkaOutboxJitterGenerator jitterGenerator) {
            return new DefaultKafkaOutboxRetryPolicy(properties.getRetry(), jitterGenerator);
        }

        /**
         * 创建默认 Engine。
         */
        @Bean
        public KafkaOutboxEngine kafkaOutboxEngine(KafkaOutboxResource resource,
                                                   KafkaOutboxRepository repository,
                                                   KafkaOutboxMessageSerializer serializer,
                                                   KafkaOutboxTraceSnapshotResolver traceSnapshotResolver,
                                                   KafkaOutboxEventListener listener) {
            return new DefaultKafkaOutboxEngine(resource.getDataSource(), repository, serializer,
                    traceSnapshotResolver, listener);
        }

        /**
         * 默认 Worker 条件配置
         */
        @Configuration
        @ConditionalOnProperty(prefix = SimpleKafkaOutboxConstant.CONFIG_PREFIX,
                name = SimpleKafkaOutboxConstant.CONFIG_PROPERTY_WORKER_ENABLE,
                havingValue = SimpleKafkaOutboxConstant.BOOLEAN_TRUE, matchIfMissing = true)
        public static class WorkerConfiguration {
            /**
             * 创建零排队、有界 Worker 执行器。
             */
            @Bean(name = SimpleKafkaOutboxConstant.BEAN_TASK_EXECUTOR, destroyMethod = "shutdown")
            public ThreadPoolTaskExecutor simpleKafkaOutboxTaskExecutor(SimpleKafkaOutboxProperties properties) {
                ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
                executor.setCorePoolSize(properties.getWorker().getConcurrency());
                executor.setMaxPoolSize(properties.getWorker().getConcurrency());
                executor.setQueueCapacity(SimpleKafkaOutboxConstant.ZERO);
                executor.setThreadNamePrefix(SimpleKafkaOutboxConstant.WORKER_THREAD_PREFIX);
                executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
                executor.setWaitForTasksToCompleteOnShutdown(false);
                executor.initialize();
                return executor;
            }

            /**
             * 创建 Worker 专用调度器。
             */
            @Bean(name = SimpleKafkaOutboxConstant.BEAN_WORKER_SCHEDULER, destroyMethod = "shutdown")
            public ThreadPoolTaskScheduler simpleKafkaOutboxWorkerScheduler() {
                ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
                scheduler.setPoolSize(SimpleKafkaOutboxConstant.DEFAULT_SCHEDULER_POOL_SIZE);
                scheduler.setThreadNamePrefix(SimpleKafkaOutboxConstant.WORKER_SCHEDULER_THREAD_PREFIX);
                scheduler.setRemoveOnCancelPolicy(true);
                scheduler.initialize();
                return scheduler;
            }

            /**
             * 创建默认 Worker，调用方自定义 Worker 时仅替换该 Bean。
             */
            @Bean
            @ConditionalOnMissingBean(KafkaOutboxWorker.class)
            public KafkaOutboxWorker kafkaOutboxWorker(KafkaOutboxRepository repository,
                                                       KafkaOutboxMessageSerializer serializer,
                                                       KafkaOutboxRetryPolicy retryPolicy,
                                                       KafkaOutboxEventListener listener,
                                                       KafkaOutboxTraceScope traceScope,
                                                       KafkaPublisher publisher,
                                                       SimpleKafkaOutboxProperties properties,
                                                       @Qualifier(SimpleKafkaOutboxConstant.BEAN_TASK_EXECUTOR)
                                                       ThreadPoolTaskExecutor simpleKafkaOutboxTaskExecutor,
                                                       @Qualifier(SimpleKafkaOutboxConstant.BEAN_WORKER_SCHEDULER)
                                                       ThreadPoolTaskScheduler simpleKafkaOutboxWorkerScheduler) {
                return new DefaultKafkaOutboxWorker(repository, serializer, retryPolicy, listener, traceScope,
                        publisher, properties, simpleKafkaOutboxTaskExecutor, simpleKafkaOutboxWorkerScheduler);
            }
        }

        /**
         * 独立清理条件配置
         */
        @Configuration
        @ConditionalOnProperty(prefix = SimpleKafkaOutboxConstant.CONFIG_PREFIX,
                name = SimpleKafkaOutboxConstant.CONFIG_PROPERTY_CLEANUP_ENABLE,
                havingValue = SimpleKafkaOutboxConstant.BOOLEAN_TRUE, matchIfMissing = true)
        public static class CleanupConfiguration {
            /**
             * 创建清理任务专用调度器。
             */
            @Bean(name = SimpleKafkaOutboxConstant.BEAN_CLEANUP_SCHEDULER, destroyMethod = "shutdown")
            public ThreadPoolTaskScheduler simpleKafkaOutboxCleanupScheduler() {
                ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
                scheduler.setPoolSize(SimpleKafkaOutboxConstant.DEFAULT_SCHEDULER_POOL_SIZE);
                scheduler.setThreadNamePrefix(SimpleKafkaOutboxConstant.CLEANUP_SCHEDULER_THREAD_PREFIX);
                scheduler.setRemoveOnCancelPolicy(true);
                scheduler.initialize();
                return scheduler;
            }

            /**
             * 创建独立 SENT 清理器。
             */
            @Bean(initMethod = "start", destroyMethod = "stop")
            @ConditionalOnMissingBean(KafkaOutboxCleanup.class)
            public KafkaOutboxCleanup kafkaOutboxCleanup(KafkaOutboxRepository repository,
                                                         KafkaOutboxEventListener listener,
                                                         SimpleKafkaOutboxProperties properties,
                                                         @Qualifier(SimpleKafkaOutboxConstant.BEAN_CLEANUP_SCHEDULER)
                                                         ThreadPoolTaskScheduler simpleKafkaOutboxCleanupScheduler) {
                return new KafkaOutboxCleanup(repository, listener, properties, simpleKafkaOutboxCleanupScheduler);
            }
        }

        /**
         * 按配置名或唯一候选选中业务 DataSource，多候选且未指定时报 KAFKA_OUTBOX_001。
         */
        private static DataSource selectDataSource(ListableBeanFactory beanFactory, String configuredName) {
            if (KafkaOutboxStringHelper.hasText(configuredName)) {
                return getBean(beanFactory, configuredName, DataSource.class,
                        ErrorCode.KAFKA_OUTBOX_001, ErrorMessage.KAFKA_OUTBOX_001,
                        SimpleKafkaOutboxConstant.REASON_DATASOURCE_MISSING);
            }
            Map<String, DataSource> beans = beanFactory.getBeansOfType(DataSource.class);
            if (beans.size() != SimpleKafkaOutboxConstant.ONE) {
                throw configurationError(ErrorCode.KAFKA_OUTBOX_001, ErrorMessage.KAFKA_OUTBOX_001,
                        SimpleKafkaOutboxConstant.REASON_DATASOURCE_AMBIGUOUS);
            }
            return beans.values().iterator().next();
        }

        /**
         * 按配置名或唯一候选选中事务管理器，多候选且未指定时报 KAFKA_OUTBOX_002。
         */
        private static DataSourceTransactionManager selectTransactionManager(ListableBeanFactory beanFactory,
                                                                             String configuredName) {
            if (KafkaOutboxStringHelper.hasText(configuredName)) {
                return getBean(beanFactory, configuredName, DataSourceTransactionManager.class,
                        ErrorCode.KAFKA_OUTBOX_002, ErrorMessage.KAFKA_OUTBOX_002,
                        SimpleKafkaOutboxConstant.REASON_TX_MANAGER_MISSING);
            }
            Map<String, DataSourceTransactionManager> beans =
                    beanFactory.getBeansOfType(DataSourceTransactionManager.class);
            if (beans.size() != SimpleKafkaOutboxConstant.ONE) {
                throw configurationError(ErrorCode.KAFKA_OUTBOX_002, ErrorMessage.KAFKA_OUTBOX_002,
                        SimpleKafkaOutboxConstant.REASON_TX_MANAGER_AMBIGUOUS);
            }
            return beans.values().iterator().next();
        }

        /**
         * 按名获取指定类型 Bean，失败时抛配置异常。
         */
        private static <T> T getBean(ListableBeanFactory beanFactory, String name, Class<T> type,
                                     String errorCode, String messageTemplate, String reason) {
            try {
                return beanFactory.getBean(name, type);
            } catch (RuntimeException e) {
                throw new KafkaOutboxConfigurationException(errorCode,
                        String.format(messageTemplate, reason), e);
            }
        }

        /**
         * 构造配置异常，按模板填充失败原因。
         */
        private static KafkaOutboxConfigurationException configurationError(String errorCode,
                                                                            String messageTemplate,
                                                                            String reason) {
            return new KafkaOutboxConfigurationException(errorCode, String.format(messageTemplate, reason));
        }
    }

    /**
     * 已验证的数据库资源组合
     */
    public static final class KafkaOutboxResource {
        /**
         * 选中的业务 DataSource
         */
        private final DataSource dataSource;
        /**
         * 与 DataSource 匹配的事务管理器
         */
        private final DataSourceTransactionManager transactionManager;

        private KafkaOutboxResource(DataSource dataSource,
                                    DataSourceTransactionManager transactionManager) {
            this.dataSource = dataSource;
            this.transactionManager = transactionManager;
        }

        /**
         * @return 选中的 DataSource
         */
        public DataSource getDataSource() {
            return dataSource;
        }

        /**
         * @return 匹配的事务管理器
         */
        public DataSourceTransactionManager getTransactionManager() {
            return transactionManager;
        }
    }
}
