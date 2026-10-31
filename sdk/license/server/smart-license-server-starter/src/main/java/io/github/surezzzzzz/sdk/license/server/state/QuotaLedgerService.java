package io.github.surezzzzzz.sdk.license.server.state;

import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务端额度台账核心：grant/metric/window 余额行的原子预留与释放。
 *
 * <p>多指标按固定指标顺序加锁避免死锁由调用方（事务切面）保证；本类实现单行余额的
 * 检查-扣减原子语义与业务操作编号去重。同名同操作重复提交只扣一次；
 * 并发不同操作不能分别读同一余额后双写成功（行版本围栏）。</p>
 *
 * @author surezzzzzz
 */
@SimpleLicenseServerComponent
public final class QuotaLedgerService {

    /** 余额行。 */
    public static final class BalanceRow {

        private final String key;
        private long limit;
        private long remaining;
        private long rowVersion;

        BalanceRow(String key, long limit) {
            this.key = key;
            this.limit = limit;
            this.remaining = limit;
            this.rowVersion = 1L;
        }

        public long getRemaining() {
            return remaining;
        }

        public long getRowVersion() {
            return rowVersion;
        }
    }

    /** 预留结果。 */
    public static final class ReserveOutcome {

        public final boolean granted;
        public final long remainingAfter;
        public final String reason;

        ReserveOutcome(boolean granted, long remainingAfter, String reason) {
            this.granted = granted;
            this.remainingAfter = remainingAfter;
            this.reason = reason;
        }
    }

    /** 幂等记录上限：超出淘汰最旧（设计 §11 幂等缓存按分类保留；防无界增长）。 */
    private static final int OPERATIONS_MAX = 100_000;

    private final Map<String, BalanceRow> balances = new HashMap<String, BalanceRow>();
    private final Map<String, String> consumedOperations =
            new LinkedHashMap<String, String>(128, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > OPERATIONS_MAX;
                }
            };

    /** 初始化（或扩容）某授权×指标余额；扩容按差额追加保留已消费，减配走治理路径。 */
    public synchronized void initBalance(String grantId, String metric, long limit) {
        String key = rowKey(grantId, metric);
        BalanceRow row = balances.get(key);
        if (row == null) {
            balances.put(key, new BalanceRow(key, limit));
            return;
        }
        // 扩容按差额追加剩余，保留已消费量；减配走治理路径不在此处理
        if (limit > row.limit) {
            row.remaining += limit - row.limit;
            row.limit = limit;
            row.rowVersion++;
        }
    }

    /**
     * 原子检查并扣减；业务操作编号去重（同 grant/metric/op 只扣一次）。
     *
     * @return granted=false 时 reason 说明余额不足或重复操作已扣
     */
    public synchronized ReserveOutcome checkAndReserve(String grantId, String metric, long quantity,
                                                       String businessOperationId) {
        String dedupKey = grantId + ":" + metric + ":" + businessOperationId;
        if (consumedOperations.containsKey(dedupKey)) {
            // 幂等重放：同业务操作编号重复提交返回首次结论（已扣成功），不重复扣减也不拒绝
            return new ReserveOutcome(true, currentRemaining(grantId, metric), "DUPLICATE_OPERATION_IDEMPOTENT");
        }
        BalanceRow row = balances.get(rowKey(grantId, metric));
        if (row == null) {
            return new ReserveOutcome(false, 0L, "NO_BALANCE_ROW");
        }
        if (row.remaining < quantity) {
            return new ReserveOutcome(false, row.remaining, "INSUFFICIENT");
        }
        row.remaining -= quantity;
        row.rowVersion++;
        consumedOperations.put(dedupKey, businessOperationId);
        return new ReserveOutcome(true, row.remaining, "OK");
    }

    /**
     * 并发占用释放（仅 CONCURRENT；CONSUMPTION 不可释放）。
     * 释放量不得超过当前已占量（limit-remaining），防止把 remaining 抬超过 limit。
     */
    public synchronized boolean release(String grantId, String metric, long quantity,
                                        String reservationId) {
        String dedupKey = grantId + ":" + metric + ":release:" + reservationId;
        if (consumedOperations.containsKey(dedupKey)) {
            return false;
        }
        BalanceRow row = balances.get(rowKey(grantId, metric));
        if (row == null || quantity <= 0 || quantity > row.limit - row.remaining) {
            return false;
        }
        row.remaining += quantity;
        row.rowVersion++;
        consumedOperations.put(dedupKey, reservationId);
        return true;
    }

    public synchronized long currentRemaining(String grantId, String metric) {
        BalanceRow row = balances.get(rowKey(grantId, metric));
        return row == null ? 0L : row.remaining;
    }

    private static String rowKey(String grantId, String metric) {
        return grantId + ":" + metric;
    }
}
