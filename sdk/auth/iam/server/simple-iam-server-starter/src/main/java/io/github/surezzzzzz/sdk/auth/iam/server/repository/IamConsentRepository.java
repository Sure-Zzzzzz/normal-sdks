package io.github.surezzzzzz.sdk.auth.iam.server.repository;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamConsentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * IAM 授权确认投影 Repository
 *
 * @author surezzzzzz
 */
@Repository
public interface IamConsentRepository extends JpaRepository<IamConsentEntity, String> {

    /**
     * 根据用户和 SAS 注册客户端查询授权确认投影
     */
    Optional<IamConsentEntity> findByUserIdAndRegisteredClientId(Long userId, String registeredClientId);
}
