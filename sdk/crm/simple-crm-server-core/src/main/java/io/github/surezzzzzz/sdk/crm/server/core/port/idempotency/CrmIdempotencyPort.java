package io.github.surezzzzzz.sdk.crm.server.core.port.idempotency;

import io.github.surezzzzzz.sdk.crm.server.core.command.CrmCommandMetadata;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmActor;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmCommandType;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmResourceType;

/**
 * 命令幂等事实端口。
 *
 * <p>实现必须按 tenant、actor、命令类型、目标聚合和幂等键唯一化，并校验规范化请求摘要。</p>
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
