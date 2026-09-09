package io.github.surezzzzzz.sdk.auth.iam.server.repository;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserGroupEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * IAM 协作组 Repository
 *
 * @author surezzzzzz
 */
@Repository
public interface IamUserGroupRepository extends JpaRepository<IamUserGroupEntity, Long> {

    Optional<IamUserGroupEntity> findByCode(String code);

    boolean existsByCode(String code);

    @Query("SELECT g FROM IamUserGroupEntity g "
            + "WHERE (:status IS NULL OR g.status = :status) "
            + "AND (:keyword IS NULL "
            + "OR LOWER(g.code) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(g.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(g.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<IamUserGroupEntity> searchForConsole(@Param("status") Integer status,
                                              @Param("keyword") String keyword,
                                              Pageable pageable);
}
