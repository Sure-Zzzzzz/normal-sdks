package io.github.surezzzzzz.sdk.license.server.service;

import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.core.model.EligibilityPolicy;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.server.constant.GrantState;
import io.github.surezzzzzz.sdk.license.server.entity.GrantAggregate;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.RevocationRepository;
import io.github.surezzzzzz.sdk.license.server.support.GrantValidityHelper;
import java.util.Optional;

/**
 * OnlineDecisionService.GrantRegistry 的 JPA 生产实现：
 * 授权行装载 + 吊销台账终态复查（吊销优先于一切管理状态）。
 *
 * @author surezzzzzz
 */
@SimpleLicenseServerComponent
public class JpaGrantRegistry implements OnlineDecisionService.GrantRegistry {

    private final GrantRepository grants;
    private final RevocationRepository revocations;

    public JpaGrantRegistry(GrantRepository grants, RevocationRepository revocations) {
        this.grants = grants;
        this.revocations = revocations;
    }

    @Override
    public Optional<GrantAggregate> findById(String grantId) {
        GrantRevisionEntity entity = grants.findById(grantId).orElse(null);
        if (entity == null) {
            return Optional.empty();
        }
        // 吊销台账优先：不可撤回吊销的授权按 REVOKED 终态装载（在线端即时生效）
        boolean revoked = revocations.findByTargetKindAndTargetId("GRANT", grantId).isPresent();
        GrantState state = revoked
                ? GrantState.REVOKED
                : GrantState.valueOf(entity.getState());
        return Optional.of(GrantAggregate.load(entity.getGrantId(), entity.getCustomerId(),
                entity.getProductId(), SubjectKind.valueOf(entity.getSubjectKind()),
                eligibilityOf(entity.getEligibilityKind(), entity.getOfflineProofProfile()),
                GrantValidityHelper.fromGrant(entity),
                EligibilityPolicy.Mode.valueOf(entity.getMode()), state,
                entity.getGrantRevision(), entity.getGrantStateVersion()));
    }

    @Override
    public boolean isCurrent(GrantAggregate snapshot) {
        Optional<GrantAggregate> current = findById(snapshot.getGrantId());
        if (!current.isPresent()) {
            return false;
        }
        GrantAggregate value = current.get();
        // 版本字段是授权状态/商业修订的乐观围栏；基础身份字段同时核对，避免错误复用同名测试快照。
        return snapshot.getGrantRevision() == value.getGrantRevision()
                && snapshot.getGrantStateVersion() == value.getGrantStateVersion()
                && snapshot.getState() == value.getState()
                && snapshot.getProductId().equals(value.getProductId())
                && snapshot.getSubjectKind() == value.getSubjectKind()
                && snapshot.getMode() == value.getMode();
    }

    /** 按授权行的资格种类装载（非法种类按空装载拒绝由 validateCombination 拦截）。 */
    private static EligibilityPolicy eligibilityOf(String eligibilityKind, String offlineProofProfile) {
        if ("CUSTOMER_MEMBERS".equals(eligibilityKind)) {
            return EligibilityPolicy.customerMembers();
        }
        if ("ASSIGNED_SUBJECTS".equals(eligibilityKind)) {
            return EligibilityPolicy.assignedSubjects(offlineProofProfile);
        }
        return EligibilityPolicy.allocatedDeployments();
    }
}
