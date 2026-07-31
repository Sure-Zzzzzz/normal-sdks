package io.github.surezzzzzz.sdk.crm.server.core.port.idempotency;

import io.github.surezzzzzz.sdk.crm.server.core.command.CrmCommandMetadata;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmActor;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmCommandType;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmResourceType;

/**
 * 已知目标资源命令幂等事实端口。
 *
 * <p>仅适用于执行前已经确定目标资源 ID 的命令，例如签发报价和确认报价。实现必须按 tenant、actor、
 * 命令类型、目标资源类型、目标资源 ID 和幂等键唯一化，并校验规范化请求摘要。</p>
 *
 * <p>服务端生成顶级资源 ID 的创建命令必须使用 {@link CrmCreateIdempotencyPort}，不得以未知或临时生成的
 * 目标资源 ID 调用本端口。报价签发和确认需要可重放声明结果时，运行时适配器必须实现并使用
 * {@link CrmReplayableIdempotencyPort}；本接口保留已发布方法的源码和二进制兼容，不能单独表达成功重放结果。</p>
 *
 * @author surezzzzzz
 */
public interface CrmIdempotencyPort {

    <T> T execute(CrmActor actor, CrmCommandMetadata metadata, CrmCommandType commandType,
                  CrmResourceType targetResourceType, String targetResourceId, String requestDigest,
                  CrmIdempotentCallback<T> callback);

    interface CrmIdempotentCallback<T> {
        /**
         * 执行幂等业务操作。
         *
         * @return 处理后的领域事实或校验结果。
         */
        T execute();
    }
}
