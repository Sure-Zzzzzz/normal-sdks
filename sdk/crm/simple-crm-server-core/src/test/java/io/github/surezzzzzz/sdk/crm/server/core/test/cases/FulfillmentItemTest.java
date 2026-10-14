package io.github.surezzzzzz.sdk.crm.server.core.test.cases;

import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.FulfillmentObligationTemplate;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentItem;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentResult;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentState;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 履约项回写状态机测试：认领（首次 in_progress）、序号水位、终态守卫与人工重置。
 *
 * @author surezzzzzz
 */
@Slf4j
class FulfillmentItemTest {

    private final Instant now = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void firstInProgressReportClaimsPendingItem() {
        FulfillmentItem claimed = item().report(1, FulfillmentState.IN_PROGRESS,
                new FulfillmentResult(1, "aksk:consumer-1", now, null, "已认领"));

        log.info("认领结果：状态={}，水位={}，版本={}", claimed.getState(), claimed.getReportSequence(),
                claimed.getVersion());
        assertEquals(FulfillmentState.IN_PROGRESS, claimed.getState(), "首次回写 in_progress 即认领");
        assertEquals(1, claimed.getReportSequence());
        assertEquals(2, claimed.getVersion(), "每次回写递增聚合版本");
    }

    @Test
    void rejectsNonIncreasingSequence() {
        FulfillmentItem claimed = item().report(2, FulfillmentState.IN_PROGRESS,
                new FulfillmentResult(2, "aksk:consumer-1", now, null, null));

        CrmException exception = assertThrows(CrmException.class, () -> claimed.report(2,
                FulfillmentState.COMPLETED,
                new FulfillmentResult(2, "aksk:consumer-1", now, "done", null)));
        log.info("重复序号拒绝结果：错误码={}", exception.getErrorCode());
        assertEquals(CrmErrorCode.FULFILLMENT_RESULT_CONSISTENCY_FAILURE, exception.getErrorCode(),
                "回写序号必须严格递增（防乱序与重复）");
    }

    @Test
    void completesWithResultDataAndGuardsTerminalState() {
        FulfillmentItem claimed = item().report(1, FulfillmentState.IN_PROGRESS,
                new FulfillmentResult(1, "aksk:consumer-1", now, null, null));
        FulfillmentItem completed = claimed.report(2, FulfillmentState.COMPLETED,
                new FulfillmentResult(2, "aksk:consumer-1", now, "license-issued", null));

        log.info("完成结果：状态={}，结果数据={}", completed.getState(), completed.getResult().getResultData());
        assertEquals(FulfillmentState.COMPLETED, completed.getState());

        CrmException terminalException = assertThrows(CrmException.class, () -> completed.report(3,
                FulfillmentState.FAILED, new FulfillmentResult(3, "aksk:consumer-1", now, "late", null)));
        CrmException resetException = assertThrows(CrmException.class, completed::reset);
        log.info("终态守卫结果：再报错误码={}，重置错误码={}", terminalException.getErrorCode(),
                resetException.getErrorCode());
        assertEquals(CrmErrorCode.INVALID_STATE_TRANSITION, terminalException.getErrorCode(),
                "COMPLETED 终态不可再报（防重复履约）");
        assertEquals(CrmErrorCode.INVALID_STATE_TRANSITION, resetException.getErrorCode(),
                "COMPLETED 不可人工重置（错报走线下修正）");
    }

    @Test
    void rejectsTerminalReportWithoutResultData() {
        CrmException exception = assertThrows(CrmException.class, () -> item().report(1,
                FulfillmentState.COMPLETED, new FulfillmentResult(1, "aksk:consumer-1", now, null, null)));
        log.info("终态缺结果数据拒绝结果：错误码={}", exception.getErrorCode());
        assertEquals(CrmErrorCode.VALIDATION_FAILED, exception.getErrorCode(), "终态回写必带结果数据");
    }

    @Test
    void resetsFailedItemKeepingWatermark() {
        FulfillmentItem failed = item().report(1, FulfillmentState.IN_PROGRESS,
                        new FulfillmentResult(1, "aksk:consumer-1", now, null, null))
                .report(2, FulfillmentState.FAILED,
                        new FulfillmentResult(2, "aksk:consumer-1", now, "consumer-down", null));
        FulfillmentItem reset = failed.reset();

        log.info("重置结果：状态={}，水位保留={}，结果清空={}", reset.getState(), reset.getReportSequence(),
                reset.getResult() == null);
        assertEquals(FulfillmentState.PENDING_DISPATCH, reset.getState(), "FAILED 可重置回待执行");
        assertEquals(2, reset.getReportSequence(), "水位保留（拒绝旧序号回写）");
        assertNull(reset.getResult(), "重置清空旧结果");

        CrmException staleException = assertThrows(CrmException.class, () -> reset.report(2,
                FulfillmentState.IN_PROGRESS,
                new FulfillmentResult(2, "aksk:consumer-1", now, null, null)));
        log.info("重置后旧序号拒绝结果：错误码={}", staleException.getErrorCode());
        assertEquals(CrmErrorCode.FULFILLMENT_RESULT_CONSISTENCY_FAILURE, staleException.getErrorCode(),
                "重置后必须使用更高序号重新回写");
    }

    private FulfillmentItem item() {
        return new FulfillmentItem("fulfillment-1", "order-1", "order-line-1", 1,
                FulfillmentState.PENDING_DISPATCH,
                new FulfillmentObligationTemplate("subject-1", "scope-1", "capability-1"),
                "consumer-1", 2, "iam:actor-1", 0, null);
    }
}
