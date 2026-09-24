package io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamApplicationAuthorizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * IAM 用户应用授权投影 Repository。
 *
 * @author surezzzzzz
 */
@Repository
public interface IamApplicationAuthorizationRepository
        extends JpaRepository<IamApplicationAuthorizationEntity, Long> {

    /**
     * 按用户与可信应用查询授权投影。
     */
    Optional<IamApplicationAuthorizationEntity> findByUserIdAndApplicationId(
            Long userId, Long applicationId);

    /**
     * 按用户查询其全部应用授权投影。
     */
    List<IamApplicationAuthorizationEntity> findByUserId(Long userId);

    /**
     * 按应用查询所有用户的授权投影（用于清单更新时批量同步）。
     */
    List<IamApplicationAuthorizationEntity> findByApplicationId(Long applicationId);

    /**
     * 应用授权纪元推进后，同步既存投影的纪元标签，不改变投影内容或业务版本。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE IamApplicationAuthorizationEntity authorization "
            + "SET authorization.applicationAuthorizationEpoch = :authorizationEpoch, "
            + "authorization.updatedAt = :updatedAt "
            + "WHERE authorization.applicationId = :applicationId "
            + "AND (authorization.applicationAuthorizationEpoch IS NULL "
            + "OR authorization.applicationAuthorizationEpoch <> :authorizationEpoch)")
    int synchronizeApplicationAuthorizationEpoch(@Param("applicationId") Long applicationId,
                                                 @Param("authorizationEpoch") Long authorizationEpoch,
                                                 @Param("updatedAt") Instant updatedAt);

    /**
     * 删除用户的全部应用授权投影（用户删除时级联，避免孤儿投影行）。
     */
    void deleteByUserId(Long userId);

    /**
     * 删除应用的所有用户授权投影（应用删除时级联，避免孤儿投影行）。
     */
    void deleteByApplicationId(Long applicationId);
}
