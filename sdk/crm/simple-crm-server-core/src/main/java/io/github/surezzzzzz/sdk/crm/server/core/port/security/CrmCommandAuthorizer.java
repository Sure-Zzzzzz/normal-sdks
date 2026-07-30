package io.github.surezzzzzz.sdk.crm.server.core.port.security;

import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmActor;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmAction;

/**
 * CRM 业务动作授权端口。
 *
 * <p>无法明确允许时实现必须抛出拒绝异常。</p>
 *
 * @author surezzzzzz
 */
public interface CrmCommandAuthorizer {

    /**
     * 校验 CRM 操作或数据权限。
     *
     * @param actor  已认证且绑定租户的操作者
     * @param action 待授权的 CRM 操作
     * @return 处理后的领域事实或校验结果。
     */
    void authorize(CrmActor actor, CrmAction action);
}
