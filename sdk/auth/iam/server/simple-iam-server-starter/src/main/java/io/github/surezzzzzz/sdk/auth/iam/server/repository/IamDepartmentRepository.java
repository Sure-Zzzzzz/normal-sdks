package io.github.surezzzzzz.sdk.auth.iam.server.repository;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamDepartmentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * IAM 部门 Repository
 *
 * @author surezzzzzz
 */
@Repository
public interface IamDepartmentRepository extends JpaRepository<IamDepartmentEntity, Long> {

    Optional<IamDepartmentEntity> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByParentId(Long parentId);

    List<IamDepartmentEntity> findByParentId(Long parentId);

    List<IamDepartmentEntity> findByParentIdAndStatus(Long parentId, Integer status);

    @Query("SELECT d FROM IamDepartmentEntity d "
            + "WHERE (:status IS NULL OR d.status = :status) "
            + "AND (:keyword IS NULL "
            + "OR LOWER(d.code) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<IamDepartmentEntity> searchForConsole(@Param("status") Integer status,
                                               @Param("keyword") String keyword,
                                               Pageable pageable);
}
