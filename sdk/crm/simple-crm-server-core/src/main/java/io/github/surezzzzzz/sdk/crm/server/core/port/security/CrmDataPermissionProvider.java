package io.github.surezzzzzz.sdk.crm.server.core.port.security;

import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmActor;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmAction;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmResourceType;

/**
 * CRM 数据范围裁决端口。
 *
 * <p>Server 适配器必须将受限范围完整翻译到读取和原子写入条件；无法翻译时拒绝。</p>
 *
 * @author surezzzzzz
 */
public interface CrmDataPermissionProvider {

    /**
     * 校验 CRM 操作或数据权限。
     *
     * @param actor        已认证且绑定租户的操作者
     * @param action       待授权的 CRM 操作
     * @param resourceType 资源类型
     * @param resourceId   resourceId参数。
     * @return 处理后的领域事实或校验结果。
     */
    void authorize(CrmActor actor, CrmAction action, CrmResourceType resourceType, String resourceId);
}
