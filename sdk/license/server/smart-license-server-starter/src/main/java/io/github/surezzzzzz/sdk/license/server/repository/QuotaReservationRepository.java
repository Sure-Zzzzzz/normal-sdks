package io.github.surezzzzzz.sdk.license.server.repository;

import io.github.surezzzzzz.sdk.license.server.entity.QuotaReservationEntity;
import java.util.Set;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 额度操作台账仓储：唯一键保证同一业务操作不会重复扣减。 */
public interface QuotaReservationRepository extends JpaRepository<QuotaReservationEntity, String>,
        JpaSpecificationExecutor<QuotaReservationEntity> {

    Optional<QuotaReservationEntity> findByOperationKey(String operationKey);

    /** 先持有同一grant/metric余额锁，再锁操作行，所有修改遵守同一顺序。 */
    @Lock(javax.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<QuotaReservationEntity> findWithLockByReservationId(String reservationId);

    /** 到期回收由下一次额度操作触发；不另启后台线程或增加宿主资源占用。 */
    @Lock(javax.persistence.LockModeType.PESSIMISTIC_WRITE)
    List<QuotaReservationEntity> findByGrantIdAndMetricAndKindAndStateAndValidUntilLessThanEqual(
            String grantId, String metric, String kind, String state, long now);

    /** 管理/运行读取共用同一授权 DATA 范围，过滤和分页在数据库内完成。 */
    default Page<QuotaReservationEntity> findVisiblePage(String grantId, String activationId,
                                                         String metric, String kind, String state,
                                                         Set<String> grantIds, Pageable pageable) {
        return findAll((root, query, builder) -> {
            List<javax.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (grantId != null && !grantId.isEmpty()) {
                predicates.add(builder.equal(root.get("grantId"), grantId));
            }
            if (activationId != null && !activationId.isEmpty()) {
                predicates.add(builder.equal(root.get("activationId"), activationId));
            }
            if (metric != null && !metric.isEmpty()) {
                predicates.add(builder.equal(root.get("metric"), metric));
            }
            if (kind != null && !kind.isEmpty()) {
                predicates.add(builder.equal(root.get("kind"), kind));
            }
            if (state != null && !state.isEmpty()) {
                predicates.add(builder.equal(root.get("state"), state));
            }
            if (grantIds != null) {
                predicates.add(grantIds.isEmpty()
                        ? builder.disjunction() : root.get("grantId").in(grantIds));
            }
            return builder.and(predicates.toArray(new javax.persistence.criteria.Predicate[0]));
        }, pageable);
    }
}
