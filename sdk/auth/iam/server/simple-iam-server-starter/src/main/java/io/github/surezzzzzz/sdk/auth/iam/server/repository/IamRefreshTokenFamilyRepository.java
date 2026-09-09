package io.github.surezzzzzz.sdk.auth.iam.server.repository;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRefreshTokenFamilyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * IAM Refresh Token 族 Repository
 *
 * @author surezzzzzz
 */
@Repository
public interface IamRefreshTokenFamilyRepository extends JpaRepository<IamRefreshTokenFamilyEntity, String> {

    /**
     * 根据当前 token 哈希查询（用于验证 + 重用检测）
     */
    Optional<IamRefreshTokenFamilyEntity> findByCurrentTokenHash(String currentTokenHash);

    /**
     * 根据上一个 token 哈希查询（用于重用检测：旧 token 再次出现则整族撤销）
     */
    Optional<IamRefreshTokenFamilyEntity> findByPreviousTokenHash(String previousTokenHash);

    /**
     * 根据用户ID查询活跃族
     */
    List<IamRefreshTokenFamilyEntity> findByUserIdAndStatus(Long userId, Integer status);

    List<IamRefreshTokenFamilyEntity> findByUserId(Long userId);

    /**
     * 根据会话ID查询
     */
    Optional<IamRefreshTokenFamilyEntity> findBySessionId(String sessionId);

    /**
     * 查询已过期但仍活跃的族（用于清理）
     */
    List<IamRefreshTokenFamilyEntity> findByExpiresAtBeforeAndStatus(Instant expiresAt, Integer status);

    /**
     * 撤销用户的所有活跃族
     */
    @Modifying
    @Query("UPDATE IamRefreshTokenFamilyEntity f SET f.status = 0, f.revokedAt = :revokedAt WHERE f.userId = :userId AND f.status = 1")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);

    /**
     * 分批物理删除已过期的族（每批独立事务防长事务锁表；过期即无防御价值，直接删行）
     *
     * @param now   当前时间
     * @param limit 单批删除行数上限
     * @return 本批删除的记录数
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM iam_refresh_token_family WHERE expires_at < :now LIMIT :limit",
            countQuery = "SELECT 1", nativeQuery = true)
    int deleteExpiredBatch(@Param("now") Instant now, @Param("limit") int limit);
}
