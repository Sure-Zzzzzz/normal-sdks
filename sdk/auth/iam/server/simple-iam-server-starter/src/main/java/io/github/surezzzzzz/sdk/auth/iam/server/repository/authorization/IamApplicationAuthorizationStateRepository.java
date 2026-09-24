package io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamApplicationAuthorizationStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * IAM 可信应用 AKU 授权状态 Repository。
 */
@Repository
public interface IamApplicationAuthorizationStateRepository
        extends JpaRepository<IamApplicationAuthorizationStateEntity, Long> {
}
