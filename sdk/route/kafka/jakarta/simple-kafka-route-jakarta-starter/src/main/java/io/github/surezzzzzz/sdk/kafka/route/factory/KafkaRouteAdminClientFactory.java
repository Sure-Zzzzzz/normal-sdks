package io.github.surezzzzzz.sdk.kafka.route.factory;

/**
 * Kafka route AdminClient 资源工厂
 *
 * @author surezzzzzz
 */
public interface KafkaRouteAdminClientFactory {

    /**
     * 按数据源在短生命周期 AdminClient 内执行回调。
     *
     * <p>回调结束后 Route 自动关闭 AdminClient；调用方不得缓存或向异步流程传递该客户端，且必须在
     * 回调内完成所有依赖客户端的异步请求。空白或未知数据源沿用 Route 的数据源不存在错误语义。</p>
     *
     * @param datasourceKey 数据源标识
     * @param callback      AdminClient 回调，不能为空
     * @param <T>           回调结果类型
     * @return 已在回调内完成的回调结果
     */
    <T> T withAdminClient(String datasourceKey, KafkaRouteAdminClientCallback<T> callback);
}
