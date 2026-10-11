package io.github.surezzzzzz.sdk.auth.iam.server.controller.rest;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.usergroup.request.CreateUserGroupRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.usergroup.request.UpdateUserGroupRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.usergroup.response.IamOpenApiGroupUserResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.usergroup.response.UserGroupResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.usergroup.IamUserGroupEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.service.user.IamUserService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.usergroup.IamUserGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

/**
 * 开放 API 协作组族（{@code /iam/api/user-groups}，1.3.7）。
 *
 * <p>组织同步系统维护协作组：CRUD 与成员增删全管理面（与管理台同构，service 复用零新逻辑）。
 * 纯码控无 DATA 面（对齐管理台同族口径）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/api/user-groups")
@RequiredArgsConstructor
public class IamUserGroupOpenApiController {

    private final IamUserGroupService userGroupService;
    private final IamUserService userService;

    /**
     * 分页列组（status/keyword 过滤）。
     */
    @GetMapping
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_API)
    public ResponseEntity<java.util.List<UserGroupResponse>> listGroups(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_VALUE) int page,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_SIZE_VALUE) int size) {
        Page<UserGroupResponse> groups = userGroupService
                .listGroups(status, keyword, Math.max(page, 1), Math.min(Math.max(size, 1), SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE))
                .map(this::toResponse);
        // yaml 契约声明数组（集合资源），Page 只作内部分页载体不外泄包装
        return ResponseEntity.ok(groups.getContent());
    }

    /**
     * 建组（201）。
     */
    @PostMapping
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_API)
    public ResponseEntity<UserGroupResponse> createGroup(@RequestBody CreateUserGroupRequest request) {
        IamUserGroupEntity group = userGroupService.createGroup(request);
        log.info("开放 API 建组：code={}, operator=aksk", group.getCode());
        return ResponseEntity.status(201).body(toResponse(group));
    }

    /**
     * 单组详情。
     */
    @GetMapping("/{groupId}")
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_API)
    public ResponseEntity<UserGroupResponse> getGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(toResponse(requireGroup(groupId)));
    }

    /**
     * 改组（整体替换 name/description/status）。
     */
    @PutMapping("/{groupId}")
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_API)
    public ResponseEntity<UserGroupResponse> updateGroup(@PathVariable Long groupId,
                                                         @RequestBody UpdateUserGroupRequest request) {
        return ResponseEntity.ok(toResponse(userGroupService.updateGroup(groupId, request)));
    }

    /**
     * 删组（204）。
     */
    @DeleteMapping("/{groupId}")
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_API)
    public ResponseEntity<Void> deleteGroup(@PathVariable Long groupId) {
        userGroupService.deleteGroup(groupId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 组成员分页。
     */
    @GetMapping("/{groupId}/users")
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_API)
    public ResponseEntity<java.util.List<IamOpenApiGroupUserResponse>> listGroupUsers(
            @PathVariable Long groupId,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_VALUE) int page,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_SIZE_VALUE) int size) {
        // service 全量形态（管理台同构）；openapi 侧子集分页（成员规模有限）
        java.util.List<IamUserEntity> users = userGroupService.getGroupUsers(requireGroup(groupId).getId());
        int fromIndex = Math.min((Math.max(page, 1) - 1) * Math.max(size, 1), users.size());
        int toIndex = Math.min(fromIndex + Math.max(size, 1), users.size());
        // yaml 契约声明数组（集合资源）；service 全量形态（管理台同构），openapi 侧子集切片
        return ResponseEntity.ok(users.subList(fromIndex, toIndex).stream()
                .map(user -> new IamOpenApiGroupUserResponse(user.getSubjectId(),
                        user.getUsername(), user.getDisplayName(), user.getStatus()))
                .collect(Collectors.toList()));
    }

    /**
     * 加成员（204）。
     */
    @PostMapping("/{groupId}/users/{subjectId}")
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_API)
    public ResponseEntity<Void> assignUser(@PathVariable Long groupId, @PathVariable String subjectId) {
        IamUserEntity user = userService.getBySubjectId(subjectId);
        userGroupService.assignUser(groupId, user.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * 移成员（204）。
     */
    @DeleteMapping("/{groupId}/users/{subjectId}")
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_API)
    public ResponseEntity<Void> revokeUser(@PathVariable Long groupId, @PathVariable String subjectId) {
        IamUserEntity user = userService.getBySubjectId(subjectId);
        userGroupService.revokeUser(groupId, user.getId());
        return ResponseEntity.noContent().build();
    }

    private IamUserGroupEntity requireGroup(Long groupId) {
        return userGroupService.getAllGroups().stream()
                .filter(group -> groupId.equals(group.getId())).findFirst()
                .orElseThrow(() -> new io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException(
                        io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode.USER_GROUP_NOT_FOUND,
                        String.format(ServerErrorMessage.USER_GROUP_NOT_FOUND, groupId)));
    }

    private UserGroupResponse toResponse(IamUserGroupEntity group) {
        return new UserGroupResponse(group.getId(), group.getCode(), group.getName(),
                group.getDescription(), group.getStatus(), group.getCreatedAt(), group.getUpdatedAt());
    }
}
