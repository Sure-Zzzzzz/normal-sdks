package io.github.surezzzzzz.sdk.auth.iam.server.repository;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamDepartmentRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * IAM 部门-角色关联 Repository
 *
 * @author surezzzzzz
 */
@Repository
public interface IamDepartmentRoleRepository extends JpaRepository<IamDepartmentRoleEntity, Long> {

    /**
     * 根据部门ID查询所有关联
     */
    List<IamDepartmentRoleEntity> findByDepartmentId(Long departmentId);

    /**
     * 根据部门ID集合查询所有关联（用户有效角色合并计算）
     */
    List<IamDepartmentRoleEntity> findByDepartmentIdIn(Collection<Long> departmentIds);

    /**
     * 根据角色ID查询所有关联（角色反查覆盖部门）
     */
    List<IamDepartmentRoleEntity> findByRoleId(Long roleId);

    /**
     * 根据角色ID集合查询所有关联
     */
    List<IamDepartmentRoleEntity> findByRoleIdIn(Collection<Long> roleIds);

    /**
     * 判断部门是否已挂载指定角色
     */
    boolean existsByDepartmentIdAndRoleId(Long departmentId, Long roleId);

    /**
     * 删除部门的指定角色挂载
     */
    @Modifying
    @Query("DELETE FROM IamDepartmentRoleEntity dr WHERE dr.departmentId = :departmentId AND dr.roleId = :roleId")
    void deleteByDepartmentIdAndRoleId(@Param("departmentId") Long departmentId, @Param("roleId") Long roleId);

    /**
     * 删除角色的所有部门挂载（角色被删除时级联清理）
     */
    @Modifying
    @Query("DELETE FROM IamDepartmentRoleEntity dr WHERE dr.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 删除部门的所有角色挂载（部门被删除时级联清理）
     */
    @Modifying
    @Query("DELETE FROM IamDepartmentRoleEntity dr WHERE dr.departmentId = :departmentId")
    void deleteByDepartmentId(@Param("departmentId") Long departmentId);
}
