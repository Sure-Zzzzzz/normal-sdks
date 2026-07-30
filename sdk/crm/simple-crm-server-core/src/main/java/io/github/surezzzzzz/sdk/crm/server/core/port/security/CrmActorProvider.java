package io.github.surezzzzzz.sdk.crm.server.core.port.security;

import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmActor;

/**
 * CRM 已认证操作者提供者。
 *
 * @author surezzzzzz
 */
public interface CrmActorProvider {

    /**
     * 获取当前已认证 CRM 操作者。
     *
     * @return 处理后的领域事实或校验结果。
     */
    CrmActor currentActor();
}
