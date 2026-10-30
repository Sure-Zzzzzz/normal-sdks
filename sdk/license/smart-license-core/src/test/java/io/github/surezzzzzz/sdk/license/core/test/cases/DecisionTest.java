package io.github.surezzzzzz.sdk.license.core.test.cases;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.surezzzzzz.sdk.license.core.model.Decision;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Decision 值对象断言：ALLOW/DENY 构造、围栏引用不可变。
 *
 * @author surezzzzzz
 */
class DecisionTest {

    @Test
    void allowCarriesFenceReferencesImmutably() {
        Decision allow = Decision.allow("resource.read", 1L,
                Decision.CommercialPhase.ACTIVE, Decision.ConnectivityPhase.NORMAL,
                1780000000L, 1780000300L, Arrays.asList("fence-1", "fence-2"));
        assertThat(allow.isAllowed()).isTrue();
        assertThat(allow.getReasonCategory()).isEqualTo(Decision.ReasonCategory.ENTITLED);
        assertThat(allow.getReservationFenceReferences()).hasSize(2);
        assertThat(allow.getRecheckAt()).isEqualTo(1780000300L);
    }

    @Test
    void denyHasNoFenceAndReasonCategory() {
        Decision deny = Decision.deny("resource.write", 1L,
                Decision.CommercialPhase.COMMERCIAL_GRACE, Decision.ConnectivityPhase.NOT_APPLICABLE,
                1780000000L, Decision.ReasonCategory.COMMERCIAL_PHASE);
        assertThat(deny.isAllowed()).isFalse();
        assertThat(deny.getRecheckAt()).isNull();
        assertThat(deny.getReservationFenceReferences()).isEmpty();
    }

    @Test
    void fenceReferencesCannotBeMutatedAfterConstruction() {
        java.util.List<String> mutable = new java.util.ArrayList<>(Arrays.asList("a"));
        Decision allow = Decision.allow("act", 1L,
                Decision.CommercialPhase.ACTIVE, Decision.ConnectivityPhase.NOT_APPLICABLE,
                0L, null, mutable);
        mutable.add("b");
        assertThat(allow.getReservationFenceReferences()).hasSize(1);
    }
}
