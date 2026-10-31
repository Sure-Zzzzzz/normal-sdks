package io.github.surezzzzzz.sdk.license.server.service;

import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.server.entity.QuotaBalanceEntity;
import io.github.surezzzzzz.sdk.license.server.repository.QuotaBalanceRepository;
import io.github.surezzzzzz.sdk.license.server.constant.QuotaConstant;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 额度台账的 JPA 生产实现：license_quota_balance 行事实（grant×metric 唯一）。
 *
 * <p>预留/释放在悲观行锁事务内检查-扣减（多实例原子语义由数据库围栏保证）；
 * 业务操作幂等去重由操作表承担（预留端点接线时叠加），本类只承载余额事实。</p>
 *
 * @author surezzzzzz
 */
@SimpleLicenseServerComponent
public class JpaQuotaLedgerService {

    private final QuotaBalanceRepository balances;

    public JpaQuotaLedgerService(QuotaBalanceRepository balances) {
        this.balances = balances;
    }

    /** 初始化（或扩容）余额：新行=限额全额；已有行按差额追加保留已消费。 */
    @Transactional(propagation = Propagation.REQUIRED)
    public void initBalance(String grantId, String metric, long limit) {
        initBalance(grantId, metric, limit, null);
    }

    /** 首次引入固定窗口指标时保存商业锚；已有锚保持不变。 */
    @Transactional(propagation = Propagation.REQUIRED)
    public void initBalance(String grantId, String metric, long limit, Long windowAnchorAt) {
        if (limit < 0L) {
            throw new IllegalArgumentException(QuotaConstant.INVALID_INPUT);
        }
        QuotaBalanceEntity row = balances.findWithLockByGrantIdAndMetric(grantId, metric).orElse(null);
        if (row == null) {
            row = new QuotaBalanceEntity(QuotaConstant.BALANCE_PREFIX + UUID.randomUUID(), grantId, metric, limit);
        } else {
            row.expandTo(limit);
        }
        if (windowAnchorAt != null) {
            row.initializeWindowAnchor(windowAnchorAt);
        }
        balances.saveAndFlush(row);
    }

    /** 原子检查并扣减（行锁围栏；余额不足返回 granted=false）。 */
    @Transactional(propagation = Propagation.REQUIRED)
    public QuotaOutcome checkAndReserve(String grantId, String metric, long quantity) {
        if (quantity <= 0L) {
            throw new IllegalArgumentException(QuotaConstant.INVALID_INPUT);
        }
        QuotaBalanceEntity row = balances.findWithLockByGrantIdAndMetric(grantId, metric).orElse(null);
        if (row == null) {
            return new QuotaOutcome(false, 0L, QuotaConstant.NO_BALANCE);
        }
        if (row.getRemaining() < quantity) {
            return new QuotaOutcome(false, row.getRemaining(), QuotaConstant.INSUFFICIENT);
        }
        row.deduct(quantity);
        // 围栏随余额行一起持久化，唯一键失败时同事务回滚。
        balances.saveAndFlush(row);
        return new QuotaOutcome(true, row.getRemaining(), QuotaConstant.OK, row.getFenceSequence());
    }

    /** 并发占用释放（仅 CONCURRENT；释放量不超已占）。 */
    @Transactional(propagation = Propagation.REQUIRED)
    public boolean release(String grantId, String metric, long quantity) {
        QuotaBalanceEntity row = balances.findWithLockByGrantIdAndMetric(grantId, metric).orElse(null);
        if (row == null || quantity <= 0 || quantity > row.getQuotaLimit() - row.getRemaining()) {
            return false;
        }
        row.release(quantity);
        balances.save(row);
        return true;
    }

    /** 额度操作事务先锁余额，再锁预占行，避免释放与回收形成相反锁序。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public QuotaBalanceEntity lock(String grantId, String metric) {
        return balances.findWithLockByGrantIdAndMetric(grantId, metric)
                .orElseThrow(() -> new JpaQuotaReservationService.QuotaUnavailableException(QuotaConstant.NO_BALANCE));
    }

    /** 持锁事务的余额变化与围栏一并flush；不在独立事务提前提交余额。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public QuotaBalanceEntity save(QuotaBalanceEntity row) {
        return balances.saveAndFlush(row);
    }

    @Transactional(readOnly = true)
    public long currentRemaining(String grantId, String metric) {
        return balances.findByGrantIdAndMetric(grantId, metric)
                .map(QuotaBalanceEntity::getRemaining).orElse(0L);
    }

    /** 预留结果（与进程内实现的结论形态对齐）。 */
    public static final class QuotaOutcome {

        public final boolean granted;
        public final long remainingAfter;
        public final String reason;
        public final long fence;

        public QuotaOutcome(boolean granted, long remainingAfter, String reason) {
            this(granted, remainingAfter, reason, 0L);
        }

        public QuotaOutcome(boolean granted, long remainingAfter, String reason, long fence) {
            this.granted = granted;
            this.remainingAfter = remainingAfter;
            this.reason = reason;
            this.fence = fence;
        }
    }
}
