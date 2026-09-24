package io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;

import java.util.Optional;

/**
 * IAM 可信应用 Repository
 *
 * @author surezzzzzz
 */
@Repository
public interface IamTrustedApplicationRepository extends JpaRepository<IamTrustedApplicationEntity, Long> {

    /**
     * 根据应用编码查询
     */
    Optional<IamTrustedApplicationEntity> findByApplicationCode(String applicationCode);

    /**
     * 配置写入和删除受理共用的应用行锁，防止 DELETE 冻结 client 快照后仍有写入穿透。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT application FROM IamTrustedApplicationEntity application WHERE application.id = :applicationId")
    Optional<IamTrustedApplicationEntity> findByIdForUpdate(@Param("applicationId") Long applicationId);

    /**
     * 判断应用编码是否存在
     */
    boolean existsByApplicationCode(String applicationCode);

    /**
     * 管理台分页查询可信应用
     */
    @Query("SELECT a FROM IamTrustedApplicationEntity a "
            + "WHERE (:keyword IS NULL OR LOWER(a.applicationCode) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(a.applicationName) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(a.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<IamTrustedApplicationEntity> searchForConsole(@Param("keyword") String keyword, Pageable pageable);
}
