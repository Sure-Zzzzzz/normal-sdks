package io.github.surezzzzzz.sdk.auth.iam.server.repository;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamTrustedApplicationMenuEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * IAM 可信应用 Portal 菜单项 Repository
 *
 * @author surezzzzzz
 */
@Repository
public interface IamTrustedApplicationMenuRepository
        extends JpaRepository<IamTrustedApplicationMenuEntity, Long> {

    /**
     * 按应用 ID 查询菜单项，按排序值升序
     */
    List<IamTrustedApplicationMenuEntity> findByApplicationIdOrderBySortOrderAsc(Long applicationId);

    /**
     * 批量按应用 ID 查询菜单项
     */
    List<IamTrustedApplicationMenuEntity> findByApplicationIdIn(List<Long> applicationIds);

    /**
     * 按应用 ID 删除全部菜单项
     */
    @Modifying
    @Query("DELETE FROM IamTrustedApplicationMenuEntity m WHERE m.applicationId = :applicationId")
    void deleteByApplicationId(@Param("applicationId") Long applicationId);
}
