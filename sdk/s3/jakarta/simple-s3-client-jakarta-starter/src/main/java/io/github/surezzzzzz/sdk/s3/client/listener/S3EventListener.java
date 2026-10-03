package io.github.surezzzzzz.sdk.s3.client.listener;

import io.github.surezzzzzz.sdk.s3.client.model.S3Event;

/**
 * S3 事件回调监听器。业务注册为实现本接口的 Bean，由事件回调接收端点
 * 在完成认证与解析后按 Order 顺序分发。
 *
 * <p>S3 事件通知为至少一次投递：重复推送的去重是监听器的责任
 * （{@code S3Event} 的 sequencer 字段即为此设计）。监听器抛出异常时
 * 接收端点返回 5xx 触发存储侧重投。</p>
 *
 * @author surezzzzzz
 */
public interface S3EventListener {

    /**
     * 处理一条已解析的 S3 事件。
     *
     * @param event 事件（records 内可能包含多条记录）
     */
    void onEvent(S3Event event);
}
