package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaConsumerFactoryOverride;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteContext;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.kafka.route.resolver.KafkaRouteResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.configuration.SimpleKafkaConsumerProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.container.KafkaConsumerContainerFactory;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.container.KafkaConsumerContainerManager;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.error.DeadLetterPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.error.KafkaConsumerErrorHandler;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.exception.KafkaConsumerConfigurationException;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.handler.KafkaConsumerHandler;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.NoOpKafkaConsumerIdempotencyChecker;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.listener.NoOpKafkaConsumerEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.ConsumerRegistration;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerContainerContext;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.registrar.KafkaConsumerRegistrar;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 消费容器管理器测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaConsumerContainerManagerTest {

    @Test
    public void testCompatibleTopicsShareContainerAndDerivedFactory() throws Exception {
        KafkaConsumerRegistrar registrar = registrar(registration("topic-a", "group-a"), registration("topic-b", "group-a"));
        SimpleKafkaRouteRegistry registry = registry();
        List<KafkaConsumerContainerContext> contexts = new ArrayList<>();
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        KafkaConsumerContainerFactory factory = context -> {
            contexts.add(context);
            return container;
        };
        ConsumerFactory<Object, Object> consumerFactory = derivedFactory();
        when(registry.createConsumerFactory(eq("source-a"), any(KafkaConsumerFactoryOverride.class)))
                .thenReturn(consumerFactory);

        KafkaConsumerContainerManager manager = manager(registrar, registry, factory, routeProperties("route-group"));
        manager.start();
        log.info("共享容器上下文：topics={}，groupId={}，autoOffsetReset={}", contexts.get(0).getTopics(),
                contexts.get(0).getGroupId(), contexts.get(0).getAutoOffsetReset());

        assertTrue(manager.isRunning());
        assertEquals(1, contexts.size());
        assertEquals(Arrays.asList("topic-a", "topic-b"), contexts.get(0).getTopics());
        assertEquals("group-a", contexts.get(0).getGroupId());
        verify(registry, times(1)).createConsumerFactory(eq("source-a"), any(KafkaConsumerFactoryOverride.class));
        verify(container).start();

        manager.stop();
        assertFalse(manager.isRunning());
        verify(container).stop();
        verify((DisposableBean) consumerFactory).destroy();
    }

    @Test
    public void testSameDatasourceAndTopicWithDifferentGroupsCreateIndependentContainers() {
        KafkaConsumerRegistrar registrar = registrar(registration("topic-a", "group-a"),
                registration("topic-a", "group-b"));
        SimpleKafkaRouteRegistry registry = registry();
        when(registry.createConsumerFactory(eq("source-a"), any(KafkaConsumerFactoryOverride.class)))
                .thenReturn(derivedFactory(), derivedFactory());
        List<KafkaConsumerContainerContext> contexts = new ArrayList<>();
        KafkaConsumerContainerManager manager = manager(registrar, registry, context -> {
            contexts.add(context);
            return mock(MessageListenerContainer.class);
        }, routeProperties("route-group"));

        manager.start();
        log.info("同 topic 不同 group 容器结果：contextCount={}，groups={}", contexts.size(),
                contexts.stream().map(KafkaConsumerContainerContext::getGroupId)
                        .collect(java.util.stream.Collectors.toList()));

        assertEquals(2, contexts.size());
        assertEquals(Arrays.asList("topic-a"), contexts.get(0).getTopics());
        assertEquals(Arrays.asList("topic-a"), contexts.get(1).getTopics());
        assertEquals("group-a", contexts.get(0).getGroupId());
        assertEquals("group-b", contexts.get(1).getGroupId());
        manager.stop();
    }

    @Test
    public void testRouteResolverSelectsDatasourceForRegistrationWithoutExplicitDatasource() {
        ConsumerRegistration registration = ConsumerRegistration.builder().topic("resolved-topic").id("resolved")
                .handler((KafkaConsumerHandler<String, String>) record -> {
                }).build();
        KafkaConsumerRegistrar registrar = registrar(registration);
        SimpleKafkaRouteRegistry registry = registry();
        ConsumerFactory<Object, Object> factory = derivedFactory();
        when(registry.createConsumerFactory(eq("source-a"), any(KafkaConsumerFactoryOverride.class))).thenReturn(factory);
        KafkaRouteResolver resolver = mock(KafkaRouteResolver.class);
        when(resolver.resolveDataSource(any(KafkaRouteContext.class))).thenReturn("source-a");
        List<KafkaConsumerContainerContext> contexts = new ArrayList<>();
        KafkaConsumerContainerManager manager = manager(registrar, registry, resolver, context -> {
            contexts.add(context);
            return mock(MessageListenerContainer.class);
        }, routeProperties("route-group"), properties());

        manager.start();
        log.info("Route 解析数据源结果：contextCount={}，datasource={}", contexts.size(),
                contexts.isEmpty() ? null : contexts.get(0).getDatasourceKey());

        assertEquals("source-a", contexts.get(0).getDatasourceKey());
        verify(resolver).resolveDataSource(any(KafkaRouteContext.class));
        manager.stop();
    }

    @Test
    public void testOverridesAndGlobalContainerSettingsReachRouteFactory() {
        KafkaConsumerRegistrar registrar = registrar(registration("topic-a", "annotation-group", " EARLIEST "));
        SimpleKafkaRouteRegistry registry = registry();
        ConsumerFactory<Object, Object> consumerFactory = derivedFactory();
        when(registry.createConsumerFactory(eq("source-a"), any(KafkaConsumerFactoryOverride.class)))
                .thenReturn(consumerFactory);
        List<KafkaConsumerContainerContext> contexts = new ArrayList<>();
        KafkaConsumerContainerFactory factory = context -> {
            contexts.add(context);
            return mock(MessageListenerContainer.class);
        };
        SimpleKafkaConsumerProperties properties = properties();
        properties.getContainer().setEnableAutoCommit(false);
        properties.getContainer().setMaxPollRecords(77);

        KafkaConsumerContainerManager manager = manager(registrar, registry, factory, routeProperties("route-group"), properties);
        manager.start();
        org.mockito.ArgumentCaptor<KafkaConsumerFactoryOverride> captor =
                org.mockito.ArgumentCaptor.forClass(KafkaConsumerFactoryOverride.class);
        verify(registry).createConsumerFactory(eq("source-a"), captor.capture());
        KafkaConsumerFactoryOverride override = captor.getValue();
        log.info("Route Consumer 覆盖结果：groupId={}，offsetReset={}，autoCommit={}，maxPollRecords={}",
                override.getGroupId(), override.getAutoOffsetReset(), override.getEnableAutoCommit(),
                override.getMaxPollRecords());

        assertEquals("annotation-group", override.getGroupId());
        assertEquals("earliest", override.getAutoOffsetReset());
        assertEquals(Boolean.FALSE, override.getEnableAutoCommit());
        assertEquals(Integer.valueOf(77), override.getMaxPollRecords());
        assertFalse(contexts.get(0).isEnableAutoCommit());
        assertEquals(77, contexts.get(0).getMaxPollRecords());
        manager.stop();
    }

    @Test
    public void testFailedSecondContainerRollsBackEarlierResources() throws Exception {
        KafkaConsumerRegistrar registrar = registrar(registration("topic-a", "group-a"), registration("topic-b", "group-b"));
        SimpleKafkaRouteRegistry registry = registry();
        ConsumerFactory<Object, Object> firstFactory = derivedFactory();
        ConsumerFactory<Object, Object> secondFactory = derivedFactory();
        when(registry.createConsumerFactory(eq("source-a"), any(KafkaConsumerFactoryOverride.class)))
                .thenReturn(firstFactory, secondFactory);
        MessageListenerContainer firstContainer = mock(MessageListenerContainer.class);
        KafkaConsumerContainerFactory factory = new KafkaConsumerContainerFactory() {
            private int count;

            @Override
            public MessageListenerContainer createContainer(KafkaConsumerContainerContext context) {
                count++;
                if (count == 2) {
                    throw new IllegalStateException("mock second container failure");
                }
                return firstContainer;
            }
        };

        KafkaConsumerContainerManager manager = manager(registrar, registry, factory, routeProperties("route-group"));
        IllegalStateException exception = assertThrows(IllegalStateException.class, manager::start);
        log.info("第二个容器创建失败回滚结果：error={}，managerRunning={}", exception.getMessage(), manager.isRunning());

        assertFalse(manager.isRunning());
        verify(firstContainer).start();
        verify(firstContainer).stop();
        verify((DisposableBean) firstFactory).destroy();
        verify((DisposableBean) secondFactory).destroy();
    }

    @Test
    public void testInvalidRouteMaxPollRecordsFailsBeforeResourcesCreated() {
        KafkaConsumerRegistrar registrar = registrar(registration("topic-a", "group-a"));
        SimpleKafkaRouteRegistry registry = registry();
        SimpleKafkaRouteProperties routeProperties = routeProperties("route-group");
        routeProperties.getSources().get("source-a").getConsumer().setMaxPollRecords(0);
        KafkaConsumerContainerManager manager = manager(registrar, registry, context -> mock(MessageListenerContainer.class),
                routeProperties);

        KafkaConsumerConfigurationException exception = assertThrows(KafkaConsumerConfigurationException.class, manager::start);
        log.info("非法 route maxPollRecords 错误码={}，消息={}", exception.getErrorCode(), exception.getMessage());

        assertEquals(ErrorCode.CONFIG_INVALID, exception.getErrorCode());
        assertEquals("消费配置或注册非法：max-poll-records-invalid", exception.getMessage());
        assertFalse(manager.isRunning());
        verify(registry, org.mockito.Mockito.never()).createConsumerFactory(any(String.class), any(KafkaConsumerFactoryOverride.class));
    }

    @Test
    public void testInvalidRegistrationOffsetResetFailsBeforeResourcesCreated() {
        KafkaConsumerRegistrar registrar = registrar(registration("topic-a", "group-a", "mock-invalid"));
        SimpleKafkaRouteRegistry registry = registry();
        KafkaConsumerContainerManager manager = manager(registrar, registry, context -> mock(MessageListenerContainer.class),
                routeProperties("route-group"));

        KafkaConsumerConfigurationException exception = assertThrows(KafkaConsumerConfigurationException.class, manager::start);
        log.info("非法注册偏移策略错误码={}，消息={}", exception.getErrorCode(), exception.getMessage());

        assertEquals(ErrorCode.CONFIG_INVALID, exception.getErrorCode());
        assertEquals("消费配置或注册非法：auto-offset-reset-invalid", exception.getMessage());
        assertFalse(manager.isRunning());
        verify(registry, org.mockito.Mockito.never()).createConsumerFactory(any(String.class), any(KafkaConsumerFactoryOverride.class));
    }

    @Test
    public void testDuplicateResolvedDatasourceTopicAndGroupFailsBeforeResourcesCreated() {
        KafkaConsumerRegistrar registrar = registrar(registration("topic-a", "group-a"), registration("topic-a", "group-a"));
        SimpleKafkaRouteRegistry registry = registry();
        KafkaConsumerContainerManager manager = manager(registrar, registry, context -> mock(MessageListenerContainer.class),
                routeProperties("route-group"));

        KafkaConsumerConfigurationException exception = assertThrows(KafkaConsumerConfigurationException.class, manager::start);
        log.info("重复有效容器组-topic 注册错误：{}", exception.getMessage());
        assertEquals(ErrorCode.CONFIG_INVALID, exception.getErrorCode());
        verify(registry, org.mockito.Mockito.never()).createConsumerFactory(any(String.class), any(KafkaConsumerFactoryOverride.class));
    }

    @Test
    public void testRepeatedStartDoesNotCreateOrStartReplacementResources() throws Exception {
        KafkaConsumerRegistrar registrar = registrar(registration("topic-a", "group-a"));
        SimpleKafkaRouteRegistry registry = registry();
        ConsumerFactory<Object, Object> consumerFactory = derivedFactory();
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(registry.createConsumerFactory(eq("source-a"), any(KafkaConsumerFactoryOverride.class)))
                .thenReturn(consumerFactory);
        KafkaConsumerContainerManager manager = manager(registrar, registry, context -> container, routeProperties("route-group"));

        manager.start();
        manager.start();
        log.info("重复启动后的运行状态：{}", manager.isRunning());

        assertTrue(manager.isRunning());
        verify(registry, times(1)).createConsumerFactory(eq("source-a"), any(KafkaConsumerFactoryOverride.class));
        verify(container, times(1)).start();
        manager.stop();
        verify((DisposableBean) consumerFactory).destroy();
    }

    @Test
    public void testRefreshStartsReplacementBeforeStoppingAndDestroyingPreviousResources() throws Exception {
        KafkaConsumerRegistrar registrar = registrar(registration("topic-a", "group-a"));
        SimpleKafkaRouteRegistry registry = registry();
        ConsumerFactory<Object, Object> firstFactory = derivedFactory();
        ConsumerFactory<Object, Object> replacementFactory = derivedFactory();
        MessageListenerContainer firstContainer = mock(MessageListenerContainer.class);
        MessageListenerContainer replacementContainer = mock(MessageListenerContainer.class);
        when(registry.createConsumerFactory(eq("source-a"), any(KafkaConsumerFactoryOverride.class)))
                .thenReturn(firstFactory, replacementFactory);
        KafkaConsumerContainerManager manager = manager(registrar, registry, new KafkaConsumerContainerFactory() {
            private int count;

            @Override
            public MessageListenerContainer createContainer(KafkaConsumerContainerContext context) {
                return count++ == 0 ? firstContainer : replacementContainer;
            }
        }, routeProperties("route-group"));

        manager.start();
        manager.refresh();
        log.info("刷新后的运行状态：{}", manager.isRunning());

        InOrder order = org.mockito.Mockito.inOrder(replacementContainer, firstContainer, firstFactory);
        order.verify(replacementContainer).start();
        order.verify(firstContainer).stop();
        order.verify((DisposableBean) firstFactory).destroy();
        assertTrue(manager.isRunning());
        manager.stop();
        verify((DisposableBean) replacementFactory).destroy();
    }

    @Test
    public void testStopWaitsForContainerCallbackBeforeDestroyingDerivedFactory() throws Exception {
        KafkaConsumerRegistrar registrar = registrar(registration("topic-a", "group-a"));
        SimpleKafkaRouteRegistry registry = registry();
        ConsumerFactory<Object, Object> consumerFactory = derivedFactory();
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        AtomicReference<Runnable> stopCallback = new AtomicReference<>();
        CountDownLatch stopInvoked = new CountDownLatch(1);
        when(container.isRunning()).thenReturn(true, false);
        org.mockito.Mockito.doAnswer(invocation -> {
            stopCallback.set(invocation.getArgument(0));
            stopInvoked.countDown();
            return null;
        }).when(container).stop(any(Runnable.class));
        when(registry.createConsumerFactory(eq("source-a"), any(KafkaConsumerFactoryOverride.class)))
                .thenReturn(consumerFactory);
        KafkaConsumerContainerManager manager = manager(registrar, registry, context -> container,
                routeProperties("route-group"));
        manager.start();
        Thread stoppingThread = new Thread(manager::stop, "consumer-manager-stop-test");
        stoppingThread.start();

        boolean stopCallbackInvoked = stopInvoked.await(3, TimeUnit.SECONDS);
        log.info("异步停止等待中：callbackInvoked={}，threadAlive={}", stopCallbackInvoked, stoppingThread.isAlive());

        assertTrue(stopCallbackInvoked, "manager 必须调用容器的异步停止回调");
        verify((DisposableBean) consumerFactory, org.mockito.Mockito.never()).destroy();
        assertTrue(stoppingThread.isAlive(), "容器停止未完成前 manager 不得结束停止流程");

        stopCallback.get().run();
        stoppingThread.join(3000L);

        assertFalse(stoppingThread.isAlive(), "容器停止完成后 manager 必须结束停止流程");
        assertFalse(manager.isRunning());
        verify((DisposableBean) consumerFactory).destroy();
    }

    @Test
    public void testStopTimeoutRetainsDerivedFactory() throws Exception {
        KafkaConsumerRegistrar registrar = registrar(registration("topic-a", "group-a"));
        SimpleKafkaRouteRegistry registry = registry();
        ConsumerFactory<Object, Object> consumerFactory = derivedFactory();
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(container.isRunning()).thenReturn(true);
        when(registry.createConsumerFactory(eq("source-a"), any(KafkaConsumerFactoryOverride.class)))
                .thenReturn(consumerFactory);
        SimpleKafkaConsumerProperties properties = properties();
        properties.getContainer().setShutdownAwaitMs(1L);
        KafkaConsumerContainerManager manager = manager(registrar, registry, context -> container,
                routeProperties("route-group"), properties);
        manager.start();

        manager.stop();

        assertFalse(manager.isRunning());
        verify(container).stop(any(Runnable.class));
        verify((DisposableBean) consumerFactory, org.mockito.Mockito.never()).destroy();
    }

    @Test
    public void testInterruptedStopWaitRetainsFactoryAndRestoresInterruptFlag() throws Exception {
        KafkaConsumerRegistrar registrar = registrar(registration("topic-a", "group-a"));
        SimpleKafkaRouteRegistry registry = registry();
        ConsumerFactory<Object, Object> consumerFactory = derivedFactory();
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        CountDownLatch stopInvoked = new CountDownLatch(1);
        AtomicBoolean interruptedAfterStop = new AtomicBoolean(false);
        when(container.isRunning()).thenReturn(true);
        org.mockito.Mockito.doAnswer(invocation -> {
            stopInvoked.countDown();
            return null;
        }).when(container).stop(any(Runnable.class));
        when(registry.createConsumerFactory(eq("source-a"), any(KafkaConsumerFactoryOverride.class)))
                .thenReturn(consumerFactory);
        KafkaConsumerContainerManager manager = manager(registrar, registry, context -> container,
                routeProperties("route-group"));
        manager.start();
        Thread stoppingThread = new Thread(() -> {
            manager.stop();
            interruptedAfterStop.set(Thread.currentThread().isInterrupted());
        }, "consumer-manager-interrupted-stop-test");
        stoppingThread.start();

        boolean stopCallbackInvoked = stopInvoked.await(3, TimeUnit.SECONDS);
        log.info("中断停止等待前：callbackInvoked={}，threadAlive={}", stopCallbackInvoked, stoppingThread.isAlive());

        assertTrue(stopCallbackInvoked, "manager 必须等待运行中容器的停止回调");
        stoppingThread.interrupt();
        stoppingThread.join(3000L);

        assertFalse(stoppingThread.isAlive(), "等待被中断后 manager 必须结束停止流程");
        assertTrue(interruptedAfterStop.get(), "manager 必须恢复调用线程的中断标记");
        assertFalse(manager.isRunning());
        verify((DisposableBean) consumerFactory, org.mockito.Mockito.never()).destroy();
    }

    @Test
    public void testRefreshWaitsForRunningContainerCallbackBeforeDestroyingPreviousFactory() throws Exception {
        KafkaConsumerRegistrar registrar = registrar(registration("topic-a", "group-a"));
        SimpleKafkaRouteRegistry registry = registry();
        ConsumerFactory<Object, Object> firstFactory = derivedFactory();
        ConsumerFactory<Object, Object> replacementFactory = derivedFactory();
        MessageListenerContainer firstContainer = mock(MessageListenerContainer.class);
        MessageListenerContainer replacementContainer = mock(MessageListenerContainer.class);
        AtomicReference<Runnable> stopCallback = new AtomicReference<>();
        AtomicReference<Throwable> refreshFailure = new AtomicReference<>();
        CountDownLatch stopInvoked = new CountDownLatch(1);
        when(firstContainer.isRunning()).thenReturn(true, false);
        org.mockito.Mockito.doAnswer(invocation -> {
            stopCallback.set(invocation.getArgument(0));
            stopInvoked.countDown();
            return null;
        }).when(firstContainer).stop(any(Runnable.class));
        when(registry.createConsumerFactory(eq("source-a"), any(KafkaConsumerFactoryOverride.class)))
                .thenReturn(firstFactory, replacementFactory);
        KafkaConsumerContainerManager manager = manager(registrar, registry, new KafkaConsumerContainerFactory() {
            private int count;

            @Override
            public MessageListenerContainer createContainer(KafkaConsumerContainerContext context) {
                return count++ == 0 ? firstContainer : replacementContainer;
            }
        }, routeProperties("route-group"));
        manager.start();
        Thread refreshThread = new Thread(() -> {
            try {
                manager.refresh();
            } catch (Throwable e) {
                refreshFailure.set(e);
            }
        }, "consumer-manager-refresh-stop-test");
        refreshThread.start();

        boolean stopCallbackInvoked = stopInvoked.await(3, TimeUnit.SECONDS);
        log.info("刷新等待旧容器停止：callbackInvoked={}，refreshThreadAlive={}", stopCallbackInvoked,
                refreshThread.isAlive());

        assertTrue(stopCallbackInvoked, "刷新必须等待旧运行容器停止完成");
        verify(replacementContainer).start();
        verify((DisposableBean) firstFactory, org.mockito.Mockito.never()).destroy();
        assertTrue(refreshThread.isAlive(), "旧容器未停止完成前 refresh 不得结束");

        stopCallback.get().run();
        refreshThread.join(3000L);

        assertFalse(refreshThread.isAlive(), "旧容器停止完成后 refresh 必须结束");
        assertNull(refreshFailure.get(), "刷新过程不应抛出异常");
        assertTrue(manager.isRunning());
        verify((DisposableBean) firstFactory).destroy();
        manager.stop();
        verify((DisposableBean) replacementFactory).destroy();
    }

    @Test
    public void testContainerStartFailureWaitsForStopCallbackBeforeDestroyingFactory() throws Exception {
        KafkaConsumerRegistrar registrar = registrar(registration("topic-a", "group-a"));
        SimpleKafkaRouteRegistry registry = registry();
        ConsumerFactory<Object, Object> consumerFactory = derivedFactory();
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        AtomicReference<Runnable> stopCallback = new AtomicReference<>();
        AtomicReference<Throwable> startFailure = new AtomicReference<>();
        CountDownLatch stopInvoked = new CountDownLatch(1);
        when(container.isRunning()).thenReturn(true, false);
        doThrow(new IllegalStateException("mock start failure")).when(container).start();
        org.mockito.Mockito.doAnswer(invocation -> {
            stopCallback.set(invocation.getArgument(0));
            stopInvoked.countDown();
            return null;
        }).when(container).stop(any(Runnable.class));
        when(registry.createConsumerFactory(eq("source-a"), any(KafkaConsumerFactoryOverride.class)))
                .thenReturn(consumerFactory);
        KafkaConsumerContainerManager manager = manager(registrar, registry, context -> container,
                routeProperties("route-group"));
        Thread startingThread = new Thread(() -> {
            try {
                manager.start();
            } catch (Throwable e) {
                startFailure.set(e);
            }
        }, "consumer-manager-start-rollback-test");
        startingThread.start();

        boolean stopCallbackInvoked = stopInvoked.await(3, TimeUnit.SECONDS);
        log.info("启动回滚等待旧容器停止：callbackInvoked={}，startThreadAlive={}", stopCallbackInvoked,
                startingThread.isAlive());

        assertTrue(stopCallbackInvoked, "启动失败后必须等待容器停止完成");
        verify((DisposableBean) consumerFactory, org.mockito.Mockito.never()).destroy();
        assertTrue(startingThread.isAlive(), "容器停止未完成前启动回滚不得结束");

        stopCallback.get().run();
        startingThread.join(3000L);

        assertFalse(startingThread.isAlive(), "容器停止完成后启动回滚必须结束");
        assertTrue(startFailure.get() instanceof IllegalStateException, "必须保留原始启动失败");
        assertFalse(manager.isRunning());
        verify((DisposableBean) consumerFactory).destroy();
    }

    @Test
    public void testStopCallbackRunsAfterContainerStopFailure() throws Exception {
        KafkaConsumerRegistrar registrar = registrar(registration("topic-a", "group-a"));
        SimpleKafkaRouteRegistry registry = registry();
        ConsumerFactory<Object, Object> consumerFactory = derivedFactory();
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        doThrow(new IllegalStateException("mock stop failure")).when(container).stop();
        when(registry.createConsumerFactory(eq("source-a"), any(KafkaConsumerFactoryOverride.class)))
                .thenReturn(consumerFactory);
        KafkaConsumerContainerManager manager = manager(registrar, registry, context -> container, routeProperties("route-group"));
        AtomicBoolean callbackCalled = new AtomicBoolean(false);
        manager.start();

        manager.stop(() -> callbackCalled.set(true));
        log.info("停止回调执行状态：{}", callbackCalled.get());

        assertTrue(callbackCalled.get());
        assertFalse(manager.isRunning());
        verify((DisposableBean) consumerFactory, org.mockito.Mockito.never()).destroy();
    }

    @Test
    public void testContainerStartFailureRollsBackCurrentFactoryAndContainer() throws Exception {
        KafkaConsumerRegistrar registrar = registrar(registration("topic-a", "group-a"));
        SimpleKafkaRouteRegistry registry = registry();
        ConsumerFactory<Object, Object> consumerFactory = derivedFactory();
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        doThrow(new IllegalStateException("mock start failure")).when(container).start();
        when(registry.createConsumerFactory(eq("source-a"), any(KafkaConsumerFactoryOverride.class)))
                .thenReturn(consumerFactory);
        KafkaConsumerContainerManager manager = manager(registrar, registry, context -> container, routeProperties("route-group"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, manager::start);
        log.info("容器启动回滚错误：{}", exception.getMessage());

        assertFalse(manager.isRunning());
        verify(container).stop();
        verify((DisposableBean) consumerFactory).destroy();
    }

    private KafkaConsumerContainerManager manager(KafkaConsumerRegistrar registrar, SimpleKafkaRouteRegistry registry,
                                                  KafkaConsumerContainerFactory factory,
                                                  SimpleKafkaRouteProperties routeProperties) {
        return manager(registrar, registry, factory, routeProperties, properties());
    }

    private KafkaConsumerContainerManager manager(KafkaConsumerRegistrar registrar, SimpleKafkaRouteRegistry registry,
                                                  KafkaConsumerContainerFactory factory,
                                                  SimpleKafkaRouteProperties routeProperties,
                                                  SimpleKafkaConsumerProperties properties) {
        return manager(registrar, registry, mock(KafkaRouteResolver.class), factory, routeProperties, properties);
    }

    private KafkaConsumerContainerManager manager(KafkaConsumerRegistrar registrar, SimpleKafkaRouteRegistry registry,
                                                  KafkaRouteResolver resolver, KafkaConsumerContainerFactory factory,
                                                  SimpleKafkaRouteProperties routeProperties,
                                                  SimpleKafkaConsumerProperties properties) {
        KafkaConsumerErrorHandler<String, String> errorHandler = (record, cause, attempt) -> null;
        DeadLetterPublisher deadLetterPublisher = (record, cause, attempt, errorCode) -> false;
        return new KafkaConsumerContainerManager(registrar, registry, resolver, routeProperties, properties,
                new NoOpKafkaConsumerIdempotencyChecker(), errorHandler, deadLetterPublisher,
                new NoOpKafkaConsumerEventListener(), factory);
    }

    private SimpleKafkaRouteRegistry registry() {
        SimpleKafkaRouteRegistry registry = mock(SimpleKafkaRouteRegistry.class);
        when(registry.containsDatasource("source-a")).thenReturn(true);
        return registry;
    }

    private SimpleKafkaRouteProperties routeProperties(String groupId) {
        SimpleKafkaRouteProperties properties = new SimpleKafkaRouteProperties();
        SimpleKafkaRouteProperties.DataSourceConfig source = new SimpleKafkaRouteProperties.DataSourceConfig();
        source.getConsumer().setGroupId(groupId);
        source.getConsumer().setAutoOffsetReset("latest");
        source.getConsumer().setEnableAutoCommit(false);
        source.getConsumer().setMaxPollRecords(500);
        properties.getSources().put("source-a", source);
        return properties;
    }

    private SimpleKafkaConsumerProperties properties() {
        SimpleKafkaConsumerProperties properties = new SimpleKafkaConsumerProperties();
        properties.getContainer().setAutoOffsetReset(null);
        properties.getContainer().setEnableAutoCommit(null);
        properties.getContainer().setMaxPollRecords(null);
        return properties;
    }

    private KafkaConsumerRegistrar registrar(ConsumerRegistration... registrations) {
        KafkaConsumerRegistrar registrar = new KafkaConsumerRegistrar();
        for (ConsumerRegistration registration : registrations) {
            registrar.register(registration);
        }
        return registrar;
    }

    private ConsumerRegistration registration(String topic, String groupId) {
        return registration(topic, groupId, null);
    }

    private ConsumerRegistration registration(String topic, String groupId, String autoOffsetReset) {
        KafkaConsumerHandler<String, String> handler = record -> {
        };
        return ConsumerRegistration.builder().topic(topic).datasource("source-a").groupId(groupId)
                .autoOffsetReset(autoOffsetReset).id(topic + "#handler").handler(handler).build();
    }

    @SuppressWarnings("unchecked")
    private ConsumerFactory<Object, Object> derivedFactory() {
        return mock(ConsumerFactory.class, org.mockito.Mockito.withSettings().extraInterfaces(DisposableBean.class));
    }
}
