package io.github.surezzzzzz.sdk.auth.iam.server.repository;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamPasswordResetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * IAM 密码重置凭证 Repository
 *
 * @author surezzzzzz
 */
@Repository
public interface IamPasswordResetRepository extends JpaRepository<IamPasswordResetEntity, String> {

    /**
     * 根据凭证哈希查询（用于验证重置请求）
     */
    Optional<IamPasswordResetEntity> findByTokenHash(String tokenHash);

    /**
     * 查询部署恢复凭据
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IamPasswordResetEntity> findByIdAndRequestedBy(String id, String requestedBy);

    /**
     * 根据用户ID查询
     */
    List<IamPasswordResetEntity> findByUserId(Long userId);

    /**
     * 查询已过期但仍有效的凭证（用于清理）
     */
    List<IamPasswordResetEntity> findByExpiresAtBeforeAndStatus(Instant expiresAt, Integer status);
}
