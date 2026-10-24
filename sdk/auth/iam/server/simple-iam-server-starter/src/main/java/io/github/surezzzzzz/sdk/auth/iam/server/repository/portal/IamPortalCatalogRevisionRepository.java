package io.github.surezzzzzz.sdk.auth.iam.server.repository.portal;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.portal.IamPortalCatalogRevisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;

/**
 * Portal 可信应用路由投影 revision Repository。
 *
 * @author surezzzzzz
 */
@Repository
public interface IamPortalCatalogRevisionRepository extends JpaRepository<IamPortalCatalogRevisionEntity, Integer> {

    /**
     * 读取并锁定单例，保证多实例 bump 串行。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT revision FROM IamPortalCatalogRevisionEntity revision WHERE revision.id = :id")
    IamPortalCatalogRevisionEntity findByIdForUpdate(@Param("id") Integer id);
}
