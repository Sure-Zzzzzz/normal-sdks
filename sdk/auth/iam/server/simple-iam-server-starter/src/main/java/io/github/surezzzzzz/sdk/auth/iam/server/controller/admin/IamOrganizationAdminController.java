package io.github.surezzzzzz.sdk.auth.iam.server.controller.admin;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.organization.response.OrganizationDepartmentWorkspaceResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.organization.response.OrganizationTreeNodeResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.organization.response.OrganizationUserProfileResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理台组织聚合视图 API：组织树、部门工作台、用户组织画像
 *
 * <p>进门门为 SecurityFilterChain 管理链（Order 4），端点级由 {@code @PreAuthorize} 强制。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/admin")
@RequiredArgsConstructor
public class IamOrganizationAdminController {

    private final OrganizationService organizationService;

    /**
     * 组织树（部门 + 协作组）
     */
    @GetMapping("/organizations/tree")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_API + "')")
    public ResponseEntity<List<OrganizationTreeNodeResponse>> getOrganizationTree() {
        return ResponseEntity.ok(organizationService.getTree());
    }

    /**
     * 部门工作台视图（子部门与成员）
     */
    @GetMapping("/organizations/departments/{departmentId}/workspace")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_API + "')")
    public ResponseEntity<OrganizationDepartmentWorkspaceResponse> getOrganizationDepartmentWorkspace(
            @PathVariable Long departmentId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_VALUE) int page,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_SIZE_VALUE) int size) {
        return ResponseEntity.ok(organizationService.getDepartmentWorkspace(
                departmentId, status, keyword, page, size));
    }

    /**
     * 用户组织画像（部门、协作组、角色）
     */
    @GetMapping("/organizations/users/{userId}/profile")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_API + "')")
    public ResponseEntity<OrganizationUserProfileResponse> getOrganizationUserProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(organizationService.getUserProfile(userId));
    }
}
