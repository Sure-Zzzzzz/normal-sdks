package io.github.surezzzzzz.sdk.kafka.route.factory;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import io.github.surezzzzzz.sdk.kafka.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.kafka.route.exception.RouteException;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaAdminCompatibilityHelper;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaRoutePropertyMerger;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.DisposableBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 默认 Kafka route AdminClient 资源工厂
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultKafkaRouteAdminClientFactory implements KafkaRouteAdminClientFactory, DisposableBean {

    /**
     * 用于保持 datasource key 校验语义与 registry 一致
     */
    private final SimpleKafkaRouteRegistry registry;

    /**
     * 构造期创建的不可变 AdminClient 配置快照
     */
    private final Map<String, Map<String, Object>> adminClientProperties;

    /**
     * AdminClient 创建与关闭协作者
     */
    private final KafkaRouteAdminClientOperations operations;

    /**
     * 协调资源创建与工厂关闭的生命周期锁
     */
    private final Object lifecycleMonitor = new Object();

    /**
     * 是否已停止接受新的 AdminClient 回调
     */
    private boolean destroyed = false;

    /**
     * 创建默认 Kafka route AdminClient 资源工厂
     *
     * @param properties Kafka route 配置
     * @param registry   Kafka route 注册表
     */
    public DefaultKafkaRouteAdminClientFactory(SimpleKafkaRouteProperties properties,
                                               SimpleKafkaRouteRegistry registry) {
        this(properties, registry, new DefaultKafkaRouteAdminClientOperations());
    }

    /**
     * 创建带内部资源协作者的 AdminClient 资源工厂。
     *
     * <p>仅在包内用于隔离第三方客户端资源操作，不构成调用方可替换的 SPI。</p>
     *
     * @param properties Kafka route 配置
     * @param registry   Kafka route 注册表
     * @param operations AdminClient 创建与关闭协作者
     */
    DefaultKafkaRouteAdminClientFactory(SimpleKafkaRouteProperties properties,
                                        SimpleKafkaRouteRegistry registry,
                                        KafkaRouteAdminClientOperations operations) {
        this.registry = registry;
        this.operations = operations;
        this.adminClientProperties = createAdminClientProperties(properties);
    }

    /**
     * 按数据源在短生命周期 AdminClient 内执行回调。
     *
     * <p>客户端只在 callback 的动态作用域内有效。工厂停止后仅拒绝新调用，已经创建的客户端仍由
     * 当前回调在 finally 中关闭。</p>
     *
     * @param datasourceKey 数据源标识
     * @param callback      AdminClient 回调
     * @param <T>           回调结果类型
     * @return 回调结果
     */
    @Override
    public <T> T withAdminClient(String datasourceKey, KafkaRouteAdminClientCallback<T> callback) {
        assertCallback(callback);
        AdminClient adminClient = null;
        synchronized (lifecycleMonitor) {
            assertNotDestroyed(datasourceKey);
            registry.getProducerFactory(datasourceKey);
            Map<String, Object> properties = adminClientProperties.get(datasourceKey);
            if (properties == null) {
                throw datasourceNotFound(datasourceKey);
            }
            try {
                adminClient = operations.create(new LinkedHashMap<>(properties));
                if (adminClient == null) {
                    throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_006,
                            String.format(ErrorMessage.DATASOURCE_CREATE_FAILED, datasourceKey));
                }
            } catch (ConfigurationException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_006,
                        String.format(ErrorMessage.DATASOURCE_CREATE_FAILED, datasourceKey));
            }
        }
        try {
            return callback.doWithAdminClient(adminClient);
        } finally {
            closeAdminClient(adminClient);
        }
    }

    /**
     * 停止接受新的 AdminClient 回调，不抢占已创建的客户端
     */
    @Override
    public void destroy() {
        synchronized (lifecycleMonitor) {
            destroyed = true;
        }
    }

    /**
     * 创建 datasource 级不可变 AdminClient 配置快照
     *
     * @param properties Kafka route 配置
     * @return AdminClient 配置快照
     */
    private Map<String, Map<String, Object>> createAdminClientProperties(SimpleKafkaRouteProperties properties) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, SimpleKafkaRouteProperties.DataSourceConfig> entry : properties.getSources().entrySet()) {
            SimpleKafkaRouteProperties.DataSourceConfig config = entry.getValue();
            Map<String, Object> adminProperties = new LinkedHashMap<>(
                    KafkaRoutePropertyMerger.mergeBaseProperties(entry.getKey(), config));
            adminProperties.put(SimpleKafkaRouteConstant.PROPERTY_BOOTSTRAP_SERVERS,
                    Collections.unmodifiableList(new ArrayList<>(config.getBootstrapServers())));
            result.put(entry.getKey(), Collections.unmodifiableMap(adminProperties));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 校验 AdminClient 回调
     *
     * @param callback AdminClient 回调
     */
    private void assertCallback(KafkaRouteAdminClientCallback<?> callback) {
        if (callback == null) {
            throw new RouteException(ErrorCode.KAFKA_ROUTE_010, ErrorMessage.CALLBACK_EMPTY);
        }
    }

    /**
     * 校验资源工厂仍可接受新调用
     *
     * @param datasourceKey 数据源标识
     */
    private void assertNotDestroyed(String datasourceKey) {
        if (destroyed) {
            throw new ConfigurationException(ErrorCode.KAFKA_ROUTE_017,
                    String.format(ErrorMessage.ADMIN_CLIENT_FACTORY_DESTROYED, datasourceKey));
        }
    }

    /**
     * 创建数据源不存在异常
     *
     * @param datasourceKey 数据源标识
     * @return 数据源不存在异常
     */
    private RouteException datasourceNotFound(String datasourceKey) {
        return new RouteException(ErrorCode.KAFKA_ROUTE_003,
                String.format(ErrorMessage.DATASOURCE_NOT_FOUND, datasourceKey, registry.getDatasourceKeys()));
    }

    /**
     * 关闭当前回调独占的 AdminClient。
     *
     * <p>关闭阶段的 RuntimeException 不得替代 callback 的正常结果或主异常；Error 仍按 JVM 语义传播。</p>
     *
     * @param adminClient AdminClient
     */
    private void closeAdminClient(AdminClient adminClient) {
        if (adminClient == null) {
            return;
        }
        try {
            operations.close(adminClient);
        } catch (RuntimeException e) {
            log.warn("Kafka route AdminClient 关闭失败，exception=[{}]", e.getClass().getSimpleName());
        }
    }

    /**
     * 默认 AdminClient 创建与关闭协作者
     */
    private static class DefaultKafkaRouteAdminClientOperations implements KafkaRouteAdminClientOperations {

        @Override
        public AdminClient create(Map<String, Object> properties) {
            return (AdminClient) KafkaAdminCompatibilityHelper.createAdminClient(properties);
        }

        @Override
        public void close(AdminClient adminClient) {
            KafkaAdminCompatibilityHelper.closeAdminClient(adminClient);
        }
    }
}
