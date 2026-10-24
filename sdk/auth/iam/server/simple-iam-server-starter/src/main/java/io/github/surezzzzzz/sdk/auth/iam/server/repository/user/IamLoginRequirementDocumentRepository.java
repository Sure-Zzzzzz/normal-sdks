package io.github.surezzzzzz.sdk.auth.iam.server.repository.user;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamLoginRequirementDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 登录前置文档版本仓储。
 *
 * @author surezzzzzz
 */
public interface IamLoginRequirementDocumentRepository
        extends JpaRepository<IamLoginRequirementDocumentEntity, Long> {

    List<IamLoginRequirementDocumentEntity> findAllByOrderByVersionIdAsc();
}
