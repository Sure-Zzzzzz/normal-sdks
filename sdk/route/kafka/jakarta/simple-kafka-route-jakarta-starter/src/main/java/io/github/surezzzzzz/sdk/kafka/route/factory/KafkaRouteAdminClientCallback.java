package io.github.surezzzzzz.sdk.kafka.route.factory;

import org.apache.kafka.clients.admin.AdminClient;

/**
 * Kafka route AdminClient 回调
 *
 * @param <T> 回调结果类型
 * @author surezzzzzz
 */
@FunctionalInterface
public interface KafkaRouteAdminClientCallback<T> {

    /**
     * 在短生命周期 AdminClient 可用期间执行操作。
     *
     * <p>不得缓存、传递或在回调返回后继续使用 {@code adminClient}。异步 Admin 请求必须在本回调内
     * 完成并转换为业务结果，不能将未完成的 {@code KafkaFuture}、{@code CompletionStage} 或其他
     * 依赖该客户端的异步对象作为返回值。</p>
     *
     * @param adminClient AdminClient
     * @return 已在回调内完成的业务结果
     */
    T doWithAdminClient(AdminClient adminClient);
}
