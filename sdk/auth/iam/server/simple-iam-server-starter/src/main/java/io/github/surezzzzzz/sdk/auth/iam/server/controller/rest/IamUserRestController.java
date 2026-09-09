package io.github.surezzzzzz.sdk.auth.iam.server.controller.rest;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.support.DataAccessPlanRestrictionVerifier;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.ResetPasswordRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.UpdateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.response.UserRestResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.service.DepartmentService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.RoleService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.UserDataAccessPlanConverter;
import io.github.surezzzzzz.sdk.auth.iam.server.service.UserService;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 开放 API 用户族（{@code /iam/api/users}）。
 *
 * <p>主体为 AKSK 凭证（外部业务系统组织与人员同步），由公共资源层链鉴权；
 * 端点级 @RequireApiPermission 精确码控（iam:user:api）+ DATA 范围消费
 * （resource=iam:user：读=部门范围与请求条件求交、越权数据不出库，写=目标
 * 部门须完整落在授权范围内，计划不可执行一律失败关闭 403）。响应为开放 API 独立
 * 字段集，不带管理台视图字段；操作事实经 AdminActionEvent 审计，operator
 * 为 AKSK 主体标识（sourceId:subjectId）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/api/users")
@RequiredArgsConstructor
public class IamUserRestController {

    private final UserService userService;
    private final RoleService roleService;
    private final DepartmentService departmentService;

