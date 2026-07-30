package io.github.surezzzzzz.sdk.crm.server.core.domain.identity;

import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 已认证且已绑定租户的 CRM 操作者。
 *
 * @author surezzzzzz
 */
@Getter
public final class CrmActor {

    private final String tenantId;
    private final String actorId;
    private final String displayName;

    /**
     * 创建CrmActor。
     *
     * @param tenantId    租户唯一标识
     * @param actorId     执行操作的操作者标识
     * @param displayName 展示名称
     *
     */
    public CrmActor(String tenantId, String actorId, String displayName) {
        this.tenantId = CrmValidationHelper.required(tenantId, "tenantId");
        this.actorId = CrmValidationHelper.required(actorId, "actorId");
        this.displayName = CrmValidationHelper.optional(displayName);
    }


}
