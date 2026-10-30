package io.github.surezzzzzz.sdk.license.core.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.core.codec.Protocol2Payload;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import io.github.surezzzzzz.sdk.license.core.model.ClockPolicy;
import io.github.surezzzzzz.sdk.license.core.model.CredentialKind;
import io.github.surezzzzzz.sdk.license.core.model.DeviceBinding;
import io.github.surezzzzzz.sdk.license.core.model.EligibilityPolicy;
import io.github.surezzzzzz.sdk.license.core.model.Lease;
import io.github.surezzzzzz.sdk.license.core.model.OrganizationPolicy;
import io.github.surezzzzzz.sdk.license.core.model.RevocationPolicy;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.core.model.SubjectRef;
import io.github.surezzzzzz.sdk.license.core.model.SubjectBinding;
import io.github.surezzzzzz.sdk.license.core.model.ValidityPolicy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 协议 2 完整载荷联合约束断言：OFFLINE/HYBRID 的 exp/lease、部署设备绑定、人员禁止实名引用泄漏。
 *
 * @author surezzzzzz
 */
class Protocol2PayloadTest {

    private static Protocol2Payload.Builder basePersonHybrid() {
        SubjectRef ref = SubjectRef.of(SubjectKind.PERSON, "identity-source", "person-1");
        SubjectRef requester = SubjectRef.of(SubjectKind.SERVICE, "service-source", "service-1");
        Lease lease = Lease.of("lease-1", 1780000300L, 1780000600L, 1780000900L,
                Arrays.asList("read"), requester, null);
        return Protocol2Payload.builder()
                .credentialKind(CredentialKind.HYBRID)
                .jti("credential-1")
                .issuerId("issuer-1")
                .productId("product-1")
                .customerId("customer-1")
                .grantId("grant-1")
                .grantRevision(1L)
                .grantStateVersion(1L)
                .eligibilityPolicy(EligibilityPolicy.customerMembers())
                .subjectBinding(SubjectBinding.personService(ref))
                .issuedAt(1780000000L)
                .notBefore(1780000000L)
                .expiresAt(1780000900L)
                .validity(ValidityPolicy.fixed(1780000000L, 1781000000L, 1782000000L, null))
                .clockPolicy(ClockPolicy.of(120, 120))
                .deviceBinding(DeviceBinding.none())
                .organizationPolicy(OrganizationPolicy.required(3600, 1800))
                .revocationPolicy(RevocationPolicy.optional())
                .lease(lease)
                .requiredCapabilities(Arrays.asList("license-condition/1", "subject-binding/1"));
    }

    @Test
    void hybridPayloadWithMatchingExpiryBuilds() {
        assertThatCode(() -> basePersonHybrid().build()).doesNotThrowAnyException();
        Protocol2Payload payload = basePersonHybrid().build();
        assertThat(payload.getExpiresAt()).isEqualTo(1780000900L);
        assertThat(payload.getLease().getOutageUntil()).isEqualTo(1780000900L);
    }

