package io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model;

import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.constant.SimpleOwnerAuthorizationCollaborationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.support.OwnerAuthorizationModelHelper;
import lombok.Getter;

/**
 * 所属人授权候选应用。
 *
 * <p>所属人自助创建时可选择的最小目标应用目录项。</p>
 *
 * @author surezzzzzz
 */
@Getter
public final class OwnerAuthorizationCandidate {

    /**
     * 目标应用标识。
     */
    private final Long applicationId;
    /**
     * 目标应用展示名。
     */
    private final String applicationName;
    /**
     * 目标应用标识快照。
     */
    private final String applicationCodeSnapshot;

    /**
     * 创建候选应用目录项。
     *
     * @param applicationId           目标应用标识
     * @param applicationName         目标应用展示名
     * @param applicationCodeSnapshot 目标应用标识快照
     */
    public OwnerAuthorizationCandidate(Long applicationId, String applicationName,
                                       String applicationCodeSnapshot) {
        this.applicationId = OwnerAuthorizationModelHelper.requirePositive(applicationId,
                SimpleOwnerAuthorizationCollaborationConstant.FIELD_APPLICATION_ID);
        this.applicationName = OwnerAuthorizationModelHelper.requireText(applicationName,
                SimpleOwnerAuthorizationCollaborationConstant.FIELD_APPLICATION_NAME);
        this.applicationCodeSnapshot = OwnerAuthorizationModelHelper.requireText(applicationCodeSnapshot,
                SimpleOwnerAuthorizationCollaborationConstant.FIELD_APPLICATION_CODE_SNAPSHOT);
    }

}
