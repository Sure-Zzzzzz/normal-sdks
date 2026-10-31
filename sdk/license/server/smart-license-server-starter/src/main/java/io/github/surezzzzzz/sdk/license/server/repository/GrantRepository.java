package io.github.surezzzzzz.sdk.license.server.repository;

import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

/** license_grant 仓储（商业授权主行：客户×产品×对象种类×模式+管理状态）。 */
public interface GrantRepository extends JpaRepository<GrantRevisionEntity, String>,
        JpaSpecificationExecutor<GrantRevisionEntity> {

    /** LOCAL额度分配总量校验的数据库围栏；同一授权的并发激活必须串行读取余额事实。 */
    @Lock(javax.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GrantRevisionEntity g where g.grantId = :grantId")
    Optional<GrantRevisionEntity> findWithLockByGrantId(@Param("grantId") String grantId);

    /** 同一客户的授权状态变更共用客户行锁，避免两个授权同时抢占同一全局限频资源。 */
    @Lock(javax.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GrantRevisionEntity g where g.customerId = :customerId order by g.grantId")
    java.util.List<GrantRevisionEntity> findAllWithLockByCustomerId(@Param("customerId") String customerId);

    /** 基础入口贡献只聚合同客户产品的授权，不合并商业权益。 */
    java.util.List<GrantRevisionEntity> findByCustomerIdAndProductIdOrderByGrantId(String customerId, String productId);

    Page<GrantRevisionEntity> findByCustomerId(Pageable pageable, String customerId);

    Page<GrantRevisionEntity> findByProductId(Pageable pageable, String productId);

    /** 到期扫描只捞仍处于 ENABLED 且已过服务截止的授权，永久授权不进入该集合。 */
    @Lock(javax.persistence.LockModeType.PESSIMISTIC_WRITE)
    Page<GrantRevisionEntity> findByStateAndServiceUntilLessThanEqual(
            String state, Long serviceUntil, Pageable pageable);

    /** 幂等定位：履约协调以履约号作业务订单引用（重放跳过建权）。 */
    java.util.List<GrantRevisionEntity> findByBusinessOrderRef(String businessOrderRef);

    /** 受限 DATA 范围列表过滤（IN 集合内分页）。 */
    Page<GrantRevisionEntity> findByGrantIdIn(Iterable<String> grantIds, Pageable pageable);

    /** 管理列表的客户、产品与 DATA 条件在同一 SQL 内求交，count 不丢筛选条件。 */
    default Page<GrantRevisionEntity> findVisiblePage(String customerId, String productId,
                                                     java.util.Set<String> grantIds, Pageable pageable) {
        return findAll((root, query, builder) -> {
            java.util.List<javax.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (customerId != null && !customerId.isEmpty()) {
                predicates.add(builder.equal(root.get("customerId"), customerId));
            }
            if (productId != null && !productId.isEmpty()) {
                predicates.add(builder.equal(root.get("productId"), productId));
            }
            if (grantIds != null) {
                predicates.add(grantIds.isEmpty() ? builder.disjunction() : root.get("grantId").in(grantIds));
            }
            return builder.and(predicates.toArray(new javax.persistence.criteria.Predicate[0]));
        }, pageable);
    }

    /** 候选发现按真实产品、对象及 DATA 在 SQL 中求交，以主键继续扫描，避免首批截断。 */
    default Page<GrantRevisionEntity> findSelectionBatch(String productId, String subjectKind,
                                                        java.util.Set<String> grantIds, String afterId,
                                                        Pageable pageable) {
        return findAll((root, query, builder) -> {
            java.util.List<javax.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            predicates.add(builder.equal(root.get("productId"), productId));
            predicates.add(builder.equal(root.get("subjectKind"), subjectKind));
            predicates.add(builder.equal(root.get("state"), "ENABLED"));
            if (grantIds != null) {
                predicates.add(grantIds.isEmpty() ? builder.disjunction() : root.get("grantId").in(grantIds));
            }
            if (afterId != null) {
                predicates.add(builder.greaterThan(root.<String>get("grantId"), afterId));
            }
            return builder.and(predicates.toArray(new javax.persistence.criteria.Predicate[0]));
        }, pageable);
    }
}
