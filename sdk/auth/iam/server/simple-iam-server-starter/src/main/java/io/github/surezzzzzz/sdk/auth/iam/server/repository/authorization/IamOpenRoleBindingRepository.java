package io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamOpenRoleBindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/**
 * 委托角色的持久边界与条件写锁。
 *
 * @author surezzzzzz
 */
@Repository
public interface IamOpenRoleBindingRepository extends JpaRepository<IamOpenRoleBindingEntity, String>,
        JpaSpecificationExecutor<IamOpenRoleBindingEntity> {
    /**
     * 按既有普通角色查询委托记录，供所有管理路径共用。
     */
    Optional<IamOpenRoleBindingEntity> findByRoleId(Long roleId);

    /**
     * 批量读取管理列表的委托边界，避免逐角色查询。
     */
    List<IamOpenRoleBindingEntity> findByRoleIdIn(List<Long> roleIds);

    /**
     * 创建幂等键必须包含完整主体三元组。
     */
    Optional<IamOpenRoleBindingEntity> findByOwnerSourceIdAndOwnerSubjectTypeAndOwnerSubjectIdAndExternalId(
            String sourceId, String subjectType, String subjectId, String externalId);

    /**
     * 取得当前数据库行锁，调用方仍须刷新已受管实体。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM IamOpenRoleBindingEntity b WHERE b.openRoleId = :openRoleId")
    Optional<IamOpenRoleBindingEntity> findForUpdate(@Param("openRoleId") String openRoleId);
}
