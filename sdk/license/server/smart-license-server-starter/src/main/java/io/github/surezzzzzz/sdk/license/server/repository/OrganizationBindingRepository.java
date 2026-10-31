package io.github.surezzzzzz.sdk.license.server.repository;

import io.github.surezzzzzz.sdk.license.server.entity.OrganizationBindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** license_organization_binding 仓储（客户组织绑定：IAM 部门准入事实源）。 */
public interface OrganizationBindingRepository extends JpaRepository<OrganizationBindingEntity, String> {
}
