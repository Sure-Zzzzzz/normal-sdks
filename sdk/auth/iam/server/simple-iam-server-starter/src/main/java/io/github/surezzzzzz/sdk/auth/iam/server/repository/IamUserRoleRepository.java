package io.github.surezzzzzz.sdk.auth.iam.server.repository;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserRoleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * IAM 用户-角色关联 Repository
 *
 * @author surezzzzzz
 */
@Repository
public interface IamUserRoleRepository extends JpaRepository<IamUserRoleEntity, Long> {

    /**
     * 根据用户ID查询所有关联
     */
    List<IamUserRoleEntity> findByUserId(Long userId);

    /**
     * 根据角色ID查询所有关联
     */
    List<IamUserRoleEntity> findByRoleId(Long roleId);

    /**
     * 根据角色ID集合查询所有关联（角色覆盖用户计算）
     */
    List<IamUserRoleEntity> findByRoleIdIn(Collection<Long> roleIds);

    /**
     * 根据角色ID分页查询关联（角色成员列表）
     */
    Page<IamUserRoleEntity> findByRoleId(Long roleId, Pageable pageable);

    /**
     * 删除用户的所有角色关联
     */
    @Modifying
    @Query("DELETE FROM IamUserRoleEntity ur WHERE ur.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /**
     * 删除用户的指定角色关联
     */
    @Modifying
    @Query("DELETE FROM IamUserRoleEntity ur WHERE ur.userId = :userId AND ur.roleId = :roleId")
    void deleteByUserIdAndRoleId(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
