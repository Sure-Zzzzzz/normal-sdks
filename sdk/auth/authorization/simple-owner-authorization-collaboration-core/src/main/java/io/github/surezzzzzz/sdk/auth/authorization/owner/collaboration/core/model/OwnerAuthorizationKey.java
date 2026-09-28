package io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model;

import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.constant.SimpleOwnerAuthorizationCollaborationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.support.OwnerAuthorizationModelHelper;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 所属人稳定键。
 *
 * <p>外部所属主体的稳定键；subject 值不携带身份源内部主键语义。</p>
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
public final class OwnerAuthorizationKey {

    /**
     * 身份源标识。
     */
    private final String ownerSourceId;
    /**
     * 身份源内稳定主体标识。
     */
    private final String ownerSubjectId;

    /**
     * 创建所属人稳定键。
     *
     * @param ownerSourceId  身份源标识
     * @param ownerSubjectId 身份源内稳定主体标识
     */
    public OwnerAuthorizationKey(String ownerSourceId, String ownerSubjectId) {
        this.ownerSourceId = OwnerAuthorizationModelHelper.requireText(ownerSourceId,
                SimpleOwnerAuthorizationCollaborationConstant.FIELD_OWNER_SOURCE_ID);
        this.ownerSubjectId = OwnerAuthorizationModelHelper.requireText(ownerSubjectId,
                SimpleOwnerAuthorizationCollaborationConstant.FIELD_OWNER_SUBJECT_ID);
    }
}
