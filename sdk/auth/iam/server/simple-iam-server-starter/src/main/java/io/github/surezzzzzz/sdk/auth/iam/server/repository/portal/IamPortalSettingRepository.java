package io.github.surezzzzzz.sdk.auth.iam.server.repository.portal;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.portal.IamPortalSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Portal 全局设置单例 Repository。
 */
@Repository
public interface IamPortalSettingRepository extends JpaRepository<IamPortalSettingEntity, Integer> {
}
