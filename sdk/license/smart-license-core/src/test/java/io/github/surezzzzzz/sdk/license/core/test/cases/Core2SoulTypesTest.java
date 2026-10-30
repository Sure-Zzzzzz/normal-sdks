package io.github.surezzzzzz.sdk.license.core.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.core.model.SubjectBinding;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.core.model.SubjectRef;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * Core2 灵魂类型联合结构断言：非法组合在构造期确定拒绝。
 *
 * @author surezzzzzz
 */
class Core2SoulTypesTest {

    @Test
    void subjectKindParsesAndRejectsUnknown() {
        assertThat(SubjectKind.fromCode("DEPLOYMENT")).isEqualTo(SubjectKind.DEPLOYMENT);
        assertThat(SubjectKind.isValid("PERSON")).isTrue();
        assertThat(SubjectKind.isValid("TENANT")).isFalse();
        assertThatThrownBy(() -> SubjectKind.fromCode("TENANT")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void subjectRefRejectsDeploymentKindAndBlankIdentifiers() {
        assertThatThrownBy(() -> SubjectRef.of(SubjectKind.DEPLOYMENT, "s", "p"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SubjectRef.of(SubjectKind.PERSON, "", "p"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SubjectRef.of(SubjectKind.SERVICE, "s", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deploymentBindingCarriesActivationOnly() {
        SubjectBinding.Deployment binding = SubjectBinding.deployment("activation-1", 2L);
        assertThat(binding.getKind()).isEqualTo(SubjectKind.DEPLOYMENT);
        assertThat(binding.activationId()).isEqualTo("activation-1");
        assertThat(binding.activationGeneration()).isEqualTo(2L);
        assertThat(binding.assignmentId()).isEmpty();
        assertThatThrownBy(binding::subjectRef).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void personBindingRejectsIllegalAssignmentPairs() {
        SubjectRef ref = SubjectRef.of(SubjectKind.PERSON, "identity-source", "person-1");
        assertThat(SubjectBinding.personService(ref).assignmentId()).isEmpty();
        assertThat(SubjectBinding.personService(ref, "assignment-1", 3L).assignmentGeneration()).hasValue(3L);
        assertThatThrownBy(() -> SubjectBinding.deployment("a", 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SubjectBinding.personService(ref, "", 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stateErrorCodesExposeStableDescriptions() {
        assertThat(ErrorCode.CREDENTIAL_REVOKED.getDescription()).isEqualTo("凭据或授权已吊销");
        assertThat(ErrorCode.REVOCATION_STALE.getDescription()).isEqualTo("吊销资料过旧或缺失");
    }
}
