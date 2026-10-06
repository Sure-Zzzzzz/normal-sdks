package io.github.surezzzzzz.sdk.auth.iam.server.controller.rest;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.response.ApplicationPermissionManifestResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.request.CreateOpenRoleRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.request.PutOpenRoleRuleRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.response.*;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.IamOpenRoleException;
import io.github.surezzzzzz.sdk.auth.iam.server.model.IamOpenRoleCreationResult;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamOpenDirectoryService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamOpenRoleService;
import io.github.surezzzzzz.sdk.auth.iam.server.support.IamOpenRoleDataAccessPlanHelper;
import io.github.surezzzzzz.sdk.auth.iam.server.support.IamOpenRoleProtocolHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;

/**
 * 机器受委托角色入口；每个操作独立消费 API 和完整 DATA 授权。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/api")
@RequiredArgsConstructor
public class IamOpenRoleRestController {
    private final IamOpenRoleService roleService;
    private final IamOpenDirectoryService directoryService;

    /**
     * 创建自己的角色；同幂等键回读不重置规则或版本。
     */
    @PostMapping(value = "/roles", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequireApiPermission(SimpleIamServerConstant.OPEN_ROLE_CREATE_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_OPEN_ROLE, action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_CREATE)
    public ResponseEntity<OpenRoleResponse> create(@RequestBody CreateOpenRoleRequest request, @CurrentDataAccessPlan DataAccessPlan plan) {
        IamOpenRoleCreationResult result = roleService.create(request, plan);
        OpenRoleResponse response = result.getRole();
        return ResponseEntity.status(result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK)
                .location(URI.create(String.format(SimpleIamServerConstant.OPEN_ROLE_LOCATION_TEMPLATE, response.getOpenRoleId())))
                .eTag(IamOpenRoleProtocolHelper.etag(response.getOpenRoleId(), response.getRevision())).body(response);
    }

    /**
     * 查询自己的受授权角色分页。
     */
    @GetMapping("/roles")
    @RequireApiPermission(SimpleIamServerConstant.OPEN_ROLE_READ_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_OPEN_ROLE, action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ)
    public ResponseEntity<OpenRolePageResponse<OpenRoleResponse>> list(
            @RequestParam(required = false) Long applicationId, @RequestParam(required = false) Long rootDepartmentId,
            @RequestParam(required = false) String externalId,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_VALUE) int page,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_SIZE_VALUE) int size,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        return ResponseEntity.ok(roleService.list(plan, applicationId, rootDepartmentId, externalId, page, size));
    }

    /**
     * 读取角色事实及同一数据库版本。
     */
    @GetMapping("/roles/{openRoleId}")
    @RequireApiPermission(SimpleIamServerConstant.OPEN_ROLE_READ_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_OPEN_ROLE, action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ)
    public ResponseEntity<OpenRoleResponse> get(@PathVariable String openRoleId, @CurrentDataAccessPlan DataAccessPlan plan) {
        OpenRoleResponse response = roleService.get(openRoleId, plan);
        return ResponseEntity.ok().eTag(IamOpenRoleProtocolHelper.etag(openRoleId, response.getRevision())).body(response);
    }

    /**
     * 读取固定应用规则。
     */
    @GetMapping("/roles/{openRoleId}/authorization-rules/{applicationId}")
    @RequireApiPermission(SimpleIamServerConstant.OPEN_ROLE_RULE_READ_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_OPEN_ROLE_RULE, action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ)
    public ResponseEntity<OpenRoleRuleResponse> getRule(@PathVariable String openRoleId, @PathVariable Long applicationId,
                                                        @CurrentDataAccessPlan DataAccessPlan plan) {
        OpenRoleRuleResponse response = roleService.getRule(openRoleId, applicationId, plan);
        return ResponseEntity.ok().eTag(IamOpenRoleProtocolHelper.etag(openRoleId, response.getRevision())).body(response);
    }

    /**
     * 在角色条件版本和指定清单版本下整体设置规则。
     */
    @PutMapping(value = "/roles/{openRoleId}/authorization-rules/{applicationId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequireApiPermission(SimpleIamServerConstant.OPEN_ROLE_RULE_WRITE_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_OPEN_ROLE_RULE, action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_WRITE)
    public ResponseEntity<OpenRoleRuleResponse> putRule(@PathVariable String openRoleId, @PathVariable Long applicationId,
                                                        @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                        @RequestBody PutOpenRoleRuleRequest request, @CurrentDataAccessPlan DataAccessPlan plan) {
        OpenRoleRuleResponse response = roleService.putRule(openRoleId, applicationId, ifMatch, request, plan);
        return ResponseEntity.ok().eTag(IamOpenRoleProtocolHelper.etag(openRoleId, response.getRevision())).body(response);
    }

    /**
     * 清除规则，无变化不递增版本。
     */
    @DeleteMapping("/roles/{openRoleId}/authorization-rules/{applicationId}")
    @RequireApiPermission(SimpleIamServerConstant.OPEN_ROLE_RULE_WRITE_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_OPEN_ROLE_RULE, action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_WRITE)
    public ResponseEntity<Void> deleteRule(@PathVariable String openRoleId, @PathVariable Long applicationId,
                                           @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch, @CurrentDataAccessPlan DataAccessPlan plan) {
        long revision = roleService.deleteRule(openRoleId, applicationId, ifMatch, plan);
        return ResponseEntity.noContent().eTag(IamOpenRoleProtocolHelper.etag(openRoleId, revision)).build();
    }

    /**
     * 查询本角色受授权部门关系，内容和版本由同一事务提供。
     */
    @GetMapping("/roles/{openRoleId}/departments")
    @RequireApiPermission(SimpleIamServerConstant.OPEN_DEPARTMENT_ROLE_READ_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_OPEN_DEPARTMENT_ROLE, action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ)
    public ResponseEntity<OpenDepartmentRolePageResponse> departments(@PathVariable String openRoleId,
                                                                      @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_VALUE) int page,
                                                                      @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_SIZE_VALUE) int size,
                                                                      @CurrentDataAccessPlan DataAccessPlan plan) {
        OpenDepartmentRolePageResponse response = roleService.listDepartments(openRoleId, plan, page, size);
        // 组织归属及调用方 DATA 可独立变化，角色版本不能作为该响应的条件缓存标记。
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    /**
     * 增加当前根子树内的单条关系；禁止客户端用请求体改变根。
     */
    @PutMapping("/departments/{departmentId}/roles/{openRoleId}")
    @RequireApiPermission(SimpleIamServerConstant.OPEN_DEPARTMENT_ROLE_WRITE_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_OPEN_DEPARTMENT_ROLE, action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_WRITE)
    public ResponseEntity<Void> assignDepartment(@PathVariable Long departmentId, @PathVariable String openRoleId,
                                                 @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                 @CurrentDataAccessPlan DataAccessPlan plan, HttpServletRequest request) throws IOException {
        if (request.getInputStream().read() != -1) throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_INVALID);
        long revision = roleService.changeDepartment(openRoleId, departmentId, ifMatch, plan, true);
        return ResponseEntity.noContent().eTag(IamOpenRoleProtocolHelper.etag(openRoleId, revision)).build();
    }

    /**
     * 清理本角色关系，目标迁出或消失也不阻止收缩。
     */
    @DeleteMapping("/departments/{departmentId}/roles/{openRoleId}")
    @RequireApiPermission(SimpleIamServerConstant.OPEN_DEPARTMENT_ROLE_WRITE_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_OPEN_DEPARTMENT_ROLE, action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_WRITE)
    public ResponseEntity<Void> revokeDepartment(@PathVariable Long departmentId, @PathVariable String openRoleId,
                                                 @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch, @CurrentDataAccessPlan DataAccessPlan plan) {
        long revision = roleService.changeDepartment(openRoleId, departmentId, ifMatch, plan, false);
        return ResponseEntity.noContent().eTag(IamOpenRoleProtocolHelper.etag(openRoleId, revision)).build();
    }

    /**
     * 完整读取批准的组织子树。
     */
    @GetMapping("/organization-directories/{rootDepartmentId}/departments")
    @RequireApiPermission(SimpleIamServerConstant.OPEN_DIRECTORY_READ_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_OPEN_DIRECTORY, action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ)
    public ResponseEntity<OpenOrganizationDirectoryResponse> directory(@PathVariable Long rootDepartmentId,
                                                                       @CurrentDataAccessPlan DataAccessPlan plan) {
        requireDirectory(rootDepartmentId, plan);
        return ResponseEntity.ok(directoryService.readDirectory(rootDepartmentId));
    }

    /**
     * 指定成员只提供组织资格所需的最小事实。
     */
    @GetMapping("/organization-directories/{rootDepartmentId}/members/{subjectId}")
    @RequireApiPermission(SimpleIamServerConstant.OPEN_DIRECTORY_READ_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_OPEN_DIRECTORY, action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ)
    public ResponseEntity<OpenOrganizationMemberResponse> member(@PathVariable Long rootDepartmentId, @PathVariable String subjectId,
                                                                 @CurrentDataAccessPlan DataAccessPlan plan) {
        requireDirectory(rootDepartmentId, plan);
        return ResponseEntity.ok(directoryService.readMember(rootDepartmentId, subjectId));
    }

    /**
     * 读取批准的非内置应用最小事实。
     */
    @GetMapping("/target-applications/{applicationId}")
    @RequireApiPermission(SimpleIamServerConstant.OPEN_APPLICATION_READ_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_OPEN_APPLICATION, action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ)
    public ResponseEntity<OpenTargetApplicationResponse> application(@PathVariable Long applicationId, @CurrentDataAccessPlan DataAccessPlan plan) {
        return ResponseEntity.ok(roleService.getApplication(applicationId, plan));
    }

    /**
     * 读取当前权限清单版本和摘要，写规则时显式携带。
     */
    @GetMapping("/target-applications/{applicationId}/permission-manifest")
    @RequireApiPermission(SimpleIamServerConstant.OPEN_APPLICATION_READ_API)
    @DataPermissionOperation(resource = SimpleIamServerConstant.DATA_RESOURCE_OPEN_APPLICATION, action = SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ)
    public ResponseEntity<ApplicationPermissionManifestResponse> manifest(@PathVariable Long applicationId, @CurrentDataAccessPlan DataAccessPlan plan) {
        return ResponseEntity.ok(roleService.getManifest(applicationId, plan));
    }

    private void requireDirectory(Long root, DataAccessPlan plan) {
        IamOpenRoleProtocolHelper.requireServiceActor();
        IamOpenRoleProtocolHelper.positive(root);
        IamOpenRoleDataAccessPlanHelper.require(plan, Collections.singletonMap(
                SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_ROOT_DEPARTMENT_ID, root.toString()));
    }
}
