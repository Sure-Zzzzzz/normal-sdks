package io.github.surezzzzzz.sdk.auth.iam.server.repository;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRolePermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * IAM 角色-权限关联 Repository
 *
 * @author surezzzzzz
 */
@Repository
public interface IamRolePermissionRepository extends JpaRepository<IamRolePermissionEntity, Long> {

    /**
     * 根据角色ID查询所有关联
     */
    List<IamRolePermissionEntity> findByRoleId(Long roleId);

    /**
     * 根据权限ID查询所有关联
     */
    List<IamRolePermissionEntity> findByPermissionId(Long permissionId);

    /**
     * 删除角色的所有权限关联
     */
    @Modifying
    @Query("DELETE FROM IamRolePermissionEntity rp WHERE rp.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 删除角色的指定权限关联
     */
    @Modifying
    @Query("DELETE FROM IamRolePermissionEntity rp WHERE rp.roleId = :roleId AND rp.permissionId = :permissionId")
    void deleteByRoleIdAndPermissionId(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    /**
     * 删除权限的所有角色关联
     */
    @Modifying
    @Query("DELETE FROM IamRolePermissionEntity rp WHERE rp.permissionId = :permissionId")
    void deleteByPermissionId(@Param("permissionId") Long permissionId);
}
