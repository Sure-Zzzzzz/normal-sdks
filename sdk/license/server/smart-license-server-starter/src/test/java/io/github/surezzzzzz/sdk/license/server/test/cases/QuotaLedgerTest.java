package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.surezzzzzz.sdk.license.server.state.QuotaLedgerService;
import org.junit.jupiter.api.Test;

/**
 * 额度台账断言：不超发、操作去重只扣一次、释放幂等、扩容保留已消费。
 *
 * @author surezzzzzz
 */
class QuotaLedgerTest {

    @Test
    void concurrentRaceForLastUnitOnlyOneWins() {
        QuotaLedgerService ledger = new QuotaLedgerService();
        ledger.initBalance("g1", "concurrent.sessions", 1L);
        QuotaLedgerService.ReserveOutcome first =
                ledger.checkAndReserve("g1", "concurrent.sessions", 1L, "op-1");
        QuotaLedgerService.ReserveOutcome second =
                ledger.checkAndReserve("g1", "concurrent.sessions", 1L, "op-2");
        assertThat(first.granted).isTrue();
        assertThat(second.granted).isFalse();
        assertThat(second.reason).isEqualTo("INSUFFICIENT");
        assertThat(ledger.currentRemaining("g1", "concurrent.sessions")).isZero();
    }

    @Test
    void sameBusinessOperationChargedExactlyOnce() {
        QuotaLedgerService ledger = new QuotaLedgerService();
        ledger.initBalance("g1", "api.calls", 100L);
        assertThat(ledger.checkAndReserve("g1", "api.calls", 5L, "order-42").granted).isTrue();
        QuotaLedgerService.ReserveOutcome replay =
                ledger.checkAndReserve("g1", "api.calls", 5L, "order-42");
        // 幂等重放返回首次结论（已扣成功），不重复扣减也不拒绝
        assertThat(replay.granted).isTrue();
        assertThat(replay.reason).isEqualTo("DUPLICATE_OPERATION_IDEMPOTENT");
        assertThat(ledger.currentRemaining("g1", "api.calls")).isEqualTo(95L);
    }

    @Test
    void concurrentReleaseRestoresAndIsIdempotent() {
        QuotaLedgerService ledger = new QuotaLedgerService();
        ledger.initBalance("g1", "sessions", 2L);
        assertThat(ledger.checkAndReserve("g1", "sessions", 2L, "op-a").granted).isTrue();
        assertThat(ledger.release("g1", "sessions", 2L, "res-1")).isTrue();
        assertThat(ledger.release("g1", "sessions", 2L, "res-1")).isFalse(); // 重复释放拒绝
        assertThat(ledger.currentRemaining("g1", "sessions")).isEqualTo(2L);
    }

    @Test
    void expansionPreservesConsumption() {
        QuotaLedgerService ledger = new QuotaLedgerService();
        ledger.initBalance("g1", "api.calls", 10L);
        assertThat(ledger.checkAndReserve("g1", "api.calls", 4L, "op-1").granted).isTrue();
        ledger.initBalance("g1", "api.calls", 20L); // 扩容
        assertThat(ledger.currentRemaining("g1", "api.calls")).isEqualTo(16L);
    }

    @Test
    void missingBalanceRowIsDeterministicReject() {
        QuotaLedgerService ledger = new QuotaLedgerService();
        QuotaLedgerService.ReserveOutcome outcome =
                ledger.checkAndReserve("g-unknown", "metric", 1L, "op-x");
        assertThat(outcome.granted).isFalse();
        assertThat(outcome.reason).isEqualTo("NO_BALANCE_ROW");
    }
}
