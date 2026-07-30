package io.github.surezzzzzz.sdk.crm.server.core.port.security;

import io.github.surezzzzzz.sdk.crm.server.core.domain.audit.CrmOperationType;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmActor;

/**
 * CRM 高危运行操作授权端口。
 *
 * @author surezzzzzz
 */
public interface CrmOperationsAuthorizer {

    /**
     * 校验 CRM 操作或数据权限。
     *
     * @param actor         已认证且绑定租户的操作者
     * @param operationType operationType参数。
     * @param tenantId      租户唯一标识
     * @param reasonCode    reasonCode参数。
     * @return 处理后的领域事实或校验结果。
     */
    void authorize(CrmActor actor, CrmOperationType operationType, String tenantId, String reasonCode);
}
