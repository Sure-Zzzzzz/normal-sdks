package io.github.surezzzzzz.sdk.crm.server.core.port.idempotency;

import io.github.surezzzzzz.sdk.crm.server.core.command.CrmCommandMetadata;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmActor;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmCommandType;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmResourceType;

/**
 * 可重放已知目标资源命令幂等事实端口。
 *
 * <p>仅适用于执行前已经确定目标报价 ID 的命令：ISSUE_QUOTATION/QUOTATION、CONFIRM_QUOTATION/QUOTATION。
 * 实现必须拒绝其他命令或资源类型配对。首次请求只能执行首次回调；同一稳定范围与请求摘要重放时，必须仅执行
 * 重放回调，由其按当前 tenant 与数据范围加载已提交事实并重建声明结果；确认报价时必须通过
 * {@link io.github.surezzzzzz.sdk.crm.server.core.port.repository.ReplayableFulfillmentItemRepository}
 * 读取完整履约项集合。重放绝不重执首次回调、状态迁移、订单创建、审计或 Outbox 写入。</p>
 *
 * <p>实现必须在同一权威事务内保存业务事实与幂等成功事实；同一稳定范围使用不同摘要时必须抛出
 * {@link io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode#IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD}；
 * 首次回调或其事务失败时不得留下可重放成功结果；重放回调或重放事务失败时不得写入或修改既有幂等成功事实；
 * 并发首次请求必须原子收敛。</p>
 *
 * @author surezzzzzz
 */
public interface CrmReplayableIdempotencyPort extends CrmIdempotencyPort {

    /**
     * 执行可重放的已知目标资源命令。
     *
     * @param actor              已认证且绑定租户的操作者
     * @param metadata           命令关联与幂等元数据
     * @param commandType        已知目标资源命令类型
     * @param targetResourceType 目标资源类型
     * @param targetResourceId   目标资源唯一标识
     * @param requestDigest      规范化请求摘要
     * @param callback           首次成功时执行的业务回调
     * @param replayCallback     成功重放时按已提交事实重建结果的回调
     * @param <T>                领域声明结果类型
     * @return 首次成功或重放重建的声明结果
     */
    <T> T execute(CrmActor actor, CrmCommandMetadata metadata, CrmCommandType commandType,
                  CrmResourceType targetResourceType, String targetResourceId, String requestDigest,
                  CrmIdempotentCallback<T> callback, CrmIdempotentReplayCallback<T> replayCallback);

    /**
     * 成功重放结果重建回调。
     *
     * @param <T> 领域声明结果类型
     * @author surezzzzzz
     */
    interface CrmIdempotentReplayCallback<T> {

        /**
         * 按已提交权威事实重建首个成功结果。
         *
         * @return 重建后的领域声明结果
         */
        T execute();
    }
}
