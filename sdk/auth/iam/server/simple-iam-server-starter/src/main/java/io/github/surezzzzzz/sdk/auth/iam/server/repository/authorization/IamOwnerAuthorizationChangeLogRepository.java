package io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamOwnerAuthorizationChangeLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * IAM 到协作消费方的授权控制面日志仓储。
 *
 * @author surezzzzzz
 */
public interface IamOwnerAuthorizationChangeLogRepository
        extends JpaRepository<IamOwnerAuthorizationChangeLogEntity, Long> {

    /**
     * 按全局顺序读取后续变更，不使用 offset，避免并发清理导致跳项。
     */
    List<IamOwnerAuthorizationChangeLogEntity> findTop200BySourceSequenceGreaterThanOrderBySourceSequenceAsc(
            Long sourceSequence);

    List<IamOwnerAuthorizationChangeLogEntity> findBySourceSequenceGreaterThanOrderBySourceSequenceAsc(
            Long sourceSequence, Pageable pageable);

    IamOwnerAuthorizationChangeLogEntity findFirstByOrderBySourceSequenceAsc();

    IamOwnerAuthorizationChangeLogEntity findFirstByOrderBySourceSequenceDesc();
}
