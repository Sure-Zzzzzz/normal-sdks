package io.github.surezzzzzz.sdk.auth.iam.server.controller.admin;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.request.CreateRoleRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.request.UpdateRoleRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response.PermissionResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response.RoleResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.department.response.DepartmentResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.response.AdminPageResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.response.AdminUserResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.service.DepartmentService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理台角色域 API：角色 CRUD、分页、成员分页与角色-权限关系
 *
 * <p>进门门为 SecurityFilterChain 管理链（Order 4），端点级由 {@code @PreAuthorize} 强制。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/admin")
@RequiredArgsConstructor
public class IamRoleAdminController {

    private final RoleService roleService;
    private final DepartmentService departmentService;

    /**
     * 角色列表
     */
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_API + "')")
    public ResponseEntity<List<RoleResponse>> listRoles() {
        return ResponseEntity.ok(roleService.getAllRoles().stream()
                .map(RoleResponse::from)
                .collect(Collectors.toList()));
    }

    /**
     * 角色分页
     */
    @GetMapping("/roles/page")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_API + "')")
    public ResponseEntity<AdminPageResponse<RoleResponse>> listRolePage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_VALUE) int page,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_SIZE_VALUE) int size) {
        return ResponseEntity.ok(AdminPageResponse.from(roleService.listRoles(keyword, page, size)
                .map(RoleResponse::from)));
    }

    /**
     * 创建角色
     */
    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_API + "')")
    public ResponseEntity<RoleResponse> createRole(@RequestBody CreateRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RoleResponse.from(roleService.createRole(request)));
    }

    /**
     * 角色详情
     */
    @GetMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_API + "')")
    public ResponseEntity<RoleResponse> getRole(@PathVariable Long roleId) {
        return ResponseEntity.ok(RoleResponse.from(roleService.getById(roleId)));
    }

    /**
     * 更新角色
     */
    @PutMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_API + "')")
    public ResponseEntity<RoleResponse> updateRole(@PathVariable Long roleId,
                                                   @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(RoleResponse.from(roleService.updateRole(roleId, request)));
    }

    /**
     * 删除角色（级联清理角色-权限关系、成员绑定与授权规则）
     */
    @DeleteMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_API + "')")
    public ResponseEntity<Void> deleteRole(@PathVariable Long roleId) {
        roleService.deleteRole(roleId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 角色成员分页
     */
    @GetMapping("/roles/{roleId}/users/page")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_API + "')")
    public ResponseEntity<AdminPageResponse<AdminUserResponse>> listRoleMemberPage(
            @PathVariable Long roleId,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_VALUE) int page,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_SIZE_VALUE) int size) {
        return ResponseEntity.ok(AdminPageResponse.from(roleService.listRoleMembers(roleId, page, size)
                .map(user -> AdminUserResponse.from(user, departmentService.getDepartmentName(user.getDepartmentId())))));
    }

    /**
     * 反查挂载了该角色的部门（该角色的成员继承来源，只读展示）
     */
    @GetMapping("/roles/{roleId}/departments")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_API + "')")
    public ResponseEntity<List<DepartmentResponse>> listRoleDepartments(@PathVariable Long roleId) {
        return ResponseEntity.ok(roleService.getRoleDepartments(roleId).stream()
                .map(DepartmentResponse::from)
                .collect(Collectors.toList()));
    }

    /**
     * 角色已分配权限列表
     */
    @GetMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_API + "')")
    public ResponseEntity<List<PermissionResponse>> getRolePermissions(@PathVariable Long roleId) {
        return ResponseEntity.ok(roleService.getRolePermissions(roleId).stream()
                .map(PermissionResponse::from)
                .collect(Collectors.toList()));
    }

    /**
     * 给角色分配权限
     */
    @PostMapping("/roles/{roleId}/permissions/{permissionId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_API + "')")
    public ResponseEntity<Void> assignPermission(@PathVariable Long roleId,
                                                 @PathVariable Long permissionId) {
        roleService.assignPermission(roleId, permissionId);
        return ResponseEntity.ok().build();
    }

    /**
     * 撤销角色权限
     */
    @DeleteMapping("/roles/{roleId}/permissions/{permissionId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_API + "')")
    public ResponseEntity<Void> revokePermission(@PathVariable Long roleId,
                                                 @PathVariable Long permissionId) {
        roleService.revokePermission(roleId, permissionId);
        return ResponseEntity.noContent().build();
    }
}
