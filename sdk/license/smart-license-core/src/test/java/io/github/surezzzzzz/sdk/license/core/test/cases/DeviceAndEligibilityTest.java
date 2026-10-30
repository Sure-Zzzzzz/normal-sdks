package io.github.surezzzzzz.sdk.license.core.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import io.github.surezzzzzz.sdk.license.core.model.DeviceBinding;
import io.github.surezzzzzz.sdk.license.core.model.EligibilityPolicy;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 设备绑定与资格政策联合校验断言：非法组合构造/发布期确定拒绝。
 *
 * @author surezzzzzz
 */
class DeviceAndEligibilityTest {

    @Test
    void weightedBindingRejectsBadWeights() {
        Map<String, String> digests = new HashMap<String, String>();
        digests.put("systemUuid", "abc");
        Map<String, Integer> weights = new HashMap<String, Integer>();
        weights.put("systemUuid", 50);
        DeviceBinding.Weighted valid = (DeviceBinding.Weighted) DeviceBinding.weighted(
                "hardware/1", digests, weights, 30, 10);
        assertThat(valid.getMinimumAvailableWeight()).isEqualTo(30);
        assertThatThrownBy(() -> DeviceBinding.weighted("hardware/1", digests, weights, 0, 10))
                .isInstanceOf(LicenseCoreException.class);
        assertThatThrownBy(() -> DeviceBinding.weighted("hardware/1", digests, weights, 50, 50))
                .isInstanceOf(LicenseCoreException.class);
        weights.put("systemUuid", 0);
        assertThatThrownBy(() -> DeviceBinding.weighted("hardware/1", digests, weights, 10, 5))
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void deviceKeyRequires32ByteFingerprint() {
        byte[] ok = new byte[32];
        DeviceBinding.DeviceKey key = (DeviceBinding.DeviceKey) DeviceBinding.deviceKey(ok);
        assertThat(key.getSpkiFingerprint()).hasSize(32);
        assertThatThrownBy(() -> DeviceBinding.deviceKey(new byte[31]))
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void deploymentMustUseAllocatedAndNoOfflineProfile() {
        assertThatCode(() -> EligibilityPolicy.validateCombination(
                SubjectKind.DEPLOYMENT, EligibilityPolicy.allocatedDeployments(),
                EligibilityPolicy.Mode.OFFLINE)).doesNotThrowAnyException();
        assertThatThrownBy(() -> EligibilityPolicy.validateCombination(
                SubjectKind.DEPLOYMENT, EligibilityPolicy.assignedSubjects(null),
                EligibilityPolicy.Mode.ONLINE)).isInstanceOf(LicenseCoreException.class);
        assertThatThrownBy(() -> EligibilityPolicy.validateCombination(
                SubjectKind.DEPLOYMENT,
                EligibilityPolicy.assignedSubjects("profile-v1"),
                EligibilityPolicy.Mode.OFFLINE)).isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void personMembersCannotGoOffline() {
        assertThatThrownBy(() -> EligibilityPolicy.validateCombination(
                SubjectKind.PERSON, EligibilityPolicy.customerMembers(),
                EligibilityPolicy.Mode.OFFLINE)).isInstanceOf(LicenseCoreException.class);
        assertThatCode(() -> EligibilityPolicy.validateCombination(
                SubjectKind.PERSON, EligibilityPolicy.customerMembers(),
                EligibilityPolicy.Mode.ONLINE)).doesNotThrowAnyException();
    }

    @Test
    void offlinePersonNeedsProfileOnlineForbidsIt() {
        assertThatCode(() -> EligibilityPolicy.validateCombination(
                SubjectKind.PERSON, EligibilityPolicy.assignedSubjects("local-v1"),
                EligibilityPolicy.Mode.OFFLINE)).doesNotThrowAnyException();
        assertThatThrownBy(() -> EligibilityPolicy.validateCombination(
                SubjectKind.PERSON, EligibilityPolicy.assignedSubjects(null),
                EligibilityPolicy.Mode.OFFLINE)).isInstanceOf(LicenseCoreException.class);
        assertThatThrownBy(() -> EligibilityPolicy.validateCombination(
                SubjectKind.PERSON, EligibilityPolicy.assignedSubjects("local-v1"),
                EligibilityPolicy.Mode.ONLINE)).isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void serviceCannotUseCustomerMembers() {
        assertThatThrownBy(() -> EligibilityPolicy.validateCombination(
                SubjectKind.SERVICE, EligibilityPolicy.customerMembers(),
                EligibilityPolicy.Mode.ONLINE)).isInstanceOf(LicenseCoreException.class);
    }
}
