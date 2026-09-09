package io.github.surezzzzzz.sdk.auth.iam.server.repository;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamApplicationAuthorizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
     * 删除用户的全部应用授权投影（用户删除时级联，避免孤儿投影行）。
     */
    void deleteByUserId(Long userId);

    /**
     * 删除应用的所有用户授权投影（应用删除时级联，避免孤儿投影行）。
     */
    void deleteByApplicationId(Long applicationId);
}
