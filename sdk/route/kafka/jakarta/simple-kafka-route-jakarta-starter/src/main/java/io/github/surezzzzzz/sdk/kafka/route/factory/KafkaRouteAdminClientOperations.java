package io.github.surezzzzzz.sdk.kafka.route.factory;

import org.apache.kafka.clients.admin.AdminClient;

import java.util.Map;

/**
 * Kafka route AdminClient 内部资源操作。
 *
 * <p>仅用于默认工厂隔离客户端创建与关闭，既不是公开 SPI，也不参与 Spring Bean 注册。</p>
 *
 * @author surezzzzzz
 */
interface KafkaRouteAdminClientOperations {

    /**
     * 使用工厂私有的配置副本创建 AdminClient。
     *
     * @param properties Kafka 配置
     * @return AdminClient
     */
    AdminClient create(Map<String, Object> properties);

    /**
     * 关闭当前回调独占的 AdminClient。
     *
     * @param adminClient AdminClient
     */
    void close(AdminClient adminClient);
}
