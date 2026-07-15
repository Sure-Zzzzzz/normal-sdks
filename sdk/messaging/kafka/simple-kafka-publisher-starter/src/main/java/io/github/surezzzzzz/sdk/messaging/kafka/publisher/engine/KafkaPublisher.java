package io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishResult;
import org.springframework.util.concurrent.ListenableFuture;

/**
 * Kafka 发布门面
 *
 * @author surezzzzzz
 */
public interface KafkaPublisher {

    /**
     * 指定最终消息 topic 异步发布，由 route 按 topic 规则选择 datasource
     *
     * <p>本方法不读取 message routeKey/datasourceKey，也不调用 routeKey resolver。
     *
     * @param topic   最终消息 topic
     * @param payload payload
     * @param <T>     payload 类型
     * @return 发布结果 Future；发送失败时以 {@link io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishException} 完成
     */
    <T> ListenableFuture<KafkaPublishResult> publish(String topic, T payload);

    /**
     * 指定最终消息 topic 和 record key 异步发布，由 route 按 topic 规则选择 datasource
     *
     * <p>本方法不读取 message routeKey/datasourceKey，也不调用 routeKey resolver。
     *
     * @param topic   最终消息 topic
     * @param key     Kafka record key
     * @param payload payload
     * @param <T>     payload 类型
     * @return 发布结果 Future；发送失败时以 {@link io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishException} 完成
     */
    <T> ListenableFuture<KafkaPublishResult> publish(String topic, String key, T payload);

    /**
     * 按消息字段异步发布
     *
     * <p>datasource 选择优先级固定为：message.datasourceKey 显式 datasource、
     * routeKey resolver 解析结果、最终消息 topic 路由规则。routeKey 只用于选择 datasource，不改变最终消息 topic。
     *
     * @param message 发布消息
     * @param <T>     payload 类型
     * @return 发布结果 Future；发送失败时以 {@link io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishException} 完成
     */
    <T> ListenableFuture<KafkaPublishResult> publish(KafkaPublishMessage<T> message);

    /**
     * 指定 routeKey 异步发布，由 route 按 routeKey 规则选择 datasource
     *
     * <p>routeKey 只用于选择 datasource，不改变 message 中的最终消息 topic；本方法忽略 message.datasourceKey，
     * 且不调用 routeKey resolver。
     *
     * @param routeKey 用于选择 datasource 的 routeKey
     * @param message  发布消息，必须提供最终消息 topic 或可解析出 topic
     * @param <T>      payload 类型
     * @return 发布结果 Future；发送失败时以 {@link io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishException} 完成
     */
    <T> ListenableFuture<KafkaPublishResult> publishByRouteKey(String routeKey, KafkaPublishMessage<T> message);

    /**
     * 指定 datasource 异步发布
     *
     * <p>本方法绕过 route 的 topic/routeKey 规则，忽略 message.routeKey，且不调用 routeKey resolver；
     * message 中的 topic 仍是最终消息 topic。
     *
     * @param datasourceKey 目标 datasource key
     * @param message       发布消息，必须提供最终消息 topic 或可解析出 topic
     * @param <T>           payload 类型
     * @return 发布结果 Future；发送失败时以 {@link io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishException} 完成
     */
    <T> ListenableFuture<KafkaPublishResult> publishOn(String datasourceKey, KafkaPublishMessage<T> message);

    /**
     * 按消息字段同步发布并等待 broker 发送结果
     *
     * <p>datasource 选择语义与 {@link #publish(KafkaPublishMessage)} 一致，仅本方法使用 send.timeout-ms。
     * 等待超时抛 KAFKA_PUBLISHER_008，等待被中断抛 KAFKA_PUBLISHER_011；两种情况都表示发送状态未知，
     * 调用方不应盲目重试，以免重复投递。
     *
     * @param message 发布消息
     * @param <T>     payload 类型
     * @return broker 确认后的发布结果
     */
    <T> KafkaPublishResult publishAndWait(KafkaPublishMessage<T> message);
}
