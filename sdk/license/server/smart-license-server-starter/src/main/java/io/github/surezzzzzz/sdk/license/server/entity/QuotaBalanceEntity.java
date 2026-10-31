package io.github.surezzzzzz.sdk.license.server.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;
import javax.persistence.UniqueConstraint;
import lombok.Getter;

/**
 * 额度余额行（grant×metric 唯一）：限额/剩余/行版本。
 *
 * <p>原子预留走数据库悲观行锁；行版本提供持久围栏，扩容按差额追加保留已消费；
 * 业务操作幂等键去重在操作表（本表只承载余额事实）。</p>
 *
 * @author surezzzzzz
 */
@Entity
@Getter
@Table(name = "license_quota_balance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"grant_id", "metric"}))
public class QuotaBalanceEntity {

    @Id
    @Column(name = "balance_id", length = 64, nullable = false)
    private String balanceId;

    @Column(name = "grant_id", length = 128, nullable = false)
    private String grantId;

    @Column(name = "metric", length = 128, nullable = false)
    private String metric;

    @Column(name = "quota_limit", nullable = false)
    private long quotaLimit;

    @Column(name = "remaining", nullable = false)
    private long remaining;

    /** 累计消费当前固定窗口截止；TOTAL/CONCURRENT为0，不按进程启动时刻重置。 */
    @Column(name = "window_until", nullable = false)
    private long windowUntil;

    /** 首次引入指标的商业开始时刻；后续修订不重置窗口锚。 */
    @Column(name = "window_anchor_at")
    private Long windowAnchorAt;

    @Column(name = "window_period_seconds")
    private Long windowPeriodSeconds;

    /** 所有余额操作及续期共用的持久序号；不以进程时钟生成围栏。 */
    @Column(name = "fence_sequence", nullable = false)
    private long fenceSequence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected QuotaBalanceEntity() {
    }

    public QuotaBalanceEntity(String balanceId, String grantId, String metric, long quotaLimit) {
        if (quotaLimit < 0L) {
            throw new IllegalArgumentException("额度限额不能为负数");
        }
        this.balanceId = balanceId;
        this.grantId = grantId;
        this.metric = metric;
        this.quotaLimit = quotaLimit;
        this.remaining = quotaLimit;
        this.createdAt = Instant.now();
    }

    /** 扩容按差额追加剩余（保留已消费）；减配走治理路径不在此处理。 */
    public void expandTo(long newLimit) {
        if (newLimit < 0L) {
            throw new IllegalArgumentException("额度限额不能为负数");
        }
        if (newLimit > quotaLimit) {
            this.remaining += newLimit - quotaLimit;
            this.quotaLimit = newLimit;
            nextFence();
        }
    }

    /** 扣减；必须由持有余额行锁的事务调用。 */
    public void deduct(long quantity) {
        if (quantity <= 0L || quantity > remaining) {
            throw new IllegalArgumentException("额度扣减数量无效");
        }
        this.remaining -= quantity;
        nextFence();
    }

    /** 释放（不超已占：{@code remaining + quantity <= quotaLimit}）。 */
    public void release(long quantity) {
        if (quantity <= 0L || quantity > quotaLimit - remaining) {
            throw new IllegalArgumentException("额度释放数量无效");
        }
        this.remaining += quantity;
        nextFence();
    }

    /** 进入新的固定消费窗口，在同一行锁下重置本窗口余额。 */
    public void advanceWindow(long nextWindowUntil) {
        if (nextWindowUntil <= 0L || nextWindowUntil <= windowUntil) {
            throw new IllegalArgumentException("额度窗口必须单调推进");
        }
        this.windowUntil = nextWindowUntil;
        this.remaining = quotaLimit;
        nextFence();
    }

    /** 窗口锚只允许初始化一次；不得由续期或商业修订覆盖。 */
    public void initializeWindowAnchor(long anchorAt) {
        if (windowAnchorAt == null) {
            windowAnchorAt = anchorAt;
        }
    }

    /** 已引入的消费指标不允许通过修改周期清空水位；新计费周期使用新的grant。 */
    public void initializeWindowPeriod(long periodSeconds) {
        if (periodSeconds <= 0L || windowPeriodSeconds != null && windowPeriodSeconds != periodSeconds) {
            throw new IllegalArgumentException("额度窗口周期不匹配");
        }
        windowPeriodSeconds = periodSeconds;
    }

    /** 续期同样推进数据库序号，即使额度数量没有变化。 */
    public long nextFence() {
        fenceSequence = Math.addExact(Math.max(fenceSequence, rowVersion), 1L);
        return fenceSequence;
    }
}
