package io.github.surezzzzzz.sdk.messaging.kafka.publisher.customizer;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishEnvelopeContext;

/**
 * Kafka 发布 envelope 自定义器
 *
 * <p>用于补充 envelope 的 attributes，{@code context} 不暴露 payload，因此不允许也不支持修改 payload。
 * customizer 通过 {@link KafkaPublishEnvelopeContext#getAttributes()} 返回的可变 Map 增删改 attributes，
 * publisher 会在所有 customizer 执行完毕后将该 Map 显式回写到 envelope 再序列化，因此 customizer 只需操作 context，
 * 不应依赖与 envelope 内部 Map 的引用共享关系。
 *
 * @author surezzzzzz
 */
public interface KafkaPublishEnvelopeCustomizer {

    /**
     * 自定义 envelope
     *
     * @param context envelope 自定义上下文
     */
    void customize(KafkaPublishEnvelopeContext context);
}
