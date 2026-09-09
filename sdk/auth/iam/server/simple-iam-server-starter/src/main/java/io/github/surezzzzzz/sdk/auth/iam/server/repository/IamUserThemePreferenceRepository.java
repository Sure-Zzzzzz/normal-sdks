package io.github.surezzzzzz.sdk.auth.iam.server.repository;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserThemePreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * IAM 用户 Portal 主题偏好 Repository。
 *
 * @author surezzzzzz
 */
@Repository
public interface IamUserThemePreferenceRepository extends JpaRepository<IamUserThemePreferenceEntity, Long> {
}
