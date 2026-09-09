package io.github.surezzzzzz.sdk.auth.iam.server.repository;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamApplicationPermissionManifestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 可信应用权限清单仓储：每应用一行的申报制清单（角色 / 页面 / 接口权限码），
 * 是应用授权勾选范围与授权规则码空间校验的事实源。
 *
 * @author surezzzzzz
 */
@Repository
public interface IamApplicationPermissionManifestRepository
        extends JpaRepository<IamApplicationPermissionManifestEntity, Long> {

    Optional<IamApplicationPermissionManifestEntity> findByApplicationId(Long applicationId);

    void deleteByApplicationId(Long applicationId);
}
