package io.github.surezzzzzz.sdk.auth.iam.server.repository;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * IAM 用户 Repository
 *
 * @author surezzzzzz
 */
@Repository
public interface IamUserRepository extends JpaRepository<IamUserEntity, Long> {

    /**
     * 根据用户名查询
     */
    Optional<IamUserEntity> findByUsername(String username);

    /**
     * 判断用户名是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 根据外部身份源与外部 ID 查询已绑定用户
     */
    Optional<IamUserEntity> findByIdentitySourceAndExternalId(String identitySource, String externalId);

    /**
     * 判断外部身份是否已被绑定
     */
    boolean existsByIdentitySourceAndExternalId(String identitySource, String externalId);

    /**
     * 根据状态查询
     */
    List<IamUserEntity> findByStatus(Integer status);

    List<IamUserEntity> findByDepartmentId(Long departmentId);

    List<IamUserEntity> findByDepartmentIdIn(List<Long> departmentIds);

    List<IamUserEntity> findByDepartmentIdInAndStatus(List<Long> departmentIds, Integer status);

    boolean existsByDepartmentId(Long departmentId);

    /**
     * 按部门统计直属成员数
     *
     * <p>统计全部归属用户，包含停用用户，不包含未归属部门的用户。
     *
     * @return 部门直属成员数
     */
    @Query("SELECT u.departmentId AS departmentId, COUNT(u.id) AS memberCount "
            + "FROM IamUserEntity u WHERE u.departmentId IS NOT NULL GROUP BY u.departmentId")
    List<DepartmentMemberCount> countDirectMembersByDepartment();

    /**
     * 管理台分页查询用户（lastLoginAfter/lockedUntilAfter/noDepartment 为仪表盘下钻筛选：今日登录、锁定中、未挂部门）
     */
    @Query("SELECT u FROM IamUserEntity u "
            + "WHERE (:status IS NULL OR u.status = :status) "
            + "AND (:departmentId IS NULL OR u.departmentId = :departmentId) "
            + "AND (:keyword IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "AND (:lastLoginAfter IS NULL OR u.lastLoginAt >= :lastLoginAfter) "
            + "AND (:lockedUntilAfter IS NULL OR u.lockedUntil > :lockedUntilAfter) "
            + "AND (:noDepartment = false OR u.departmentId IS NULL)")
    Page<IamUserEntity> searchForConsole(@Param("status") Integer status,
                                         @Param("departmentId") Long departmentId,
                                         @Param("keyword") String keyword,
                                         @Param("lastLoginAfter") Instant lastLoginAfter,
                                         @Param("lockedUntilAfter") Instant lockedUntilAfter,
                                         @Param("noDepartment") boolean noDepartment,
                                         Pageable pageable);

    /**
     * 开放 API 分页查询用户：DATA 受限部门范围与请求条件（含单值 departmentId）求交，越权数据不出库
     */
    @Query("SELECT u FROM IamUserEntity u "
            + "WHERE (:status IS NULL OR u.status = :status) "
            + "AND (:departmentId IS NULL OR u.departmentId = :departmentId) "
            + "AND (:keyword IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "AND u.departmentId IN :departmentScope")
    Page<IamUserEntity> searchForRestApi(@Param("status") Integer status,
                                         @Param("departmentId") Long departmentId,
                                         @Param("keyword") String keyword,
                                         @Param("departmentScope") Collection<Long> departmentScope,
                                         Pageable pageable);

    /**
     * 统计指定时刻之后登录过的用户数（仪表盘"今日登录用户"口径：last_login_at >= 今日零点）
     */
    long countByLastLoginAtAfter(Instant after);

    /**
     * 统计当前处于锁定中的用户数（locked_until > now）
     */
    long countByLockedUntilAfter(Instant after);

    /**
     * 按最后登录时间倒序分页查询登录过的用户（仪表盘最近登录列表）
     */
    Page<IamUserEntity> findByLastLoginAtIsNotNull(Pageable pageable);

    /**
     * 统计指定状态的用户数（仪表盘治理缺口：禁用账号数）
     */
    long countByStatus(Integer status);

    /**
     * 统计未归属部门的用户数（仪表盘治理缺口）
     */
    long countByDepartmentIdIsNull();

    /**
     * 递增指定用户的权限版本（角色/权限/绑定变更时调用，触发在线会话热刷新）
     */
    @Modifying
    @Query("UPDATE IamUserEntity u SET u.permissionVersion = u.permissionVersion + 1 WHERE u.id IN :userIds")
    void bumpPermissionVersion(@Param("userIds") Collection<Long> userIds);
}
