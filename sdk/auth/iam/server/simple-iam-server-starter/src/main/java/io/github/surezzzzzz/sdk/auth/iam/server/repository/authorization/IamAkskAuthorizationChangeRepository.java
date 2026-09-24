package io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamAkskAuthorizationChangeEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * IAM 到 AKSK 的授权控制面日志仓储。
 *
 * @author surezzzzzz
 */
public interface IamAkskAuthorizationChangeRepository
        extends JpaRepository<IamAkskAuthorizationChangeEntity, Long> {

    /**
     * 按全局顺序读取后续变更，不使用 offset，避免并发清理导致跳项。
     */
    List<IamAkskAuthorizationChangeEntity> findTop200BySourceSequenceGreaterThanOrderBySourceSequenceAsc(
            Long sourceSequence);

    List<IamAkskAuthorizationChangeEntity> findBySourceSequenceGreaterThanOrderBySourceSequenceAsc(
            Long sourceSequence, Pageable pageable);

    IamAkskAuthorizationChangeEntity findFirstByOrderBySourceSequenceAsc();

    IamAkskAuthorizationChangeEntity findFirstByOrderBySourceSequenceDesc();
}
