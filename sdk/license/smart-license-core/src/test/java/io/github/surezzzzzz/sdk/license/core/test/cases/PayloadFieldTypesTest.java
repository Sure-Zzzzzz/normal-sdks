package io.github.surezzzzzz.sdk.license.core.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import io.github.surezzzzzz.sdk.license.core.model.ClockPolicy;
import io.github.surezzzzzz.sdk.license.core.model.Decision;
import io.github.surezzzzzz.sdk.license.core.model.Lease;
import io.github.surezzzzzz.sdk.license.core.model.NamedSeatReference;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.core.model.SubjectRef;
import io.github.surezzzzzz.sdk.license.core.model.ValidityPolicy;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/**
 * 协议 2 载荷字段族断言：期限阶段、租约截止、时钟政策边界、实名引用保留。
 *
 * @author surezzzzzz
 */
class PayloadFieldTypesTest {

    @Test
    void validityFixedPhaseBoundaries() {
        ValidityPolicy fixed = ValidityPolicy.fixed(1000L, 2000L, 3000L, Arrays.asList("read"));
        assertThat(fixed.commercialPhaseAt(999L)).isEqualTo(Decision.CommercialPhase.NOT_STARTED);
        assertThat(fixed.commercialPhaseAt(1000L)).isEqualTo(Decision.CommercialPhase.ACTIVE);
        assertThat(fixed.commercialPhaseAt(1999L)).isEqualTo(Decision.CommercialPhase.ACTIVE);
        assertThat(fixed.commercialPhaseAt(2000L)).isEqualTo(Decision.CommercialPhase.COMMERCIAL_GRACE);
        assertThat(fixed.commercialPhaseAt(2999L)).isEqualTo(Decision.CommercialPhase.COMMERCIAL_GRACE);
        assertThat(fixed.commercialPhaseAt(3000L)).isEqualTo(Decision.CommercialPhase.ENDED);
        assertThatThrownBy(() -> ValidityPolicy.fixed(2000L, 1000L, 3000L, null))
                .isInstanceOf(LicenseCoreException.class);
        assertThatThrownBy(() -> ValidityPolicy.fixed(1000L, 3000L, 2000L, null))
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void validityPerpetualNeverEndsButStillRespectsSuspension() {
        ValidityPolicy perpetual = ValidityPolicy.perpetual(1000L, null, Collections.<String>emptyList());
        assertThat(perpetual.commercialPhaseAt(Long.MAX_VALUE / 2))
                .isEqualTo(Decision.CommercialPhase.ACTIVE);
        assertThat(perpetual.commercialPhaseAt(999L)).isEqualTo(Decision.CommercialPhase.NOT_STARTED);
        assertThat(perpetual.getServiceUntil()).isNull();
    }

    @Test
    void leaseConnectivityPhaseBoundaries() {
        SubjectRef requester = SubjectRef.of(SubjectKind.SERVICE, "src", "svc");
        Lease lease = Lease.of("lease-1", 1000, 2000, 3000,
                Arrays.asList("read"), requester, null);
        assertThat(lease.connectivityPhaseAt(1999L)).isEqualTo(Decision.ConnectivityPhase.NORMAL);
        assertThat(lease.connectivityPhaseAt(2000L)).isEqualTo(Decision.ConnectivityPhase.OUTAGE);
        assertThat(lease.connectivityPhaseAt(2999L)).isEqualTo(Decision.ConnectivityPhase.OUTAGE);
        assertThat(lease.connectivityPhaseAt(3000L)).isEqualTo(Decision.ConnectivityPhase.ENDED);
        assertThatThrownBy(() -> Lease.of("l", 3000, 2000, 1000, null, requester, null))
                .isInstanceOf(LicenseCoreException.class);
        assertThatThrownBy(() -> Lease.of("l", 1000, 2000, 3000, null, null, null))
                .isInstanceOf(LicenseCoreException.class);
        assertThatThrownBy(() -> Lease.of("l", 1000, 2000, 3000, null,
                SubjectRef.of(SubjectKind.PERSON, "src", "person"), null))
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void clockPolicyRejectsOver300Seconds() {
        ClockPolicy.of(0, 300);
        ClockPolicy.of(300, 0);
        assertThatThrownBy(() -> ClockPolicy.of(301, 0))
                .isInstanceOf(LicenseCoreException.class);
        assertThatThrownBy(() -> ClockPolicy.of(0, -1))
                .isInstanceOf(LicenseCoreException.class);
    }

    @Test
    void namedSeatReferenceRetentionConstraints() {
        NamedSeatReference.fixed("a1", 1L, "metric", 9999L);
        NamedSeatReference.perpetual("a1", 1L, "metric");
        assertThat(NamedSeatReference.fixed("a1", 1L, "metric", 9999L).getRetainUntil()).hasValue(9999L);
        assertThat(NamedSeatReference.perpetual("a1", 1L, "metric").getRetainUntil()).isEmpty();
        assertThatThrownBy(() -> NamedSeatReference.fixed("a1", 1L, "metric", -1))
                .isInstanceOf(LicenseCoreException.class);
        assertThatThrownBy(() -> NamedSeatReference.perpetual("a1", 0L, "metric"))
                .isInstanceOf(LicenseCoreException.class);
    }
}
