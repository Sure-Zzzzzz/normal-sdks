package io.github.surezzzzzz.sdk.license.server.repository;

import io.github.surezzzzzz.sdk.license.server.entity.RevocationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ActivationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SigningOperationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SubjectAssignmentEntity;
import io.github.surezzzzzz.sdk.license.server.constant.RevocationConstant;
import io.github.surezzzzzz.sdk.license.server.constant.RevocationTargetKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/** license_revocation 仓储（吊销只增不减）。 */
public interface RevocationRepository extends JpaRepository<RevocationEntity, String>,
        JpaSpecificationExecutor<RevocationEntity> {

    Optional<RevocationEntity> findByTargetKindAndTargetId(String targetKind, String targetId);

    /** 产品级完整导出：每种目标沿真实父关系归属产品，不能按编号或客户范围猜测。 */
    @Query("select r from RevocationEntity r where "
            + "(r.targetKind = 'GRANT' and exists (select g from GrantRevisionEntity g "
            + "where g.grantId = r.targetId and g.productId = ?1)) "
            + "or (r.targetKind = 'CREDENTIAL' and exists (select o from SigningOperationEntity o, "
            + "GrantRevisionEntity g where o.credentialId = r.targetId and g.grantId = o.grantId "
            + "and g.productId = ?1)) "
            + "or (r.targetKind = 'ACTIVATION' and exists (select a from ActivationEntity a, "
            + "GrantRevisionEntity g where a.activationId = r.targetId and g.grantId = a.grantId "
            + "and g.productId = ?1)) "
            + "or (r.targetKind = 'ASSIGNMENT' and exists (select a from SubjectAssignmentEntity a, "
            + "GrantRevisionEntity g where a.assignmentId = r.targetId and g.grantId = a.grantId "
            + "and g.productId = ?1)) "
            + "or (r.targetKind = 'SIGNING_KEY' and exists (select p from SigningProfileEntity p "
            + "where p.kid = r.targetId and p.productId = ?1)) order by r.revokedAt asc, r.revocationId asc")
    List<RevocationEntity> findForProduct(String productId);

    /** DATA 范围和目标筛选共同在 SQL 中求交后分页，计数与内容使用同一条件。 */
    default Page<RevocationEntity> findVisible(Set<String> grantIds, String targetKind, String targetId,
                                             Pageable pageable) {
        return findAll(visible(grantIds, targetKind, targetId), pageable);
    }

    /** 详情与列表使用同一范围，目标编号不能直接当作授权编号。 */
    default Optional<RevocationEntity> findVisibleById(String revocationId, Set<String> grantIds) {
        return findOne(visible(grantIds, null, null).and((root, query, builder) ->
                builder.equal(root.get(RevocationConstant.REVOCATION_ID), revocationId)));
    }

    /**
     * 签名密钥可覆盖多份授权，受限 grantId 范围不拥有它的全局台账；仅 ALL 范围可见。
     * 其余三类子资源通过真实持久关系关联，不按编号相同猜测授权归属。
     */
    static Specification<RevocationEntity> visible(Set<String> grantIds, String targetKind, String targetId) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get(RevocationConstant.TARGET_KIND).in((Object[]) RevocationTargetKind.getAllCodes()));
            if (grantIds != null) {
                if (grantIds.isEmpty()) {
                    predicates.add(builder.disjunction());
                } else {
                    Predicate grant = builder.and(
                            builder.equal(root.get(RevocationConstant.TARGET_KIND), RevocationTargetKind.GRANT.getCode()),
                            root.get(RevocationConstant.TARGET_ID).in(grantIds));
                    predicates.add(builder.or(grant,
                            owned(root, query, builder, SigningOperationEntity.class,
                                    RevocationTargetKind.CREDENTIAL, RevocationConstant.CREDENTIAL_ID, grantIds),
                            owned(root, query, builder, ActivationEntity.class,
                                    RevocationTargetKind.ACTIVATION, RevocationConstant.ACTIVATION_ID, grantIds),
                            owned(root, query, builder, SubjectAssignmentEntity.class,
                                    RevocationTargetKind.ASSIGNMENT, RevocationConstant.ASSIGNMENT_ID, grantIds)));
                }
            }
            if (targetKind != null && !targetKind.isEmpty()) {
                predicates.add(builder.equal(root.get(RevocationConstant.TARGET_KIND), targetKind));
            }
            if (targetId != null && !targetId.isEmpty()) {
                predicates.add(builder.equal(root.get(RevocationConstant.TARGET_ID), targetId));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** 用 EXISTS 避免一个关联目标产生重复台账行或放大分页计数。 */
    static <T> Predicate owned(Root<RevocationEntity> revocation, CriteriaQuery<?> query, CriteriaBuilder builder,
                               Class<T> targetClass, RevocationTargetKind kind, String targetField,
                               Set<String> grantIds) {
        Subquery<Integer> ownership = query.subquery(Integer.class);
        Root<T> target = ownership.from(targetClass);
        ownership.select(builder.literal(1)).where(
                builder.equal(target.get(targetField), revocation.get(RevocationConstant.TARGET_ID)),
                target.get(RevocationConstant.GRANT_ID).in(grantIds));
        return builder.and(
                builder.equal(revocation.get(RevocationConstant.TARGET_KIND), kind.getCode()),
                builder.exists(ownership));
    }
}