    /**
     * 分页查询用户（status / departmentId / keyword 过滤，DATA 部门范围求交）。
     */
    @GetMapping
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_IAM_USER,
            action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ)
    public ResponseEntity<Page<UserRestResponse>> listUsers(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_VALUE) int page,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_SIZE_VALUE) int size,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        Set<Long> departmentScope = UserDataAccessPlanConverter.toDepartmentScope(plan);
        Page<UserRestResponse> response = userService
                .listUsers(status, departmentId, keyword, departmentScope, page, size)
                .map(this::toUserRestResponse);
        log.debug("开放 API 用户列表查询：operator={}, departmentScope={}, 命中 {} 条",
                resolveOperator(), departmentScope, response.getTotalElements());
        return ResponseEntity.ok(response);
    }

    /**
     * 用户详情（含角色编码列表；目标部门须在 DATA 授权范围内）。
     */
    @GetMapping("/{userId}")
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_IAM_USER,
            action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ)
    public ResponseEntity<UserRestResponse> getUser(@PathVariable Long userId,
                                                    @CurrentDataAccessPlan DataAccessPlan plan) {
        IamUserEntity user = userService.getById(userId);
        requireWithinScope(plan, user);
        return ResponseEntity.ok(toUserRestResponse(user));
    }

    /**
     * 用户角色列表（目标部门须在 DATA 授权范围内）。
     */
    @GetMapping("/{userId}/roles")
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_IAM_USER,
            action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ)
    public ResponseEntity<List<String>> getUserRoles(@PathVariable Long userId,
                                                     @CurrentDataAccessPlan DataAccessPlan plan) {
        IamUserEntity user = userService.getById(userId);
        requireWithinScope(plan, user);
        return ResponseEntity.ok(roleCodes(userId));
    }

    /**
     * 创建用户（目标部门须在 DATA 授权范围内）。
     */
    @PostMapping
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_IAM_USER,
            action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_WRITE)
    public ResponseEntity<UserRestResponse> createUser(@RequestBody CreateUserRequest request,
                                                       @CurrentDataAccessPlan DataAccessPlan plan) {
        requireDepartmentWithinScope(plan, request.getDepartmentId());
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(toUserRestResponse(userService.createUser(request)));
    }

    /**
     * 更新用户（现有部门与变更后部门均须在 DATA 授权范围内）。
     */
    @PutMapping("/{userId}")
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_IAM_USER,
            action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_WRITE)
    public ResponseEntity<UserRestResponse> updateUser(@PathVariable Long userId,
                                                       @RequestBody UpdateUserRequest request,
                                                       @CurrentDataAccessPlan DataAccessPlan plan) {
        IamUserEntity user = userService.getById(userId);
        requireWithinScope(plan, user);
        if (request.getDepartmentId() != null
                && !request.getDepartmentId().equals(user.getDepartmentId())) {
            requireDepartmentWithinScope(plan, request.getDepartmentId());
        }
        return ResponseEntity.ok(toUserRestResponse(userService.updateUser(userId, request)));
    }

    /**
     * 删除用户（物理删除，级联清理角色绑定、组成员与应用授权投影）。
     */
    @DeleteMapping("/{userId}")
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_IAM_USER,
            action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_WRITE)
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId,
                                           @CurrentDataAccessPlan DataAccessPlan plan) {
        requireWithinScope(plan, userService.getById(userId));
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 启用用户。
     */
    @PutMapping("/{userId}/enable")
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_IAM_USER,
            action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_WRITE)
    public ResponseEntity<Void> enableUser(@PathVariable Long userId,
                                           @CurrentDataAccessPlan DataAccessPlan plan) {
        requireWithinScope(plan, userService.getById(userId));
        userService.enableUser(userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 禁用用户（全端吊销会话）。
     */
    @PutMapping("/{userId}/disable")
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_IAM_USER,
            action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_WRITE)
    public ResponseEntity<Void> disableUser(@PathVariable Long userId,
                                            @CurrentDataAccessPlan DataAccessPlan plan) {
        requireWithinScope(plan, userService.getById(userId));
        userService.disableUser(userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 重置密码（即全端吊销）；requestedBy 记AKSK 主体标识。
     */
    @PutMapping("/{userId}/reset-password")
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_IAM_USER,
            action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_WRITE)
    public ResponseEntity<Void> resetPassword(@PathVariable Long userId,
                                              @RequestBody ResetPasswordRequest request,
                                              @CurrentDataAccessPlan DataAccessPlan plan) {
        requireWithinScope(plan, userService.getById(userId));
        userService.resetPassword(userId, request.getNewPassword(), resolveOperator());
        return ResponseEntity.ok().build();
    }

    /**
     * 绑定角色。
     */
    @PostMapping("/{userId}/roles/{roleId}")
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_IAM_USER,
            action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_WRITE)
    public ResponseEntity<Void> assignRole(@PathVariable Long userId, @PathVariable Long roleId,
                                           @CurrentDataAccessPlan DataAccessPlan plan) {
        requireWithinScope(plan, userService.getById(userId));
        roleService.assignRole(userId, roleId);
        return ResponseEntity.ok().build();
    }

    /**
     * 解绑角色。
     */
    @DeleteMapping("/{userId}/roles/{roleId}")
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_IAM_USER,
            action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_WRITE)
    public ResponseEntity<Void> revokeRole(@PathVariable Long userId, @PathVariable Long roleId,
                                           @CurrentDataAccessPlan DataAccessPlan plan) {
        requireWithinScope(plan, userService.getById(userId));
        roleService.revokeRole(userId, roleId);
        return ResponseEntity.noContent().build();
    }

    private UserRestResponse toUserRestResponse(IamUserEntity user) {
        String departmentName = user.getDepartmentId() == null
                ? null : departmentService.getDepartmentName(user.getDepartmentId());
        return UserRestResponse.from(user, departmentName, roleCodes(user.getId()));
    }

    private List<String> roleCodes(Long userId) {
        return roleService.getUserRoles(userId).stream()
                .map(role -> role.getCode())
                .collect(Collectors.toList());
    }

    /**
     * 校验目标用户部门完整落在 DATA 授权范围内（至少满足一个完整授权项）。
     */
    private void requireWithinScope(DataAccessPlan plan, IamUserEntity user) {
        requireDepartmentWithinScope(plan, user.getDepartmentId());
    }

    private void requireDepartmentWithinScope(DataAccessPlan plan, Long departmentId) {
        Map<String, String> dimensions = new HashMap<String, String>();
        dimensions.put(SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_DEPARTMENT_ID,
                departmentId == null ? null : String.valueOf(departmentId));
        DataAccessPlanRestrictionVerifier.requireTargetAllowed(plan, dimensions);
    }

    /**
     * 解析 AKSK 主体标识（sourceId:subjectId，如 aksk:crm-sync）；非 AKSK 主体（理论不达）回退认证名。
     */
    private String resolveOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof VerifiedResourceContext) {
            VerifiedResourcePrincipal principal = ((VerifiedResourceContext) authentication.getPrincipal()).getPrincipal();
            return principal.getSourceId().getValue() + ":" + principal.getSubjectId();
        }
        return authentication == null ? null : authentication.getName();
    }
}
