package io.github.surezzzzzz.sdk.elasticsearch.persistence.configuration;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.SimpleElasticsearchPersistencePackage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.annotation.SimpleElasticsearchPersistenceComponent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.classifier.BulkFailureClassifier;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.classifier.DefaultBulkFailureClassifier;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.SimpleElasticsearchPersistenceConstant;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.*;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.DefaultPersistenceEngine;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.PersistenceEngine;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.executor.PersistenceExecutor;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.executor.PersistenceExecutorRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.executor.RestPersistenceExecutor;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.model.request.TaskQueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentPreProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentPreProcessorChain;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.*;
import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteConfiguration;
import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.WriteIndexResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.PersistenceProtocolConstant.VALUE_TRUE;

/**
 * 在 Route 之后装配；启用但缺少托管路由时直接启动失败，不静默降级。
 */
@AutoConfiguration(after = SimpleElasticsearchRouteConfiguration.class)
@ConditionalOnProperty(prefix = SimpleElasticsearchPersistenceConstant.CONFIG_PREFIX,
        name = SimpleElasticsearchPersistenceConstant.CONFIG_ENABLE, havingValue = VALUE_TRUE)
@EnableConfigurationProperties(SimpleElasticsearchPersistenceProperties.class)
@ComponentScan(basePackageClasses = SimpleElasticsearchPersistencePackage.class, useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(SimpleElasticsearchPersistenceComponent.class))
public class SimpleElasticsearchPersistenceAutoConfiguration {

    /**
     * 默认 JSON 边界不依赖应用 ObjectMapper。
     */
    @Bean
    @ConditionalOnMissingBean
    public PersistencePayloadCodec persistencePayloadCodec() {
        return new JacksonPersistencePayloadCodec();
    }

    /**
     * 处理器按 Spring 排序，调用方可替换整条链。
     */
    @Bean
    @ConditionalOnMissingBean
    public DocumentPreProcessorChain documentPreProcessorChain(ObjectProvider<DocumentPreProcessor> processors) {
        return new DocumentPreProcessorChain(processors.orderedStream().collect(Collectors.toList()));
    }

    /**
     * 默认状态码重试分类器。
     */
    @Bean
    @ConditionalOnMissingBean
    public BulkFailureClassifier bulkFailureClassifier() {
        return new DefaultBulkFailureClassifier();
    }

    /**
     * 索引解析只能使用 Route 的声明与渲染器。
     */
    @Bean
    @ConditionalOnMissingBean
    public PersistenceTargetResolver persistenceTargetResolver(RouteResolver routes, WriteIndexResolver writes,
                                                               SimpleElasticsearchRouteProperties properties) {
        return new PersistenceTargetResolver(routes, writes, properties);
    }

    /**
     * REST 适配器只借用托管连接。
     */
    @Bean
    @ConditionalOnMissingBean
    public PersistenceRestHelper persistenceRestHelper(SimpleElasticsearchRouteRegistry registry, PersistencePayloadCodec codec) {
        return new PersistenceRestHelper(registry, codec);
    }

    /**
     * 协议编译器拥有预检、结果和事件转换。
     */
    @Bean
    @ConditionalOnMissingBean
    public ElasticsearchWriteApiHelper elasticsearchWriteApiHelper(PersistenceTargetResolver targets,
                                                                   PersistencePayloadCodec codec, PersistenceRestHelper rest, DocumentPreProcessorChain processors,
                                                                   BulkFailureClassifier classifier, SimpleElasticsearchPersistenceProperties properties, ApplicationEventPublisher publisher) {
        if (properties.getByQuery() == null) throw PersistenceTargetResolver.invalid();
        return new ElasticsearchWriteApiHelper(targets, codec, rest, processors, classifier, properties, publisher);
    }

    /**
     * 先收集扩展执行器，再补缺省类型；重复自定义注册由注册表拒绝。
     */
    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings({"rawtypes", "unchecked"})
    public PersistenceExecutorRegistry persistenceExecutorRegistry(ElasticsearchWriteApiHelper helper,
                                                                   ObjectProvider<PersistenceExecutor<?, ?>> extensions) {
        List<PersistenceExecutor<?, ?>> values = extensions.orderedStream().collect(Collectors.toCollection(ArrayList::new));
        for (Class type : Arrays.asList(IndexRequest.class, UpdateRequest.class, DeleteRequest.class, BulkRequest.class,
                UpdateByQueryRequest.class, DeleteByQueryRequest.class, TaskQueryRequest.class)) {
            if (values.stream().noneMatch(value -> value.getRequestType() == type))
                values.add(new RestPersistenceExecutor(type, helper));
        }
        return new PersistenceExecutorRegistry(values);
    }

    /**
     * 有界客户端线程池由 Spring 关闭；用户同名 Executor 时让位。
     */
    @Bean(name = SimpleElasticsearchPersistenceConstant.ASYNC_EXECUTOR_BEAN_NAME)
    @ConditionalOnMissingBean(name = SimpleElasticsearchPersistenceConstant.ASYNC_EXECUTOR_BEAN_NAME)
    public ThreadPoolTaskExecutor esPersistenceAsyncExecutor(SimpleElasticsearchPersistenceProperties properties) {
        SimpleElasticsearchPersistenceProperties.Async value = properties.getAsync();
        if (value == null || value.getCoreSize() <= 0 || value.getMaxSize() < value.getCoreSize() || value.getQueueCapacity() < 0)
            throw PersistenceTargetResolver.invalid();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(value.getCoreSize());
        executor.setMaxPoolSize(value.getMaxSize());
        executor.setQueueCapacity(value.getQueueCapacity());
        executor.setThreadNamePrefix(SimpleElasticsearchPersistenceConstant.ASYNC_EXECUTOR_THREAD_NAME_PREFIX);
        return executor;
    }

    /**
     * 业务门面允许按接口整体验证和替换。
     */
    @Bean
    @ConditionalOnMissingBean(PersistenceEngine.class)
    public PersistenceEngine persistenceEngine(PersistenceExecutorRegistry registry,
                                               @Qualifier(SimpleElasticsearchPersistenceConstant.ASYNC_EXECUTOR_BEAN_NAME) Executor executor,
                                               ElasticsearchWriteApiHelper helper) {
        return new DefaultPersistenceEngine(registry, executor, helper);
    }
}
