package io.github.surezzzzzz.sdk.messaging.kafka.publisher.customizer;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishHeaderContext;

/**
 * Kafka 发布 header 自定义器
 *
 * <p>通过 {@link KafkaPublishHeaderContext#getHeaders()} 增删改本次发送的字符串 header，
 * publisher 会在所有 customizer 执行完毕后统一做 header 名称校验、保留 header 冲突校验并按 UTF-8 编码为 Kafka byte array。
 *
 * <p>限制：
 * <ul>
 *   <li>当 {@code allow-header-override=false}（默认）时，customizer 不能修改或删除任何保留 header
 *       （x-message-id / x-message-type / x-trace-id / x-source / x-published-at 的大小写变体），
 *       否则会抛出 {@code KAFKA_PUBLISHER_009}；需要放开时请在配置中设置 {@code allow-header-override=true}。</li>
 *   <li>header value 不允许为 null，null 语义必须通过删除该 key 表达；空字符串允许，会编码为长度为 0 的字节数组。</li>
 *   <li>header 名称大小写不敏感重复时，{@code allow-header-override=false} 会抛 {@code KAFKA_PUBLISHER_009}。</li>
 * </ul>
 *
 * @author surezzzzzz
 */
public interface KafkaPublishHeaderCustomizer {

    /**
     * 自定义 header
     *
     * @param context header 上下文
     */
    void customize(KafkaPublishHeaderContext context);
}
