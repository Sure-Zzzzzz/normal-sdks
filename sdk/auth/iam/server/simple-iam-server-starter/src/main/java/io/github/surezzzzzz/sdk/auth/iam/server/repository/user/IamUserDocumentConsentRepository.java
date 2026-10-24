package io.github.surezzzzzz.sdk.auth.iam.server.repository.user;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserDocumentConsentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户登录要求同意记录仓储。
 *
 * @author surezzzzzz
 */
public interface IamUserDocumentConsentRepository extends JpaRepository<IamUserDocumentConsentEntity, Long> {

    Optional<IamUserDocumentConsentEntity> findByUserIdAndDocumentVersionId(Long userId, Long documentVersionId);
}
