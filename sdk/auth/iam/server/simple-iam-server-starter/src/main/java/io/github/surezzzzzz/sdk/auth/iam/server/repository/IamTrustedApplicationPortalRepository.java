package io.github.surezzzzzz.sdk.auth.iam.server.repository;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamTrustedApplicationPortalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * IAM 可信应用 Portal 集成配置 Repository
 *
 * @author surezzzzzz
 */
@Repository
public interface IamTrustedApplicationPortalRepository
        extends JpaRepository<IamTrustedApplicationPortalEntity, Long> {

    /**
     * 查询所有指定启用状态的 Portal 配置（侧边栏动态菜单读模型）
     */
    List<IamTrustedApplicationPortalEntity> findByEnabled(Integer enabled);
}
