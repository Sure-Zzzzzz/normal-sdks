package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import io.github.surezzzzzz.sdk.license.core.model.EligibilityPolicy;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.core.model.ValidityPolicy;
import io.github.surezzzzzz.sdk.license.server.constant.GrantState;
import io.github.surezzzzzz.sdk.license.server.entity.GrantAggregate;
import org.junit.jupiter.api.Test;

/**
 * 授权聚合断言：组合校验委托、状态版本围栏、修订不可变前提。
 *
 * @author surezzzzzz
 */
class GrantAggregateTest {

    private GrantAggregate personOnlineGrant() {
        return GrantAggregate.draft("g1", "cu1", "p1", SubjectKind.PERSON,
                EligibilityPolicy.customerMembers(),
                ValidityPolicy.fixed(1780000000L, 1781000000L, 1782000000L, null),
                EligibilityPolicy.Mode.ONLINE);
    }

    @Test
    void draftStartsAtVersionOneWithStateGuards() {
        GrantAggregate grant = personOnlineGrant();
        assertThat(grant.getState()).isEqualTo(GrantState.DRAFT);
        assertThat(grant.getGrantRevision()).isEqualTo(1L);
        assertThat(grant.getGrantStateVersion()).isEqualTo(1L);
        assertThat(grant.isUsable()).isFalse();
    }

    @Test
    void suspendResumeBumpsStateVersionNotRevision() {
        GrantAggregate grant = personOnlineGrant();
        assertThatThrownBy(() -> grant.suspendOrResume(false))
                .isInstanceOf(LicenseCoreException.class); // DRAFT 不能直接切，必须走发布启用
        grant.enable();
        assertThat(grant.getState()).isEqualTo(GrantState.ENABLED);
        assertThat(grant.isUsable()).isTrue();
        grant.suspendOrResume(true);
        assertThat(grant.getState()).isEqualTo(GrantState.SUSPENDED);
        assertThat(grant.getGrantStateVersion()).isEqualTo(3L);
        assertThat(grant.getGrantRevision()).isEqualTo(1L); // 管理变化不偷增商业修订
        grant.suspendOrResume(false);
        assertThat(grant.isUsable()).isTrue();
    }

    @Test
    void revisionAppliesNewValidityAndRejectsNull() {
        GrantAggregate grant = personOnlineGrant();
        // 修订必须携带新期限（null 拒绝：API 不接受无意义修订）
        assertThatThrownBy(() -> grant.revise(null)).isInstanceOf(LicenseCoreException.class);
        assertThat(grant.revise(ValidityPolicy.fixed(1780000100L, 1780000200L, 1780000300L, null)))
                .isEqualTo(2L);
        assertThat(grant.getGrantRevision()).isEqualTo(2L);
    }

    @Test
    void illegalCombinationRejectedAtConstruction() {
        assertThatThrownBy(() -> GrantAggregate.draft("g", "cu", "p", SubjectKind.DEPLOYMENT,
                EligibilityPolicy.customerMembers(),
                ValidityPolicy.fixed(1L, 2L, 3L, null), EligibilityPolicy.Mode.ONLINE))
                .isInstanceOf(LicenseCoreException.class);
        assertThatCode(() -> GrantAggregate.draft("g", "cu", "p", SubjectKind.SERVICE,
                EligibilityPolicy.assignedSubjects(null),
                ValidityPolicy.fixed(1L, 2L, 3L, null), EligibilityPolicy.Mode.ONLINE))
                .doesNotThrowAnyException();
    }
}
