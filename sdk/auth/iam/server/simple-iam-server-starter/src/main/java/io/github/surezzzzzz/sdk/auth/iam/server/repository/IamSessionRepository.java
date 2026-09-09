package io.github.surezzzzzz.sdk.auth.iam.server.repository;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamSessionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * IAM 会话 Repository
 *
 * @author surezzzzzz
 */
@Repository
public interface IamSessionRepository extends JpaRepository<IamSessionEntity, String> {

    /**
     * 根据用户ID查询活跃会话
     */
    List<IamSessionEntity> findByUserIdAndStatus(Long userId, Integer status);

    List<IamSessionEntity> findByUserId(Long userId);

    /**
     * 根据 access_token JTI 查询
     */
    Optional<IamSessionEntity> findByAccessTokenJti(String accessTokenJti);

    /**
     * 根据 refresh token 族ID 查询
     */
    Optional<IamSessionEntity> findByRefreshTokenFamilyId(String refreshTokenFamilyId);

    /**
     * 根据 Servlet 会话哈希查询活跃 IAM 会话
     */
    List<IamSessionEntity> findByServletSessionIdHashAndStatusOrderByIssuedAtDesc(String servletSessionIdHash, Integer status);

    /**
     * 查询已过期但仍标记为活跃的会话（用于清理）
     */
    List<IamSessionEntity> findByExpiresAtBeforeAndStatus(Instant expiresAt, Integer status);

    /**
     * 统计当前在线会话数（status=1 且未过期，与 SessionService 活跃判定一致；双实例共享 MySQL 天然全局）
     */
    long countByStatusAndExpiresAtAfter(Integer status, Instant expiresAt);

    /**
     * 管理台分页查询活跃会话（status=1 且未过期），按最近活跃倒序；userId 为 null 时查全部
     */
    @Query("SELECT s FROM IamSessionEntity s "
            + "WHERE s.status = 1 AND s.expiresAt > :now "
            + "AND (:userId IS NULL OR s.userId = :userId) "
            + "ORDER BY COALESCE(s.lastActiveAt, s.issuedAt) DESC")
    Page<IamSessionEntity> searchActiveSessions(@Param("userId") Long userId,
                                                @Param("now") Instant now,
                                                Pageable pageable);

    /**
     * 撤销用户的所有活跃会话
     */
    @Modifying
    @Query("UPDATE IamSessionEntity s SET s.status = 0, s.revokedAt = :revokedAt WHERE s.userId = :userId AND s.status = 1")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);

    /**
     * 会话滑动续期：只写活跃时间与有效期两列，status=1 守卫防并发撤销被覆盖复活
     *
     * @return 实际更新行数，会话已撤销时为 0
     */
    @Modifying
    @Query("UPDATE IamSessionEntity s SET s.lastActiveAt = :now, s.expiresAt = :expiresAt WHERE s.id = :id AND s.status = 1")
    int touchSession(@Param("id") String id, @Param("now") Instant now, @Param("expiresAt") Instant expiresAt);
}
