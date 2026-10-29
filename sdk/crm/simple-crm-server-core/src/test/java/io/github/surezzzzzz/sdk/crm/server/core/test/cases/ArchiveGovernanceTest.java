package io.github.surezzzzzz.sdk.crm.server.core.test.cases;

import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapabilityType;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.CustomerState;
import io.github.surezzzzzz.sdk.crm.server.core.domain.offering.Offering;
import io.github.surezzzzzz.sdk.crm.server.core.domain.offering.OfferingState;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 档案治理域测试：Offering 三态、客户停用/复启/合并/转移、报价归属转移与惰性过期。
 *
 * @author surezzzzzz
 */
@Slf4j
class ArchiveGovernanceTest {

    private final Instant now = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void offeringWalksThroughThreeStates() {
        Offering draft = offering(OfferingState.DRAFT);
        assertFalse(draft.isPublished(), "录入态不可进新报价");

        Offering published = draft.published(now);
        assertTrue(published.isPublished(), "上架后可进新报价");

        Offering disabled = published.disabled(now);
        assertFalse(disabled.isPublished(), "下架后不进新报价");

        Offering republished = disabled.published(now);
        assertTrue(republished.isPublished(), "下架可重新上架");

        CrmException duplicateDisable = assertThrows(CrmException.class, () -> disabled.disabled(now));
        CrmException duplicatePublish = assertThrows(CrmException.class, () -> published.published(now));
        log.info("目录迁移守卫结果：重复下架错误码={}，重复上架错误码={}", duplicateDisable.getErrorCode(),
                duplicatePublish.getErrorCode());
        assertEquals(CrmErrorCode.INVALID_STATE_TRANSITION, duplicateDisable.getErrorCode());
        assertEquals(CrmErrorCode.INVALID_STATE_TRANSITION, duplicatePublish.getErrorCode());
    }

    @Test
    void customerDeactivatesAndActivates() {
        Customer active = customer(CustomerState.ACTIVE);
        Customer inactive = active.deactivated(now);
        Customer reactivated = inactive.activated(now);

        log.info("客户停用复启结果：态={}→{}→{}", active.getState(), inactive.getState(), reactivated.getState());
        assertEquals(CustomerState.INACTIVE, inactive.getState());
        assertEquals(CustomerState.ACTIVE, reactivated.getState());

        CrmException duplicate = assertThrows(CrmException.class, () -> inactive.deactivated(now));
        log.info("重复停用拒绝结果：错误码={}", duplicate.getErrorCode());
        assertEquals(CrmErrorCode.INVALID_STATE_TRANSITION, duplicate.getErrorCode());
    }

    @Test
    void customerMergesIntoTargetWithTerminalState() {
        Customer source = customer(CustomerState.ACTIVE);
        Customer merged = source.mergedInto("customer-target", now);

        log.info("合并结果：态={}，指向={}", merged.getState(), merged.getMergedIntoCustomerId());
        assertEquals(CustomerState.MERGED, merged.getState());
        assertEquals("customer-target", merged.getMergedIntoCustomerId());

        CrmException selfMerge = assertThrows(CrmException.class, () -> source.mergedInto(
                source.getCustomerId(), now));
        CrmException transferMerged = assertThrows(CrmException.class, () -> merged.transferredTo(
                "iam:actor-2", now));
        CrmException deactivateMerged = assertThrows(CrmException.class, () -> merged.deactivated(now));
        CrmException activateMerged = assertThrows(CrmException.class, () -> merged.activated(now));
        log.info("合并守卫结果：自并错误码={}，转移已并入错误码={}", selfMerge.getErrorCode(),
                transferMerged.getErrorCode());
        assertEquals(CrmErrorCode.INVALID_STATE_TRANSITION, selfMerge.getErrorCode());
        assertEquals(CrmErrorCode.INVALID_STATE_TRANSITION, transferMerged.getErrorCode());
        assertEquals(CrmErrorCode.INVALID_STATE_TRANSITION, deactivateMerged.getErrorCode());
        assertEquals(CrmErrorCode.INVALID_STATE_TRANSITION, activateMerged.getErrorCode());
    }

    @Test
    void customerTransfersOwnership() {
        Customer transferred = customer(CustomerState.ACTIVE).transferredTo("aksk:client-2", now);

        log.info("客户转移结果：owner={}", transferred.getOwner());
        assertEquals("aksk:client-2", transferred.getOwner());
    }

    @Test
    void quotationTransferRejectsConfirmedAndExpiryIsLazy() {
        Quotation draft = new Quotation("quotation-1", "customer-1", "iam:actor-1", 1L, 1,
                null, null, null, now, now);
        Quotation transferred = draft.transferredTo("iam:actor-2", now);
        assertEquals("iam:actor-2", transferred.getOwner(), "未确认报价可转移归属");
        assertEquals(2L, transferred.getAggregateVersion(), "归属转移必须递增报价聚合版本");
        assertThrows(CrmException.class, () -> draft.transferredTo("invalid-owner", now));

        Quotation confirmed = new Quotation("quotation-1", "customer-1", "iam:actor-1", 3L, 1,
                null, "order-1", null, now, now);
        CrmException confirmedTransfer = assertThrows(CrmException.class,
                () -> confirmed.transferredTo("iam:actor-2", now));
        log.info("已确认报价转移拒绝结果：错误码={}", confirmedTransfer.getErrorCode());
        assertEquals(CrmErrorCode.INVALID_STATE_TRANSITION, confirmedTransfer.getErrorCode(),
                "确认后订单已生成，历史归属不追改");

        Quotation issued = new Quotation("quotation-1", "customer-1", "iam:actor-1", 2L, 1,
                1, null, null, now, now);
        assertTrue(issued.isIssued(), "已签发未确认为 issued 形态");
        assertFalse(issued.isConfirmed());
    }

    private Offering offering(OfferingState state) {
        return new Offering("offering-1", "offering-reference-1", "Offering", state,
                CommercialCapabilityType.FIXED_PRICE_FULFILLMENT_V1, "capability-1", "iam:actor-1", 1L, now, now);
    }

    private Customer customer(CustomerState state) {
        return new Customer("customer-1", "Customer", state, "iam:actor-1", null, 1L, now, now);
    }
}
