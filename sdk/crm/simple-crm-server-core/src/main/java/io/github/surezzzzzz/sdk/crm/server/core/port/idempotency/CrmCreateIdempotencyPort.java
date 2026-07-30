package io.github.surezzzzzz.sdk.crm.server.core.port.idempotency;

import io.github.surezzzzzz.sdk.crm.server.core.command.CrmCommandMetadata;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmActor;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmCommandType;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmResourceType;

/**
 * 创建命令幂等事实端口。
 *
 * <p>仅适用于执行前尚未生成顶级资源 ID 的创建命令：CREATE_CUSTOMER/CUSTOMER、CREATE_CONTACT/CONTACT、
 * CREATE_OFFERING/OFFERING、CREATE_QUOTATION/QUOTATION。实现必须拒绝其他命令或资源类型配对，并按 tenant、
 * actor、命令类型、目标资源类型和幂等键唯一化，不能依赖尚未生成的目标资源 ID。</p>
 *
 * <p>同一稳定范围和同一规范化请求摘要必须返回首个成功创建时保存的顶级资源 ID，且不得再次执行回调、
 * 生成 ID 或重复写入业务事实、审计与 Outbox；同一稳定范围使用不同摘要时，必须抛出
 * {@link io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode#IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD}。
 * 首次成功必须在同一权威事务中保存业务事实、顶级资源 ID 和幂等成功结果。回调或事务失败时不得保留可重放的成功记录，
 * 并发首次请求必须原子收敛为同一个资源和成功结果。</p>
 *
 * <p>调用方在首次成功和重放后均须按返回 ID、当前 tenant 与数据范围重新加载声明结果；若成功记录存在但资源不可重建，
 * 不得再次创建。事务、唯一约束、并发控制、锁、重试和请求摘要规范化由外部适配器实现。</p>
 *
 * @author surezzzzzz
 */
public interface CrmCreateIdempotencyPort {

    /**
     * 执行服务端生成顶级资源 ID 的创建命令。
     *
     * @param actor              已认证且绑定租户的操作者
     * @param metadata           命令关联与幂等元数据
     * @param commandType        创建命令类型
     * @param targetResourceType 创建的顶级资源类型
     * @param requestDigest      规范化请求摘要
     * @param callback           首次成功时执行并返回已持久化顶级资源 ID 的回调
     * @return 首次保存或成功重放的顶级资源 ID
     */
    String execute(CrmActor actor, CrmCommandMetadata metadata, CrmCommandType commandType,
                   CrmResourceType targetResourceType, String requestDigest,
                   CrmCreateIdempotentCallback callback);

    /**
     * 首次创建回调。
     *
     * @author surezzzzzz
     */
    interface CrmCreateIdempotentCallback {

        /**
         * 执行首次创建并返回已持久化的顶级资源 ID。
         *
         * @return 已持久化的顶级资源 ID
         */
        String execute();
    }
}
