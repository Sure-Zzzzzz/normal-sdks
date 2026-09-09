package io.github.surezzzzzz.sdk.auth.iam.server.controller.admin;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response.RoleResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.response.AdminPageResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.BindExternalIdentityRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.ResetPasswordRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.UpdateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.response.AdminUserResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理台用户域 API：用户 CRUD、启禁用、密码重置、外部身份预绑定与角色分配
 *
 * <p>进门门为 SecurityFilterChain 管理链（Order 4），端点级由 {@code @PreAuthorize} 强制。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/admin")
@RequiredArgsConstructor
public class IamUserAdminController {

    private final UserService userService;
    private final RoleService roleService;
    private final DepartmentService departmentService;
    private final ExternalIdentityBindingService externalIdentityBindingService;

    /**
     * 用户分页查询
     */
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<AdminPageResponse<AdminUserResponse>> listUsers(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) java.time.Instant lastLoginAfter,
            @RequestParam(required = false) java.time.Instant lockedUntilAfter,
            @RequestParam(defaultValue = "false") boolean noDepartment,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_VALUE) int page,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_SIZE_VALUE) int size) {
        return ResponseEntity.ok(AdminPageResponse.from(
                userService.listUsers(status, departmentId, keyword, lastLoginAfter, lockedUntilAfter, noDepartment, page, size)
                        .map(user -> AdminUserResponse.from(user, departmentService.getDepartmentName(user.getDepartmentId())))));
    }

    /**
     * 创建用户
     */
    @PostMapping("/users")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<AdminUserResponse> createUser(@RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toAdminUserResponse(userService.createUser(request)));
    }

    /**
     * 用户详情
     */
    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<AdminUserResponse> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(toAdminUserResponse(userService.getById(userId)));
    }

    /**
     * 更新用户
     */
    @PutMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<AdminUserResponse> updateUser(@PathVariable Long userId,
                                                        @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(toAdminUserResponse(userService.updateUser(userId, request)));
    }

    /**
     * 删除用户（级联清理角色绑定、组成员与应用授权投影）
     */
    @DeleteMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 启用用户
     */
    @PutMapping("/users/{userId}/enable")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<Void> enableUser(@PathVariable Long userId) {
        userService.enableUser(userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 禁用用户（全端吊销会话）
     */
    @PutMapping("/users/{userId}/disable")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<Void> disableUser(@PathVariable Long userId) {
        userService.disableUser(userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 手动解锁用户（清除登录失败锁定与失败计数，不等自动过期）
     */
    @PutMapping("/users/{userId}/unlock")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<Void> unlockUser(@PathVariable Long userId) {
        userService.unlockUser(userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 管理员重置密码（即全端吊销）
     */
    @PutMapping("/users/{userId}/reset-password")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<Void> resetPassword(@PathVariable Long userId,
                                              @RequestBody ResetPasswordRequest request,
                                              @AuthenticationPrincipal UserDetails userDetails) {
        IamUserDetailsSupport iamUserDetails = requireIamUserDetails(userDetails);
        userService.resetPassword(userId, request.getNewPassword(), iamUserDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    /**
     * 预绑定外部身份
     */
    @PostMapping("/users/{userId}/external-identity")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<AdminUserResponse> bindExternalIdentity(@PathVariable Long userId,
                                                                  @RequestBody BindExternalIdentityRequest request) {
        return ResponseEntity.ok(toAdminUserResponse(externalIdentityBindingService.bind(
                userId, request.getProviderCode(), request.getExternalId())));
    }

    /**
     * 解绑外部身份
     */
    @DeleteMapping("/users/{userId}/external-identity")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<Void> unbindExternalIdentity(@PathVariable Long userId) {
        externalIdentityBindingService.unbind(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 用户已分配角色列表
     */
    @GetMapping("/users/{userId}/roles")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<List<RoleResponse>> getUserRoles(@PathVariable Long userId) {
        return ResponseEntity.ok(roleService.getUserRoles(userId).stream()
                .map(RoleResponse::from)
                .collect(Collectors.toList()));
    }

    /**
     * 给用户分配角色
     */
    @PostMapping("/users/{userId}/roles/{roleId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<Void> assignRole(@PathVariable Long userId,
                                           @PathVariable Long roleId) {
        roleService.assignRole(userId, roleId);
        return ResponseEntity.ok().build();
    }

    /**
     * 撤销用户角色
     */
    @DeleteMapping("/users/{userId}/roles/{roleId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<Void> revokeRole(@PathVariable Long userId,
                                           @PathVariable Long roleId) {
        roleService.revokeRole(userId, roleId);
        return ResponseEntity.noContent().build();
    }

    private IamUserDetailsSupport requireIamUserDetails(UserDetails userDetails) {
        if (userDetails instanceof IamUserDetailsSupport) {
            return (IamUserDetailsSupport) userDetails;
        }
        throw new AccessDeniedException(ServerErrorMessage.PERMISSION_DENIED);
    }

    private AdminUserResponse toAdminUserResponse(IamUserEntity user) {
        return AdminUserResponse.from(user, departmentService.getDepartmentName(user.getDepartmentId()));
    }
}
