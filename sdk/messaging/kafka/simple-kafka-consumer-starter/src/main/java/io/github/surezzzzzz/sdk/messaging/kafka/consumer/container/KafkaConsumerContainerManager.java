package io.github.surezzzzzz.sdk.messaging.kafka.consumer.container;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.KafkaRouteInputType;
import io.github.surezzzzzz.sdk.kafka.route.constant.KafkaRouteOperationType;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaConsumerFactoryOverride;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteContext;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.kafka.route.resolver.KafkaRouteResolver;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaConfigurationCompatibilityHelper;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.configuration.SimpleKafkaConsumerProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.SimpleKafkaConsumerConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.error.DeadLetterPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.error.KafkaConsumerErrorHandler;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.exception.KafkaConsumerConfigurationException;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.handler.KafkaConsumerHandler;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.handler.KafkaConsumerHandlerAdapter;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.handler.TopicDispatchingKafkaConsumerHandler;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.KafkaConsumerIdempotencyChecker;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.listener.KafkaConsumerEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.ConsumerContainerGroupKey;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.ConsumerRegistration;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.EffectiveConsumerConfiguration;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerContainerContext;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.registrar.KafkaConsumerRegistrar;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.support.KafkaConsumerStringHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 消费容器生命周期管理器
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaConsumerContainerManager implements SmartLifecycle {

    private final KafkaConsumerRegistrar registrar;
    private final SimpleKafkaRouteRegistry routeRegistry;
    private final KafkaRouteResolver routeResolver;
    private final SimpleKafkaRouteProperties routeProperties;
    private final SimpleKafkaConsumerProperties properties;
    private final KafkaConsumerIdempotencyChecker idempotencyChecker;
    private final KafkaConsumerErrorHandler<String, String> errorHandler;
    private final DeadLetterPublisher deadLetterPublisher;
    private final KafkaConsumerEventListener eventListener;
    private final KafkaConsumerContainerFactory containerFactory;

    private final Map<ConsumerContainerGroupKey, ManagedConsumerContainer> activeContainers = new LinkedHashMap<>();
    private volatile boolean running;

    public KafkaConsumerContainerManager(KafkaConsumerRegistrar registrar,
                                         SimpleKafkaRouteRegistry routeRegistry,
                                         KafkaRouteResolver routeResolver,
                                         SimpleKafkaRouteProperties routeProperties,
                                         SimpleKafkaConsumerProperties properties,
                                         KafkaConsumerIdempotencyChecker idempotencyChecker,
                                         KafkaConsumerErrorHandler<String, String> errorHandler,
                                         DeadLetterPublisher deadLetterPublisher,
                                         KafkaConsumerEventListener eventListener,
                                         KafkaConsumerContainerFactory containerFactory) {
        this.registrar = registrar;
        this.routeRegistry = routeRegistry;
        this.routeResolver = routeResolver;
        this.routeProperties = routeProperties;
        this.properties = properties;
        this.idempotencyChecker = idempotencyChecker;
        this.errorHandler = errorHandler;
        this.deadLetterPublisher = deadLetterPublisher;
        this.eventListener = eventListener;
        this.containerFactory = containerFactory;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        Map<ConsumerContainerGroupKey, ManagedConsumerContainer> created = createContainers();
        activeContainers.putAll(created);
        running = true;
        log.info("Kafka 消费容器管理器启动完成，共启动 [{}] 个容器", activeContainers.size());
    }

    /**
     * 刷新已注册的消费入口
     */
    public synchronized void refresh() {
        if (!running) {
            start();
            return;
        }
        Map<ConsumerContainerGroupKey, ManagedConsumerContainer> created = createContainers();
        Map<ConsumerContainerGroupKey, ManagedConsumerContainer> previous = new LinkedHashMap<>(activeContainers);
        activeContainers.clear();
        activeContainers.putAll(created);
        stopContainers(previous.values());
        log.info("Kafka 消费容器刷新完成，共启动 [{}] 个容器", activeContainers.size());
    }

    @Override
    public synchronized void stop(Runnable callback) {
        try {
            stop();
        } finally {
            if (callback != null) {
                callback.run();
            }
        }
    }

    @Override
    public synchronized void stop() {
        stopContainers(activeContainers.values());
        activeContainers.clear();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return SimpleKafkaConsumerConstant.CONTAINER_LIFECYCLE_PHASE;
    }

    private Map<ConsumerContainerGroupKey, ManagedConsumerContainer> createContainers() {
        Map<ConsumerContainerGroupKey, GroupDefinition> groups = resolveGroups(registrar.getRegistrations());
        Map<ConsumerContainerGroupKey, ManagedConsumerContainer> created = new LinkedHashMap<>();
        try {
            for (Map.Entry<ConsumerContainerGroupKey, GroupDefinition> entry : groups.entrySet()) {
                GroupDefinition group = entry.getValue();
                ConsumerFactory<Object, Object> consumerFactory = routeRegistry.createConsumerFactory(
                        group.configuration.getDatasourceKey(), KafkaConsumerFactoryOverride.builder()
                                .groupId(group.configuration.getGroupId())
                                .autoOffsetReset(group.configuration.getAutoOffsetReset())
                                .enableAutoCommit(group.configuration.isEnableAutoCommit())
                                .maxPollRecords(group.configuration.getMaxPollRecords())
                                .build());
                MessageListenerContainer container = null;
                try {
                    KafkaConsumerHandlerAdapter adapter = new KafkaConsumerHandlerAdapter(
                            new TopicDispatchingKafkaConsumerHandler(group.handlers, group.registrationIds),
                            idempotencyChecker, errorHandler,
                            deadLetterPublisher, eventListener, group.configuration.getDatasourceKey(),
                            group.configuration.getGroupId());
                    KafkaConsumerContainerContext context = KafkaConsumerContainerContext.builder()
                            .datasourceKey(group.configuration.getDatasourceKey())
                            .groupId(group.configuration.getGroupId())
                            .topics(new ArrayList<>(group.handlers.keySet()))
                            .autoOffsetReset(group.configuration.getAutoOffsetReset())
                            .enableAutoCommit(group.configuration.isEnableAutoCommit())
                            .maxPollRecords(group.configuration.getMaxPollRecords())
                            .concurrency(group.configuration.getConcurrency())
                            .shutdownAwaitMs(group.configuration.getShutdownAwaitMs())
                            .listener(adapter)
                            .consumerFactory(consumerFactory)
                            .build();
                    container = containerFactory.createContainer(context);
                    container.start();
                    created.put(entry.getKey(), new ManagedConsumerContainer(container, consumerFactory,
                            group.configuration.getShutdownAwaitMs()));
                } catch (RuntimeException e) {
                    if (stopContainer(container, group.configuration.getShutdownAwaitMs())) {
                        destroyConsumerFactory(consumerFactory);
                    }
                    throw e;
                }
            }
            return created;
        } catch (RuntimeException e) {
            stopContainers(created.values());
            throw e;
        }
    }

    private Map<ConsumerContainerGroupKey, GroupDefinition> resolveGroups(List<ConsumerRegistration> registrations) {
        Map<ConsumerContainerGroupKey, GroupDefinition> groups = new LinkedHashMap<>();
        Map<ConsumerContainerGroupKey, Set<String>> groupTopics = new LinkedHashMap<>();
        for (ConsumerRegistration registration : registrations) {
            EffectiveConsumerConfiguration configuration = resolveConfiguration(registration);
            ConsumerContainerGroupKey key = new ConsumerContainerGroupKey(configuration);
            Set<String> topics = groupTopics.get(key);
            if (topics == null) {
                topics = new LinkedHashSet<>();
                groupTopics.put(key, topics);
            }
            if (!topics.add(registration.getTopic())) {
                throw configInvalid(String.format(ErrorMessage.CONFIG_INVALID_DUPLICATE_REGISTRATION,
                        configuration.getDatasourceKey(), registration.getTopic()));
            }
            GroupDefinition group = groups.get(key);
            if (group == null) {
                group = new GroupDefinition(configuration);
                groups.put(key, group);
            }
            group.handlers.put(registration.getTopic(), castHandler(registration.getHandler()));
            group.registrationIds.put(registration.getTopic(), registration.getId());
        }
        return groups;
    }

    private EffectiveConsumerConfiguration resolveConfiguration(ConsumerRegistration registration) {
        String datasourceKey = resolveDatasource(registration);
        SimpleKafkaRouteProperties.ConsumerConfig routeConsumer = routeConsumer(datasourceKey);
        return EffectiveConsumerConfiguration.builder()
                .datasourceKey(datasourceKey)
                .groupId(resolveGroupId(registration, routeConsumer))
                .autoOffsetReset(resolveAutoOffsetReset(registration, routeConsumer))
                .enableAutoCommit(resolveEnableAutoCommit(routeConsumer))
                .maxPollRecords(resolveMaxPollRecords(routeConsumer))
                .concurrency(properties.getContainer().getConcurrency())
                .shutdownAwaitMs(properties.getContainer().getShutdownAwaitMs())
                .build();
    }

    private String resolveDatasource(ConsumerRegistration registration) {
        String datasource = KafkaConsumerStringHelper.trimToNull(registration.getDatasource());
        if (datasource != null) {
            if (!routeRegistry.containsDatasource(datasource)) {
                throw configInvalid(String.format(ErrorMessage.CONFIG_INVALID_DATASOURCE_MISSING, datasource));
            }
            return datasource;
        }
        KafkaRouteContext context = KafkaRouteContext.builder()
                .topic(registration.getTopic())
                .routeInput(registration.getTopic())
                .inputType(KafkaRouteInputType.TOPIC)
                .operationType(KafkaRouteOperationType.FACTORY)
                .build();
        try {
            String resolved = routeResolver.resolveDataSource(context);
            if (KafkaConsumerStringHelper.hasText(resolved) && routeRegistry.containsDatasource(resolved)) {
                return resolved;
            }
        } catch (RuntimeException e) {
            throw new KafkaConsumerConfigurationException(ErrorCode.TOPIC_DATASOURCE_UNRESOLVED,
                    String.format(ErrorMessage.TOPIC_DATASOURCE_UNRESOLVED, registration.getTopic(),
                            routeRegistry.getDatasourceKeys()), e);
        }
        throw new KafkaConsumerConfigurationException(ErrorCode.TOPIC_DATASOURCE_UNRESOLVED,
                String.format(ErrorMessage.TOPIC_DATASOURCE_UNRESOLVED, registration.getTopic(),
                        routeRegistry.getDatasourceKeys()));
    }

    private SimpleKafkaRouteProperties.ConsumerConfig routeConsumer(String datasourceKey) {
        SimpleKafkaRouteProperties.DataSourceConfig config = routeProperties.getSources().get(datasourceKey);
        return config == null || config.getConsumer() == null
                ? new SimpleKafkaRouteProperties.ConsumerConfig() : config.getConsumer();
    }

    private String resolveGroupId(ConsumerRegistration registration,
                                  SimpleKafkaRouteProperties.ConsumerConfig routeConsumer) {
        String groupId = KafkaConsumerStringHelper.trimToNull(registration.getGroupId());
        if (groupId != null) {
            return groupId;
        }
        String routeGroupId = KafkaConsumerStringHelper.trimToNull(routeConsumer.getGroupId());
        if (routeGroupId != null) {
            return routeGroupId;
        }
        throw configInvalid(String.format(ErrorMessage.CONFIG_INVALID_GROUP_ID_MISSING, registration.getTopic()));
    }

    private String resolveAutoOffsetReset(ConsumerRegistration registration,
                                          SimpleKafkaRouteProperties.ConsumerConfig routeConsumer) {
        String value = KafkaConsumerStringHelper.trimToNull(registration.getAutoOffsetReset());
        if (value == null) {
            value = KafkaConsumerStringHelper.trimToNull(properties.getContainer().getAutoOffsetReset());
        }
        if (value == null) {
            value = KafkaConsumerStringHelper.trimToNull(routeConsumer.getAutoOffsetReset());
        }
        String resolved = value == null ? SimpleKafkaConsumerConstant.DEFAULT_AUTO_OFFSET_RESET
                : value.toLowerCase(Locale.ROOT);
        if (!isValidAutoOffsetReset(resolved)) {
            throw configInvalid(SimpleKafkaConsumerConstant.REASON_AUTO_OFFSET_RESET_INVALID);
        }
        return resolved;
    }

    private boolean isValidAutoOffsetReset(String value) {
        return SimpleKafkaConsumerConstant.AUTO_OFFSET_RESET_EARLIEST.equals(value)
                || SimpleKafkaConsumerConstant.AUTO_OFFSET_RESET_LATEST.equals(value)
                || SimpleKafkaConsumerConstant.AUTO_OFFSET_RESET_NONE.equals(value);
    }

    private boolean resolveEnableAutoCommit(SimpleKafkaRouteProperties.ConsumerConfig routeConsumer) {
        Boolean configured = properties.getContainer().getEnableAutoCommit();
        boolean resolved = configured != null ? configured : routeConsumer.getEnableAutoCommit() != null
                ? routeConsumer.getEnableAutoCommit() : SimpleKafkaConsumerConstant.DEFAULT_ENABLE_AUTO_COMMIT;
        if (resolved) {
            throw configInvalid(SimpleKafkaConsumerConstant.REASON_AUTO_COMMIT_UNSUPPORTED);
        }
        return false;
    }

    private int resolveMaxPollRecords(SimpleKafkaRouteProperties.ConsumerConfig routeConsumer) {
        Integer configured = properties.getContainer().getMaxPollRecords();
        if (configured != null) {
            return configured;
        }
        int resolved = routeConsumer.getMaxPollRecords() != null
                ? routeConsumer.getMaxPollRecords() : SimpleKafkaConsumerConstant.DEFAULT_MAX_POLL_RECORDS;
        if (resolved <= SimpleKafkaConsumerConstant.ZERO) {
            throw configInvalid(SimpleKafkaConsumerConstant.REASON_MAX_POLL_RECORDS_INVALID);
        }
        return resolved;
    }

    @SuppressWarnings("unchecked")
    private KafkaConsumerHandler<String, String> castHandler(KafkaConsumerHandler<?, ?> handler) {
        return (KafkaConsumerHandler<String, String>) handler;
    }

    private boolean stopContainer(MessageListenerContainer container, long shutdownAwaitMs) {
        if (container == null) {
            return true;
        }
        if (!container.isRunning()) {
            try {
                container.stop();
                return true;
            } catch (RuntimeException e) {
                log.warn("停止未运行消费容器异常，保留派生 ConsumerFactory：container=[{}]", container, e);
                return false;
            }
        }
        CountDownLatch stopped = new CountDownLatch(SimpleKafkaConsumerConstant.FIRST_ATTEMPT);
        try {
            container.stop(stopped::countDown);
        } catch (RuntimeException e) {
            log.warn("停止消费容器异常，保留派生 ConsumerFactory：container=[{}]", container, e);
            return false;
        }
        try {
            if (!stopped.await(shutdownAwaitMs, TimeUnit.MILLISECONDS)) {
                log.warn("消费容器停止超时，保留派生 ConsumerFactory：container=[{}]，timeoutMs=[{}]",
                        container, shutdownAwaitMs);
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("等待消费容器停止被中断，保留派生 ConsumerFactory：container=[{}]", container, e);
            return false;
        }
        if (container.isRunning()) {
            log.warn("消费容器停止回调后仍在运行，保留派生 ConsumerFactory：container=[{}]", container);
            return false;
        }
        return true;
    }

    private void stopContainers(Iterable<ManagedConsumerContainer> containers) {
        List<ManagedConsumerContainer> reversed = new ArrayList<>();
        for (ManagedConsumerContainer container : containers) {
            reversed.add(container);
        }
        Collections.reverse(reversed);
        for (ManagedConsumerContainer container : reversed) {
            if (stopContainer(container.container, container.shutdownAwaitMs)) {
                destroyConsumerFactory(container.consumerFactory);
            }
        }
    }

    private void destroyConsumerFactory(ConsumerFactory<Object, Object> consumerFactory) {
        try {
            KafkaConfigurationCompatibilityHelper.destroyConsumerFactory(consumerFactory);
        } catch (RuntimeException e) {
            log.warn("关闭消费 ConsumerFactory 异常", e);
        }
    }

    private KafkaConsumerConfigurationException configInvalid(String reason) {
        return new KafkaConsumerConfigurationException(ErrorCode.CONFIG_INVALID,
                String.format(ErrorMessage.CONFIG_INVALID, reason));
    }

    private static class GroupDefinition {

        private final EffectiveConsumerConfiguration configuration;
        private final Map<String, KafkaConsumerHandler<String, String>> handlers = new LinkedHashMap<>();
        private final Map<String, String> registrationIds = new LinkedHashMap<>();

        private GroupDefinition(EffectiveConsumerConfiguration configuration) {
            this.configuration = configuration;
        }
    }

    private static class ManagedConsumerContainer {

        private final MessageListenerContainer container;
        private final ConsumerFactory<Object, Object> consumerFactory;
        private final long shutdownAwaitMs;

        private ManagedConsumerContainer(MessageListenerContainer container,
                                         ConsumerFactory<Object, Object> consumerFactory,
                                         long shutdownAwaitMs) {
            this.container = container;
            this.consumerFactory = consumerFactory;
            this.shutdownAwaitMs = shutdownAwaitMs;
        }
    }
}
