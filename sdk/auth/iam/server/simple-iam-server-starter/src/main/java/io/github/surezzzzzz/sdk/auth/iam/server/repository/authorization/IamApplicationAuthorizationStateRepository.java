package io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamApplicationAuthorizationStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.util.Optional;

/**
 * IAM 可信应用 AKU 授权状态 Repository。
 */
@Repository
public interface IamApplicationAuthorizationStateRepository
        extends JpaRepository<IamApplicationAuthorizationStateEntity, Long>,
        IamApplicationAuthorizationStateRepositoryCustom {

    /**
     * 屏障状态操作必须串行化；事务提交前锁由数据库持有。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT state FROM IamApplicationAuthorizationStateEntity state " +
            "WHERE state.applicationId = :applicationId")
    Optional<IamApplicationAuthorizationStateEntity> findByApplicationIdForUpdate(
            @Param("applicationId") Long applicationId);
}
