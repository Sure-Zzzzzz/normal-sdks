package io.github.surezzzzzz.sdk.auth.iam.server.repository;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRoleAuthorizationRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * IAM 角色应用授权规则 Repository。
 *
 * @author surezzzzzz
 */
@Repository
public interface IamRoleAuthorizationRuleRepository
        extends JpaRepository<IamRoleAuthorizationRuleEntity, Long> {

    /**
     * 按角色与应用查询授权规则。
     */
    Optional<IamRoleAuthorizationRuleEntity> findByRoleIdAndApplicationId(
            Long roleId, Long applicationId);

    /**
     * 批量按角色与应用查询授权规则。
     */
    List<IamRoleAuthorizationRuleEntity> findByRoleIdInAndApplicationId(
            List<Long> roleIds, Long applicationId);

    /**
     * 按角色集合查询全部授权规则（跨应用）。
     */
    List<IamRoleAuthorizationRuleEntity> findByRoleIdIn(List<Long> roleIds);

    /**
     * 按应用查询所有角色的授权规则。
     */
    List<IamRoleAuthorizationRuleEntity> findByApplicationId(Long applicationId);

    /**
     * 删除角色的全部授权规则（角色删除时级联，避免孤儿规则）。
     */
    void deleteByRoleId(Long roleId);

    /**
     * 删除应用的全部授权规则（应用删除时级联，避免孤儿规则）。
     */
    void deleteByApplicationId(Long applicationId);
}
