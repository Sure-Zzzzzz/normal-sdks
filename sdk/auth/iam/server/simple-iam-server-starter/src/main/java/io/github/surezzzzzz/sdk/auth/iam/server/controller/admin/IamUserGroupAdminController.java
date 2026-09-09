package io.github.surezzzzzz.sdk.auth.iam.server.controller.admin;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.response.AdminPageResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.response.AdminUserResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.usergroup.request.CreateUserGroupRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.usergroup.request.UpdateUserGroupRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.usergroup.response.UserGroupResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.service.DepartmentService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.UserGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理台协作组域 API：协作组 CRUD、分页与成员管理
 *
 * <p>进门门为 SecurityFilterChain 管理链（Order 4），端点级由 {@code @PreAuthorize} 强制。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/admin")
@RequiredArgsConstructor
public class IamUserGroupAdminController {

    private final UserGroupService userGroupService;
    private final DepartmentService departmentService;

    /**
     * 协作组列表
     */
    @GetMapping("/user-groups")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_API + "')")
    public ResponseEntity<List<UserGroupResponse>> listUserGroups() {
        return ResponseEntity.ok(userGroupService.getAllGroups().stream()
                .map(UserGroupResponse::from)
                .collect(Collectors.toList()));
    }

    /**
     * 协作组分页
     */
    @GetMapping("/user-groups/page")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_API + "')")
    public ResponseEntity<AdminPageResponse<UserGroupResponse>> listUserGroupPage(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_VALUE) int page,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_SIZE_VALUE) int size) {
        return ResponseEntity.ok(AdminPageResponse.from(
                userGroupService.listGroups(status, keyword, page, size).map(UserGroupResponse::from)));
    }

    /**
     * 创建协作组
     */
    @PostMapping("/user-groups")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_API + "')")
    public ResponseEntity<UserGroupResponse> createUserGroup(@RequestBody CreateUserGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserGroupResponse.from(userGroupService.createGroup(request)));
    }

    /**
     * 协作组详情
     */
    @GetMapping("/user-groups/{groupId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_API + "')")
    public ResponseEntity<UserGroupResponse> getUserGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(UserGroupResponse.from(userGroupService.getById(groupId)));
    }

    /**
     * 更新协作组
     */
    @PutMapping("/user-groups/{groupId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_API + "')")
    public ResponseEntity<UserGroupResponse> updateUserGroup(@PathVariable Long groupId,
                                                             @RequestBody UpdateUserGroupRequest request) {
        return ResponseEntity.ok(UserGroupResponse.from(userGroupService.updateGroup(groupId, request)));
    }

    /**
     * 删除协作组
     */
    @DeleteMapping("/user-groups/{groupId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_API + "')")
    public ResponseEntity<Void> deleteUserGroup(@PathVariable Long groupId) {
        userGroupService.deleteGroup(groupId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 协作组成员列表
     */
    @GetMapping("/user-groups/{groupId}/users")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_API + "')")
    public ResponseEntity<List<AdminUserResponse>> getUserGroupUsers(@PathVariable Long groupId) {
        return ResponseEntity.ok(userGroupService.getGroupUsers(groupId).stream()
                .map(user -> AdminUserResponse.from(user, departmentService.getDepartmentName(user.getDepartmentId())))
                .collect(Collectors.toList()));
    }

    /**
     * 添加协作组成员
     */
    @PostMapping("/user-groups/{groupId}/users/{userId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_API + "')")
    public ResponseEntity<Void> assignUserGroupUser(@PathVariable Long groupId,
                                                    @PathVariable Long userId) {
        userGroupService.assignUser(groupId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 移除协作组成员
     */
    @DeleteMapping("/user-groups/{groupId}/users/{userId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_API + "')")
    public ResponseEntity<Void> revokeUserGroupUser(@PathVariable Long groupId,
                                                    @PathVariable Long userId) {
        userGroupService.revokeUser(groupId, userId);
        return ResponseEntity.noContent().build();
    }
}
