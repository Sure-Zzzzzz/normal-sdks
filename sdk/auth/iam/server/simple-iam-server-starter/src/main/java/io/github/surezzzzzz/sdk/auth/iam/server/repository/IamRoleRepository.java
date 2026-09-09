package io.github.surezzzzzz.sdk.auth.iam.server.repository;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRoleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * IAM 角色 Repository
 *
 * @author surezzzzzz
 */
@Repository
public interface IamRoleRepository extends JpaRepository<IamRoleEntity, Long> {

    /**
     * 根据角色编码查询
     */
    Optional<IamRoleEntity> findByCode(String code);

    /**
     * 判断角色编码是否存在
     */
    boolean existsByCode(String code);

    /**
     * 管理台分页查询角色
     */
    @Query("SELECT r FROM IamRoleEntity r "
            + "WHERE (:keyword IS NULL OR LOWER(r.code) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(r.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<IamRoleEntity> searchForConsole(@Param("keyword") String keyword, Pageable pageable);
}
