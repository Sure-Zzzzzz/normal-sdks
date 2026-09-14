package io.github.surezzzzzz.sdk.auth.iam.server.repository.portal;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.portal.IamTrustedApplicationPortalEntity;
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

    /**
     * 读取全部 Portal 集成的全局根节点顺序，停用应用也必须保留位置。
     */
    List<IamTrustedApplicationPortalEntity> findAllByOrderBySortOrderAscApplicationIdAsc();

    /**
     * 侧边栏只读取启用集成，但必须保留平台管理员维护的相对顺序。
     */
    List<IamTrustedApplicationPortalEntity> findByEnabledOrderBySortOrderAscApplicationIdAsc(Integer enabled);
}
