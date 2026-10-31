package io.github.surezzzzzz.sdk.license.server.repository;

import io.github.surezzzzzz.sdk.license.server.entity.QuotaBalanceEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

/** license_quota_balance 仓储（额度余额行：grant×metric 唯一）。 */
public interface QuotaBalanceRepository extends JpaRepository<QuotaBalanceEntity, String> {

    Optional<QuotaBalanceEntity> findByGrantIdAndMetric(String grantId, String metric);

    /** 悲观行锁装载（多实例原子预留的数据库围栏）。 */
    @Lock(javax.persistence.LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@javax.persistence.QueryHint(name = "javax.persistence.lock.timeout", value = "3000"))
    Optional<QuotaBalanceEntity> findWithLockByGrantIdAndMetric(String grantId, String metric);
}
