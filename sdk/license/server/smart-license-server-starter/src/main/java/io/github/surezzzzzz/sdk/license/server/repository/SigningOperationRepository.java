package io.github.surezzzzzz.sdk.license.server.repository;

import io.github.surezzzzzz.sdk.license.server.constant.SigningOperationConstant;
import io.github.surezzzzzz.sdk.license.server.entity.SigningOperationEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** license_signing_operation 仓储（签发任务：冻结输入+状态机+提交签名）。 */
public interface SigningOperationRepository extends JpaRepository<SigningOperationEntity, String>,
        JpaSpecificationExecutor<SigningOperationEntity> {

    Page<SigningOperationEntity> findByGrantIdOrderByRowVersionDesc(Pageable pageable, String grantId);

    SigningOperationEntity findByCredentialId(String credentialId);

    /** 幂等首任务定位（真幂等：同摘要重试命中首任务）。 */
    SigningOperationEntity findByIdempotencyDigest(String idempotencyDigest);

    /** 单条条件更新先认领再执行；迟到工作者不能覆盖新代数、已恢复或已放弃的任务。 */
    @Modifying(clearAutomatically = true)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @Query("update SigningOperationEntity operation set operation.state = :state, "
            + "operation.executionGeneration = :generation, operation.committedSignature = :signature, "
            + "operation.reasonCategory = :reason, operation.lastKmsCallRef = :callRef, "
            + "operation.lastKmsCallAt = :callAt, operation.rowVersion = operation.rowVersion + 1 "
            + "where operation.operationId = :operationId and operation.rowVersion = :expectedVersion "
            + "and operation.executionGeneration = :expectedGeneration")
    int compareAndSet(@Param("operationId") String operationId,
                      @Param("expectedVersion") long expectedVersion,
                      @Param("expectedGeneration") long expectedGeneration,
                      @Param("state") String state,
                      @Param("generation") long generation,
                      @Param("signature") String signature,
                      @Param("reason") String reason,
                      @Param("callRef") String callRef,
                      @Param("callAt") java.time.Instant callAt);

    /** 销毁保护判定：档案名下已提交（READY）的凭据签名任务（历史凭据引用面）。 */
    java.util.List<SigningOperationEntity> findBySigningProfileIdAndState(String signingProfileId, String state);

    /** 受限 DATA 范围列表过滤（凭据元数据按授权范围）。 */
    Page<SigningOperationEntity> findByGrantIdIn(Iterable<String> grantIds, Pageable pageable);

    /**
     * DATA 范围、授权筛选与激活筛选在数据库中求交后分页，计数查询使用同一条件。
     *
     * @param allowedGrantIds 已评估的 DATA 主键集合；null 代表全量，空集合必须返回空页
     * @param grantId         可选授权筛选
     * @param activationId    可选部署激活筛选
     * @param pageable        带稳定排序的页码请求
     * @return 条件交集的真实分页
     */
    default Page<SigningOperationEntity> findCredentials(Set<String> allowedGrantIds,
                                                        String grantId, String activationId,
                                                        Pageable pageable) {
        return findAll((root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (allowedGrantIds != null) {
                predicates.add(allowedGrantIds.isEmpty() ? builder.disjunction()
                        : root.get(SigningOperationConstant.GRANT_ID).in(allowedGrantIds));
            }
            if (grantId != null && !grantId.isEmpty()) {
                predicates.add(builder.equal(root.get(SigningOperationConstant.GRANT_ID), grantId));
            }
            if (activationId != null && !activationId.isEmpty()) {
                predicates.add(builder.equal(root.get(SigningOperationConstant.ACTIVATION_ID), activationId));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }
}
