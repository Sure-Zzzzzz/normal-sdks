package io.github.surezzzzzz.sdk.crm.server.core.test.cases;

import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.*;
import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
class FixedPriceFulfillmentCapabilityTest {

    private final FixedPriceFulfillmentCapability capability = new FixedPriceFulfillmentCapability();

    @Test
    void evaluatesDeterministicFrozenCommercialAndFulfillmentFacts() {
        CommercialCapabilityRequest request = new CommercialCapabilityRequest(
                new BigDecimal("3"), "SET", new Money(new BigDecimal("19.90"), "CNY"),
                "subject-1", "scope-1", "capability-1");

        CommercialCapabilityResult first = capability.evaluate(request);
        CommercialCapabilityResult second = capability.evaluate(request);
        FixedPriceCommercialTermsSnapshot firstTerms =
                (FixedPriceCommercialTermsSnapshot) first.getCommercialTermsSnapshot();

        log.info("固定价格能力计算结果：金额={}，币种={}，数量={}，单位={}",
                first.getLineTotal().getAmount(), first.getLineTotal().getCurrency(), firstTerms.getQuantity(),
                firstTerms.getUnit());
        assertEquals(CommercialCapabilityType.FIXED_PRICE_FULFILLMENT_V1, capability.getType());
        assertEquals(new BigDecimal("59.70"), first.getLineTotal().getAmount());
        assertEquals(first.getLineTotal().getAmount(), second.getLineTotal().getAmount());
        assertEquals(CommercialCapabilityType.FIXED_PRICE_FULFILLMENT_V1, firstTerms.getCapabilityType());
        assertEquals(new BigDecimal("3"), firstTerms.getQuantity());
        assertEquals("SET", firstTerms.getUnit());
        assertEquals(new BigDecimal("19.90"), firstTerms.getUnitPrice().getAmount());
        assertEquals("CNY", firstTerms.getUnitPrice().getCurrency());
        assertEquals("subject-1", first.getFulfillmentObligationTemplate().getSubjectReference());
        assertEquals("scope-1", first.getFulfillmentObligationTemplate().getFulfillmentScope());
        assertEquals("capability-1", first.getFulfillmentObligationTemplate().getRequiredConsumerCapability());
    }

    @Test
    void rejectsInvalidMoneyPrecisionAndCurrencyAddition() {
        assertValidation(() -> new Money(new BigDecimal("19.901"), "CNY"));
        assertValidation(() -> new Money(new BigDecimal("100.1"), "JPY"));
        assertValidation(() -> new Money(new BigDecimal("1.00"), "CNY")
                .add(new Money(new BigDecimal("1.00"), "USD")));
    }

    @Test
    void rejectsCalculationThatCannotBeRepresentedInCurrencyPrecision() {
        assertValidation(() -> new Money(new BigDecimal("19.90"), "CNY")
                .multiply(new BigDecimal("0.333")));
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertValidation(() -> new CommercialCapabilityRequest(
                BigDecimal.ZERO, "SET", new Money(new BigDecimal("19.90"), "CNY"),
                "subject-1", "scope-1", "capability-1"));
    }

    private void assertValidation(ThrowingRunnable runnable) {
        CrmException exception = assertThrows(CrmException.class, runnable::run);
        log.info("校验拒绝结果：错误码={}", exception.getErrorCode());
        assertEquals(CrmErrorCode.VALIDATION_FAILED, exception.getErrorCode());
    }

    private interface ThrowingRunnable {
        void run();
    }
}
