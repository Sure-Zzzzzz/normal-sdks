package io.github.surezzzzzz.sdk.auth.iam.server.repository;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamPermissionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * IAM 权限 Repository
 *
 * @author surezzzzzz
 */
@Repository
public interface IamPermissionRepository extends JpaRepository<IamPermissionEntity, Long> {

    /**
     * 根据权限编码查询
     */
    Optional<IamPermissionEntity> findByCode(String code);

    /**
     * 判断权限编码是否存在
     */
    boolean existsByCode(String code);

    /**
     * 管理台分页查询权限
     */
    @Query("SELECT p FROM IamPermissionEntity p "
            + "WHERE (:keyword IS NULL OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<IamPermissionEntity> searchForConsole(@Param("keyword") String keyword, Pageable pageable);
}