    @Test
    void hybridWithoutLeaseOrMismatchedExpiryRejected() {
        assertThatThrownBy(() -> basePersonHybrid().lease(null).build())
                .isInstanceOf(LicenseCoreException.class);
        assertThatThrownBy(() -> basePersonHybrid().expiresAt(1780000600L).build())
                .isInstanceOf(LicenseCoreException.class);
        // 断网截止不得晚于商业宽限截止
        SubjectRef ref = SubjectRef.of(SubjectKind.PERSON, "s", "p");
        SubjectRef requester = SubjectRef.of(SubjectKind.SERVICE, "s", "svc");
        assertThatThrownBy(() -> basePersonHybrid()
                .lease(Lease.of("l2", 1780000300L, 1780000600L, 1789000000L,
                        Arrays.asList("read"), requester, null))
                .expiresAt(1789000000L)
                .build())
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void offlineFixedMustExpireAtGraceUntil() {
        SubjectRef ref = SubjectRef.of(SubjectKind.PERSON, "s", "p");
        Protocol2Payload.Builder builder = Protocol2Payload.builder()
                .credentialKind(CredentialKind.OFFLINE)
                .jti("c2").issuerId("i").productId("p").customerId("cu").grantId("g")
                .grantRevision(1L).grantStateVersion(1L)
                .eligibilityPolicy(EligibilityPolicy.assignedSubjects("local-v1"))
                .subjectBinding(SubjectBinding.personService(ref,
                        "assignment-1", 1L))
                .issuedAt(1780000000L).notBefore(1780000000L)
                .expiresAt(1782000000L)
                .validity(ValidityPolicy.fixed(1780000000L, 1781000000L, 1782000000L, null))
                .clockPolicy(ClockPolicy.of(0, 0))
                .deviceBinding(DeviceBinding.none())
                .organizationPolicy(OrganizationPolicy.notRequired())
                .revocationPolicy(RevocationPolicy.optional())
                .requiredCapabilities(Collections.<String>emptyList());
        assertThatCode(builder::build).doesNotThrowAnyException();
        assertThatThrownBy(() -> builder.expiresAt(1781000000L).build())
                .isInstanceOf(LicenseCoreException.class);
        assertThatThrownBy(() -> builder.expiresAt(null).build())
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void offlinePerpetualOmitsExpiry() {
        SubjectRef ref = SubjectRef.of(SubjectKind.PERSON, "s", "p");
        Protocol2Payload payload = Protocol2Payload.builder()
                .credentialKind(CredentialKind.OFFLINE)
                .jti("c3").issuerId("i").productId("p").customerId("cu").grantId("g")
                .grantRevision(1L).grantStateVersion(1L)
                .eligibilityPolicy(EligibilityPolicy.assignedSubjects("local-v1"))
                .subjectBinding(SubjectBinding.personService(ref, "assignment-1", 1L))
                .issuedAt(1780000000L).notBefore(1780000000L)
                .validity(ValidityPolicy.perpetual(1780000000L, null, null))
                .clockPolicy(ClockPolicy.of(0, 0))
                .deviceBinding(DeviceBinding.none())
                .organizationPolicy(OrganizationPolicy.notRequired())
                .revocationPolicy(RevocationPolicy.optional())
                .requiredCapabilities(Collections.<String>emptyList())
                .build();
        assertThat(payload.getExpiresAt()).isNull();
    }

    @Test
    void deploymentAllowsNoneDeviceBindingAndForbidsNamedSeats() {
        Protocol2Payload.Builder builder = Protocol2Payload.builder()
                .credentialKind(CredentialKind.OFFLINE)
                .jti("c4").issuerId("i").productId("p").customerId("cu").grantId("g")
                .grantRevision(1L).grantStateVersion(1L)
                .eligibilityPolicy(EligibilityPolicy.allocatedDeployments())
                .subjectBinding(SubjectBinding.deployment("activation-1", 1L))
                .issuedAt(1780000000L).notBefore(1780000000L)
                .expiresAt(1782000000L)
                .validity(ValidityPolicy.fixed(1780000000L, 1781000000L, 1782000000L, null))
                .clockPolicy(ClockPolicy.of(0, 0))
                .deviceBinding(DeviceBinding.none())
                .organizationPolicy(OrganizationPolicy.notRequired())
                .revocationPolicy(RevocationPolicy.optional())
                .requiredCapabilities(Collections.<String>emptyList());
        // 部署 + NONE 是模型显式声明的无绑定产品形态（POLICIES §4），可构造
        assertThatCode(builder::build).doesNotThrowAnyException();
        // 换 WEIGHTED 后合法；再塞实名引用又拒绝
        java.util.Map<String, String> digests = new java.util.HashMap<String, String>();
        digests.put("systemUuid", "d");
        java.util.Map<String, Integer> weights = new java.util.HashMap<String, Integer>();
        weights.put("systemUuid", 50);
        DeviceBinding weighted = DeviceBinding.weighted("hardware/1", digests, weights, 30, 10);
        assertThatCode(() -> builder.deviceBinding(weighted).build()).doesNotThrowAnyException();
        List<io.github.surezzzzzz.sdk.license.core.model.NamedSeatReference> seats =
                Arrays.asList(io.github.surezzzzzz.sdk.license.core.model.NamedSeatReference
                        .perpetual("a1", 1L, "m"));
        assertThatThrownBy(() -> builder.namedSeatReferences(seats).build())
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void personWithNonNoneDeviceBindingRejected() {
        java.util.Map<String, String> digests = new java.util.HashMap<String, String>();
        digests.put("systemUuid", "d");
        java.util.Map<String, Integer> weights = new java.util.HashMap<String, Integer>();
        weights.put("systemUuid", 50);
        assertThatThrownBy(() -> basePersonHybrid()
                .deviceBinding(DeviceBinding.weighted("hardware/1", digests, weights, 30, 10))
                .build())
                .isInstanceOf(LicenseCoreException.class);
    }
}
