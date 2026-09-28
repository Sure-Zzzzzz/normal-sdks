package io.github.surezzzzzz.sdk.auth.aksk.server.service;

import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskOwnerAuthorizationMode;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import lombok.Value;

/**
 * AKSK 在签发或内省时得到的有效授权结论。
 */
@Value
public class AkskEffectiveAuthorizationResult {

    ApplicationAuthorizationContext authorization;
    AkskOwnerAuthorizationMode authorizationMode;
    Long targetApplicationId;
    Long ownerSecurityEpoch;
    Long applicationAuthorizationEpoch;
    String ownerSourceId;
    String ownerSubjectId;
    Long ownerInheritedAccessEpoch;
    Long projectionAccessEpoch;

    public AkskEffectiveAuthorizationResult(ApplicationAuthorizationContext authorization,
                                            AkskOwnerAuthorizationMode authorizationMode,
                                            Long targetApplicationId, Long ownerSecurityEpoch,
                                            Long applicationAuthorizationEpoch, String ownerSourceId,
                                            String ownerSubjectId, Long ownerInheritedAccessEpoch,
                                            Long projectionAccessEpoch) {
        this.authorization = authorization;
        this.authorizationMode = authorizationMode;
        this.targetApplicationId = targetApplicationId;
        this.ownerSecurityEpoch = ownerSecurityEpoch;
        this.applicationAuthorizationEpoch = applicationAuthorizationEpoch;
        this.ownerSourceId = ownerSourceId;
        this.ownerSubjectId = ownerSubjectId;
        this.ownerInheritedAccessEpoch = ownerInheritedAccessEpoch;
        this.projectionAccessEpoch = projectionAccessEpoch;
    }

    public AkskEffectiveAuthorizationResult(ApplicationAuthorizationContext authorization,
                                            AkskOwnerAuthorizationMode authorizationMode,
                                            Long targetApplicationId, Long ownerSecurityEpoch,
                                            Long applicationAuthorizationEpoch, String ownerSourceId,
                                            String ownerSubjectId) {
        this(authorization, authorizationMode, targetApplicationId, ownerSecurityEpoch,
                applicationAuthorizationEpoch, ownerSourceId, ownerSubjectId, null, null);
    }
}
