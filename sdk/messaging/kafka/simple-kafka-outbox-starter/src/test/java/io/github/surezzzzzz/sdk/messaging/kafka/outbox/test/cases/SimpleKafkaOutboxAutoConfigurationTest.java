package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.cleanup.KafkaOutboxCleanup;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.configuration.KafkaOutboxPropertiesValidator;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.configuration.SimpleKafkaOutboxConfiguration;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.configuration.SimpleKafkaOutboxProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.SimpleKafkaOutboxConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.engine.DefaultKafkaOutboxEngine;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.engine.KafkaOutboxEngine;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.listener.KafkaOutboxEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.listener.NoOpKafkaOutboxEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.repository.JdbcKafkaOutboxRepository;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.repository.KafkaOutboxRepository;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.retry.DefaultKafkaOutboxJitterGenerator;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.retry.DefaultKafkaOutboxRetryPolicy;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.retry.KafkaOutboxJitterGenerator;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.retry.KafkaOutboxRetryPolicy;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.serializer.JacksonKafkaOutboxMessageSerializer;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.serializer.KafkaOutboxMessageSerializer;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.trace.DefaultKafkaOutboxTraceSnapshotResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.trace.KafkaOutboxTraceScope;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.trace.KafkaOutboxTraceSnapshotResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.trace.MdcKafkaOutboxTraceScope;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.worker.DefaultKafkaOutboxWorker;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.worker.KafkaOutboxWorker;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.KafkaPublisher;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.LifecycleProcessor;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Simple Kafka Outbox 自动配置测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SimpleKafkaOutboxAutoConfigurationTest {

    private static final String ENABLE_PROPERTY = SimpleKafkaOutboxConstant.CONFIG_PREFIX + "."
            + SimpleKafkaOutboxConstant.CONFIG_PROPERTY_ENABLE + "=true";
    private static final String DISABLE_PROPERTY = SimpleKafkaOutboxConstant.CONFIG_PREFIX + "."
            + SimpleKafkaOutboxConstant.CONFIG_PROPERTY_ENABLE + "=false";
    private static final String WORKER_DISABLE_PROPERTY = SimpleKafkaOutboxConstant.CONFIG_PREFIX + "."
            + SimpleKafkaOutboxConstant.CONFIG_PROPERTY_WORKER_ENABLE + "=false";
    private static final String CLEANUP_DISABLE_PROPERTY = SimpleKafkaOutboxConstant.CONFIG_PREFIX + "."
            + SimpleKafkaOutboxConstant.CONFIG_PROPERTY_CLEANUP_ENABLE + "=false";
    private static final String CLEANUP_INTERVAL_PROPERTY = SimpleKafkaOutboxConstant.CONFIG_PREFIX
            + ".cleanup.interval-ms=86400000";

    @Test
    public void testDefaultChainCreatesAllBeans() {
        enabledContextRunner().run(context -> {
            stopCleanup(context);
            assertDefaultInfrastructure(context);
            assertDefaultSpiChain(context);

            SimpleKafkaOutboxConfiguration.KafkaOutboxResource resource =
                    context.getBean(SimpleKafkaOutboxConfiguration.KafkaOutboxResource.class);
            DataSource dataSource = context.getBean(DataSource.class);
            DataSourceTransactionManager transactionManager = context.getBean(DataSourceTransactionManager.class);
            log.info("默认资源组合 dataSource={}, transactionManager={}", dataSource, transactionManager);
            assertSame(dataSource, resource.getDataSource(), "资源组合必须使用容器中的 DataSource 实例");
            log.info("校验资源组合中的事务管理器实例: {}", resource.getTransactionManager());
            assertSame(transactionManager, resource.getTransactionManager(), "资源组合必须使用容器中的事务管理器实例");
            log.info("校验事务管理器绑定的数据源实例: {}", transactionManager.getDataSource());
            assertSame(dataSource, transactionManager.getDataSource(), "事务管理器必须管理同一个 DataSource 实例");
        });
    }

    @Test
    public void testCustomSerializerOnlyReplacesSerializer() {
        enabledContextRunner()
                .withBean("customSerializer", KafkaOutboxMessageSerializer.class,
                        () -> mock(KafkaOutboxMessageSerializer.class))
                .run(context -> {
                    stopCleanup(context);
                    assertDefaultInfrastructure(context);
                    assertDefaultRuntimeResources(context);
                    assertCustomBean(context, KafkaOutboxMessageSerializer.class, "customSerializer", "序列化器");
                    assertNoBean(context, JacksonKafkaOutboxMessageSerializer.class, "自定义序列化器生效后不应保留默认序列化器");
                    assertDefaultRepositoryRetryListenerAndWorker(context);
                });
    }

    @Test
    public void testCustomRetryPolicyOnlyReplacesRetryPolicy() {
        enabledContextRunner()
                .withBean("customRetryPolicy", KafkaOutboxRetryPolicy.class,
                        () -> mock(KafkaOutboxRetryPolicy.class))
                .run(context -> {
                    stopCleanup(context);
                    assertDefaultInfrastructure(context);
                    assertDefaultRuntimeResources(context);
                    assertCustomBean(context, KafkaOutboxRetryPolicy.class, "customRetryPolicy", "重试策略");
                    assertNoBean(context, DefaultKafkaOutboxRetryPolicy.class, "自定义重试策略生效后不应保留默认重试策略");
                    assertDefaultSerializerRepositoryListenerAndWorker(context);
                });
    }

    @Test
    public void testCustomListenerOnlyReplacesListener() {
        enabledContextRunner()
                .withBean("customListener", KafkaOutboxEventListener.class,
                        () -> mock(KafkaOutboxEventListener.class))
                .run(context -> {
                    stopCleanup(context);
                    assertDefaultInfrastructure(context);
                    assertDefaultRuntimeResources(context);
                    assertCustomBean(context, KafkaOutboxEventListener.class, "customListener", "事件监听器");
                    assertNoBean(context, NoOpKafkaOutboxEventListener.class, "自定义监听器生效后不应保留默认监听器");
                    assertDefaultSerializerRepositoryRetryAndWorker(context);
                });
    }

    @Test
    public void testCustomRepositoryOnlyReplacesRepository() {
        enabledContextRunner()
                .withBean("customRepository", KafkaOutboxRepository.class,
                        () -> mock(KafkaOutboxRepository.class))
                .run(context -> {
                    stopCleanup(context);
                    assertDefaultInfrastructure(context);
                    assertDefaultRuntimeResources(context);
                    assertCustomBean(context, KafkaOutboxRepository.class, "customRepository", "Repository");
                    assertNoBean(context, JdbcKafkaOutboxRepository.class, "自定义 Repository 生效后不应保留默认 JDBC Repository");
                    assertDefaultSerializerRetryListenerAndWorker(context);
                });
    }

    @Test
    public void testCustomWorkerOnlyReplacesWorker() {
        enabledContextRunner()
                .withBean("customWorker", KafkaOutboxWorker.class, () -> mock(KafkaOutboxWorker.class))
                .run(context -> {
                    stopCleanup(context);
                    assertDefaultInfrastructure(context);
                    assertDefaultRuntimeResources(context);
                    assertCustomBean(context, KafkaOutboxWorker.class, "customWorker", "Worker");
                    assertNoBean(context, DefaultKafkaOutboxWorker.class, "自定义 Worker 生效后不应保留默认 Worker");
                    assertDefaultSerializerRepositoryRetryAndListener(context);
                });
    }

    @Test
    public void testCustomEngineRetiresCompleteDefaultChain() {
        baseContextRunner()
                .withPropertyValues(ENABLE_PROPERTY)
                .withBean(KafkaPublisher.class, () -> mock(KafkaPublisher.class))
                .withBean("customEngine", KafkaOutboxEngine.class, () -> mock(KafkaOutboxEngine.class))
                .run(context -> {
                    log.info("自定义 Engine 完整接管后的启动失败信息: {}", context.getStartupFailure());
                    assertNull(context.getStartupFailure(), "自定义 Engine 完整接管时上下文应正常启动");
                    assertCustomBean(context, KafkaOutboxEngine.class, "customEngine", "Engine");
                    assertNoBean(context, DefaultKafkaOutboxEngine.class, "自定义 Engine 生效后不应保留默认 Engine");
                    assertNoBean(context, SimpleKafkaOutboxProperties.class, "自定义 Engine 生效后不应注册 Properties");
                    assertNoBean(context, KafkaOutboxPropertiesValidator.class, "自定义 Engine 生效后不应注册配置校验器");
                    assertNoBean(context, SimpleKafkaOutboxConfiguration.KafkaOutboxResource.class,
                            "自定义 Engine 生效后不应注册数据库资源组合");
                    assertNoBean(context, NamedParameterJdbcTemplate.class, "自定义 Engine 生效后不应注册 JDBC Template");
                    assertNoBean(context, TransactionTemplate.class, "自定义 Engine 生效后不应注册事务模板");
                    assertNoBean(context, KafkaOutboxRepository.class, "自定义 Engine 生效后不应注册 Repository");
                    assertNoBean(context, KafkaOutboxMessageSerializer.class, "自定义 Engine 生效后不应注册序列化器");
                    assertNoBean(context, KafkaOutboxRetryPolicy.class, "自定义 Engine 生效后不应注册重试策略");
                    assertNoBean(context, KafkaOutboxJitterGenerator.class, "自定义 Engine 生效后不应注册抖动生成器");
                    assertNoBean(context, KafkaOutboxEventListener.class, "自定义 Engine 生效后不应注册监听器");
                    assertNoBean(context, KafkaOutboxTraceSnapshotResolver.class, "自定义 Engine 生效后不应注册 trace 快照解析器");
                    assertNoBean(context, KafkaOutboxTraceScope.class, "自定义 Engine 生效后不应注册 trace 作用域");
                    assertNoBean(context, DataSource.class, "自定义 Engine 生效后不应要求或注册 DataSource");
                    assertNoBean(context, DataSourceTransactionManager.class,
                            "自定义 Engine 生效后不应要求或注册事务管理器");
                    assertNoBean(context, KafkaOutboxWorker.class, "自定义 Engine 生效后不应注册 Worker");
                    assertNoBean(context, KafkaOutboxCleanup.class, "自定义 Engine 生效后不应注册清理器");
                    assertNoBean(context, ThreadPoolTaskExecutor.class, "自定义 Engine 生效后不应注册任务执行器");
                    assertNoBean(context, ThreadPoolTaskScheduler.class, "自定义 Engine 生效后不应注册任何调度器");
                    assertBeanAbsentByName(context, SimpleKafkaOutboxConstant.BEAN_NAMED_JDBC_TEMPLATE,
                            "自定义 Engine 生效后不应注册命名 JDBC Template");
                    assertBeanAbsentByName(context, SimpleKafkaOutboxConstant.BEAN_TRANSACTION_TEMPLATE,
                            "自定义 Engine 生效后不应注册命名事务模板");
                    assertBeanAbsentByName(context, SimpleKafkaOutboxConstant.BEAN_TASK_EXECUTOR,
                            "自定义 Engine 生效后不应注册命名任务执行器");
                    assertBeanAbsentByName(context, SimpleKafkaOutboxConstant.BEAN_WORKER_SCHEDULER,
                            "自定义 Engine 生效后不应注册 Worker 调度器");
                    assertBeanAbsentByName(context, SimpleKafkaOutboxConstant.BEAN_CLEANUP_SCHEDULER,
                            "自定义 Engine 生效后不应注册清理调度器");
                    assertBeanAbsentByName(context, "kafkaOutboxPropertiesValidationInitializer",
                            "自定义 Engine 生效后不应注册配置校验触发器");
                });
    }

    @Test
    public void testWorkerDisabledRetiresWorkerResources() {
        enabledContextRunner().withPropertyValues(WORKER_DISABLE_PROPERTY).run(context -> {
            stopCleanup(context);
            assertDefaultInfrastructure(context);
            assertDefaultSerializerRepositoryRetryAndListener(context);
            assertNoBean(context, KafkaOutboxWorker.class, "worker.enable=false 时不应注册 Worker");
            assertNoBean(context, ThreadPoolTaskExecutor.class, "worker.enable=false 时不应注册 Worker 执行器");
            assertBeanAbsentByName(context, SimpleKafkaOutboxConstant.BEAN_WORKER_SCHEDULER,
                    "worker.enable=false 时不应注册 Worker 调度器");
            assertSingleBean(context, KafkaOutboxCleanup.class, "worker 关闭时默认清理器仍应存在");
        });
    }

    @Test
    public void testCleanupDisabledRetiresCleanupResources() {
        enabledContextRunner().withPropertyValues(CLEANUP_DISABLE_PROPERTY).run(context -> {
            assertDefaultInfrastructure(context);
            assertDefaultSpiChainExceptCleanup(context);
            assertNoBean(context, KafkaOutboxCleanup.class, "cleanup.enable=false 时不应注册清理器");
            assertBeanAbsentByName(context, SimpleKafkaOutboxConstant.BEAN_CLEANUP_SCHEDULER,
                    "cleanup.enable=false 时不应注册清理调度器");
        });
    }

    @Test
    public void testMissingPublisherDoesNotEnableOutbox() {
        contextRunnerWithoutPublisher().withPropertyValues(ENABLE_PROPERTY).run(context -> {
            log.info("缺少 KafkaPublisher 时 Engine 数量: {}", context.getBeansOfType(KafkaOutboxEngine.class).size());
            assertEquals(0, context.getBeansOfType(KafkaOutboxEngine.class).size(),
                    "缺少 KafkaPublisher 时不应启用 Outbox");
            assertNoBean(context, SimpleKafkaOutboxProperties.class, "缺少 KafkaPublisher 时不应注册 Properties");
            assertNoBean(context, KafkaOutboxRepository.class, "缺少 KafkaPublisher 时不应注册 Repository");
            assertNoBean(context, KafkaOutboxWorker.class, "缺少 KafkaPublisher 时不应注册 Worker");
            assertNoBean(context, KafkaOutboxCleanup.class, "缺少 KafkaPublisher 时不应注册清理器");
        });
    }

    @Test
    public void testDisabledPropertyDoesNotEnableOutbox() {
        contextRunnerWithPublisher().withPropertyValues(DISABLE_PROPERTY).run(context -> {
            log.info("enable=false 时 Engine 数量: {}", context.getBeansOfType(KafkaOutboxEngine.class).size());
            assertEquals(0, context.getBeansOfType(KafkaOutboxEngine.class).size(),
                    "enable=false 时不应启用 Outbox");
            assertNoBean(context, SimpleKafkaOutboxProperties.class, "enable=false 时不应注册 Properties");
            assertNoBean(context, KafkaOutboxRepository.class, "enable=false 时不应注册 Repository");
            assertNoBean(context, KafkaOutboxWorker.class, "enable=false 时不应注册 Worker");
            assertNoBean(context, KafkaOutboxCleanup.class, "enable=false 时不应注册清理器");
        });
    }

    private ApplicationContextRunner enabledContextRunner() {
        return contextRunnerWithPublisher().withPropertyValues(ENABLE_PROPERTY, CLEANUP_INTERVAL_PROPERTY);
    }

    private ApplicationContextRunner contextRunnerWithPublisher() {
        return contextRunnerWithoutPublisher()
                .withBean(KafkaPublisher.class, () -> mock(KafkaPublisher.class));
    }

    private ApplicationContextRunner contextRunnerWithoutPublisher() {
        return baseContextRunner().withInitializer(applicationContext -> {
            DataSource dataSource = mock(DataSource.class);
            DataSourceTransactionManager transactionManager = mock(DataSourceTransactionManager.class);
            when(transactionManager.getDataSource()).thenReturn(dataSource);
            applicationContext.getBeanFactory().registerSingleton("dataSource", dataSource);
            applicationContext.getBeanFactory().registerSingleton("transactionManager", transactionManager);
        });
    }

    private ApplicationContextRunner baseContextRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleKafkaOutboxConfiguration.class))
                // 阻止 SmartLifecycle 自动启动，避免默认 Worker 在测试上下文中创建扫描线程。
                .withBean(AbstractApplicationContext.LIFECYCLE_PROCESSOR_BEAN_NAME,
                        LifecycleProcessor.class, () -> mock(LifecycleProcessor.class));
    }

    private void stopCleanup(AssertableApplicationContext context) {
        KafkaOutboxCleanup cleanup = context.getBeanProvider(KafkaOutboxCleanup.class).getIfAvailable();
        if (cleanup != null) {
            cleanup.stop();
            log.info("已立即停止自动配置清理任务，避免测试上下文保留调度任务");
        }
    }

    private void assertDefaultInfrastructure(AssertableApplicationContext context) {
        log.info("默认基础链 Bean 列表: {}", (Object) context.getBeanDefinitionNames());
        assertNull(context.getStartupFailure(), "默认基础链上下文应正常启动");
        assertSingleBean(context, SimpleKafkaOutboxProperties.class, "应注册一个 Properties Bean");
        assertSingleBean(context, KafkaOutboxPropertiesValidator.class, "应注册一个配置校验器 Bean");
        assertBeanPresentByName(context, "kafkaOutboxPropertiesValidationInitializer",
                "应注册配置校验触发器 Bean");
        assertSingleBean(context, SimpleKafkaOutboxConfiguration.KafkaOutboxResource.class,
                "应注册一个已校验的数据库资源组合");
        assertSingleBean(context, NamedParameterJdbcTemplate.class, "应注册一个模块专用 JDBC Template");
        assertSingleBean(context, TransactionTemplate.class, "应注册一个模块专用事务模板");
        assertSingleBean(context, KafkaOutboxTraceSnapshotResolver.class, "应注册一个 trace 快照解析器");
        assertSingleBean(context, DefaultKafkaOutboxTraceSnapshotResolver.class,
                "trace 快照解析器应使用默认实现");
        assertSingleBean(context, KafkaOutboxTraceScope.class, "应注册一个 trace 作用域");
        assertSingleBean(context, MdcKafkaOutboxTraceScope.class, "trace 作用域应使用 MDC 默认实现");
        assertSingleBean(context, KafkaOutboxEngine.class, "应注册一个 Engine");
        assertSingleBean(context, DefaultKafkaOutboxEngine.class, "Engine 应使用默认实现");
        assertBeanPresentByName(context, SimpleKafkaOutboxConstant.BEAN_NAMED_JDBC_TEMPLATE,
                "应按常量名称注册 JDBC Template");
        assertBeanPresentByName(context, SimpleKafkaOutboxConstant.BEAN_TRANSACTION_TEMPLATE,
                "应按常量名称注册事务模板");
    }

    private void assertDefaultRuntimeResources(AssertableApplicationContext context) {
        assertSingleBean(context, KafkaOutboxCleanup.class, "单 SPI 覆盖时默认清理器应保留");
        assertSingleBean(context, ThreadPoolTaskExecutor.class, "单 SPI 覆盖时 Worker 执行器应保留");
        log.info("单 SPI 覆盖时调度器数量: {}", context.getBeansOfType(ThreadPoolTaskScheduler.class).size());
        assertEquals(2, context.getBeansOfType(ThreadPoolTaskScheduler.class).size(),
                "单 SPI 覆盖时 Worker 和 Cleanup 两个调度器均应保留");
        assertBeanPresentByName(context, SimpleKafkaOutboxConstant.BEAN_TASK_EXECUTOR,
                "单 SPI 覆盖时 Worker 执行器应保留");
        assertBeanPresentByName(context, SimpleKafkaOutboxConstant.BEAN_WORKER_SCHEDULER,
                "单 SPI 覆盖时 Worker 调度器应保留");
        assertBeanPresentByName(context, SimpleKafkaOutboxConstant.BEAN_CLEANUP_SCHEDULER,
                "单 SPI 覆盖时清理调度器应保留");
    }

    private void assertDefaultSpiChain(AssertableApplicationContext context) {
        assertDefaultSerializerRepositoryRetryAndListener(context);
        assertSingleBean(context, KafkaOutboxWorker.class, "应注册一个 Worker");
        assertSingleBean(context, DefaultKafkaOutboxWorker.class, "Worker 应使用默认实现");
        assertSingleBean(context, KafkaOutboxCleanup.class, "应注册一个默认清理器");
        assertSingleBean(context, ThreadPoolTaskExecutor.class, "应注册一个 Worker 执行器");
        log.info("默认调度器数量: {}", context.getBeansOfType(ThreadPoolTaskScheduler.class).size());
        assertEquals(2, context.getBeansOfType(ThreadPoolTaskScheduler.class).size(),
                "默认链应注册 Worker 和 Cleanup 两个调度器");
        assertBeanPresentByName(context, SimpleKafkaOutboxConstant.BEAN_TASK_EXECUTOR,
                "应按常量名称注册 Worker 执行器");
        assertBeanPresentByName(context, SimpleKafkaOutboxConstant.BEAN_WORKER_SCHEDULER,
                "应按常量名称注册 Worker 调度器");
        assertBeanPresentByName(context, SimpleKafkaOutboxConstant.BEAN_CLEANUP_SCHEDULER,
                "应按常量名称注册清理调度器");
    }

    private void assertDefaultSpiChainExceptCleanup(AssertableApplicationContext context) {
        assertDefaultSerializerRepositoryRetryAndListener(context);
        assertSingleBean(context, KafkaOutboxWorker.class, "清理关闭时仍应注册一个 Worker");
        assertSingleBean(context, DefaultKafkaOutboxWorker.class, "清理关闭时 Worker 仍应使用默认实现");
        assertSingleBean(context, ThreadPoolTaskExecutor.class, "清理关闭时仍应注册 Worker 执行器");
        assertBeanPresentByName(context, SimpleKafkaOutboxConstant.BEAN_WORKER_SCHEDULER,
                "清理关闭时仍应注册 Worker 调度器");
    }

    private void assertDefaultRepositoryRetryListenerAndWorker(AssertableApplicationContext context) {
        assertSingleBean(context, KafkaOutboxRepository.class, "仅替换序列化器时 Repository 应保留");
        assertSingleBean(context, JdbcKafkaOutboxRepository.class, "仅替换序列化器时应保留默认 JDBC Repository");
        assertSingleBean(context, KafkaOutboxRetryPolicy.class, "仅替换序列化器时重试策略应保留");
        assertSingleBean(context, DefaultKafkaOutboxRetryPolicy.class, "仅替换序列化器时应保留默认重试策略");
        assertSingleBean(context, KafkaOutboxEventListener.class, "仅替换序列化器时监听器应保留");
        assertSingleBean(context, NoOpKafkaOutboxEventListener.class, "仅替换序列化器时应保留默认监听器");
        assertSingleBean(context, KafkaOutboxWorker.class, "仅替换序列化器时 Worker 应保留");
        assertSingleBean(context, DefaultKafkaOutboxWorker.class, "仅替换序列化器时应保留默认 Worker");
    }

    private void assertDefaultSerializerRepositoryListenerAndWorker(AssertableApplicationContext context) {
        assertSingleBean(context, KafkaOutboxMessageSerializer.class, "仅替换重试策略时序列化器应保留");
        assertSingleBean(context, JacksonKafkaOutboxMessageSerializer.class, "仅替换重试策略时应保留默认序列化器");
        assertSingleBean(context, KafkaOutboxRepository.class, "仅替换重试策略时 Repository 应保留");
        assertSingleBean(context, JdbcKafkaOutboxRepository.class, "仅替换重试策略时应保留默认 JDBC Repository");
        assertSingleBean(context, KafkaOutboxEventListener.class, "仅替换重试策略时监听器应保留");
        assertSingleBean(context, NoOpKafkaOutboxEventListener.class, "仅替换重试策略时应保留默认监听器");
        assertSingleBean(context, KafkaOutboxWorker.class, "仅替换重试策略时 Worker 应保留");
        assertSingleBean(context, DefaultKafkaOutboxWorker.class, "仅替换重试策略时应保留默认 Worker");
    }

    private void assertDefaultSerializerRepositoryRetryAndWorker(AssertableApplicationContext context) {
        assertSingleBean(context, KafkaOutboxMessageSerializer.class, "仅替换监听器时序列化器应保留");
        assertSingleBean(context, JacksonKafkaOutboxMessageSerializer.class, "仅替换监听器时应保留默认序列化器");
        assertSingleBean(context, KafkaOutboxRepository.class, "仅替换监听器时 Repository 应保留");
        assertSingleBean(context, JdbcKafkaOutboxRepository.class, "仅替换监听器时应保留默认 JDBC Repository");
        assertSingleBean(context, KafkaOutboxRetryPolicy.class, "仅替换监听器时重试策略应保留");
        assertSingleBean(context, DefaultKafkaOutboxRetryPolicy.class, "仅替换监听器时应保留默认重试策略");
        assertSingleBean(context, KafkaOutboxWorker.class, "仅替换监听器时 Worker 应保留");
        assertSingleBean(context, DefaultKafkaOutboxWorker.class, "仅替换监听器时应保留默认 Worker");
    }

    private void assertDefaultSerializerRetryListenerAndWorker(AssertableApplicationContext context) {
        assertSingleBean(context, KafkaOutboxMessageSerializer.class, "仅替换 Repository 时序列化器应保留");
        assertSingleBean(context, JacksonKafkaOutboxMessageSerializer.class, "仅替换 Repository 时应保留默认序列化器");
        assertSingleBean(context, KafkaOutboxRetryPolicy.class, "仅替换 Repository 时重试策略应保留");
        assertSingleBean(context, DefaultKafkaOutboxRetryPolicy.class, "仅替换 Repository 时应保留默认重试策略");
        assertSingleBean(context, KafkaOutboxEventListener.class, "仅替换 Repository 时监听器应保留");
        assertSingleBean(context, NoOpKafkaOutboxEventListener.class, "仅替换 Repository 时应保留默认监听器");
        assertSingleBean(context, KafkaOutboxWorker.class, "仅替换 Repository 时 Worker 应保留");
        assertSingleBean(context, DefaultKafkaOutboxWorker.class, "仅替换 Repository 时应保留默认 Worker");
    }

    private void assertDefaultSerializerRepositoryRetryAndListener(AssertableApplicationContext context) {
        assertSingleBean(context, KafkaOutboxMessageSerializer.class, "序列化器应使用默认链实现");
        assertSingleBean(context, JacksonKafkaOutboxMessageSerializer.class, "应保留默认 Jackson 序列化器");
        assertSingleBean(context, KafkaOutboxRepository.class, "Repository 应使用默认链实现");
        assertSingleBean(context, JdbcKafkaOutboxRepository.class, "应保留默认 JDBC Repository");
        assertSingleBean(context, KafkaOutboxRetryPolicy.class, "重试策略应使用默认链实现");
        assertSingleBean(context, DefaultKafkaOutboxRetryPolicy.class, "应保留默认重试策略");
        assertSingleBean(context, KafkaOutboxJitterGenerator.class, "应保留默认抖动生成器 SPI");
        assertSingleBean(context, DefaultKafkaOutboxJitterGenerator.class, "抖动生成器应使用默认实现");
        assertSingleBean(context, KafkaOutboxEventListener.class, "监听器应使用默认链实现");
        assertSingleBean(context, NoOpKafkaOutboxEventListener.class, "应保留默认空操作监听器");
    }

    private <T> void assertCustomBean(AssertableApplicationContext context, Class<T> type,
                                      String beanName, String description) {
        T customBean = context.getBean(beanName, type);
        T selectedBean = context.getBean(type);
        log.info("自定义 {} Bean={}, 容器选中 Bean={}", description, customBean, selectedBean);
        assertSame(customBean, selectedBean, "自定义" + description + "必须覆盖默认实现且保持单实例");
    }

    private void assertSingleBean(AssertableApplicationContext context, Class<?> type,
                                  String failureMessage) {
        int count = context.getBeansOfType(type).size();
        log.info("类型 {} 的 Bean 数量: {}", type.getSimpleName(), count);
        assertEquals(1, count, failureMessage);
    }

    private void assertNoBean(AssertableApplicationContext context, Class<?> type,
                              String failureMessage) {
        int count = context.getBeansOfType(type).size();
        log.info("期望退场的类型 {} Bean 数量: {}", type.getSimpleName(), count);
        assertEquals(0, count, failureMessage);
    }

    private void assertBeanPresentByName(AssertableApplicationContext context, String beanName,
                                         String failureMessage) {
        boolean present = context.containsBean(beanName);
        log.info("期望存在的 Bean 名称 {}，存在={}", beanName, present);
        assertTrue(present, failureMessage);
    }

    private void assertBeanAbsentByName(AssertableApplicationContext context, String beanName,
                                        String failureMessage) {
        boolean present = context.containsBean(beanName);
        log.info("期望退场的 Bean 名称 {}，存在={}", beanName, present);
        assertFalse(present, failureMessage);
    }

}
