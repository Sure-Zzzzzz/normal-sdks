package io.github.surezzzzzz.sdk.auth.iam.server.controller.admin;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.request.PutRoleAuthorizationRuleRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response.RoleAuthorizationRuleResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamRoleAuthorizationRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * IAM 管理域角色授权规则 API。
 *
 * <p>规则定义某角色在某应用下的权限集合（授权投影的计算源）。
 * 权限码必须落在应用权限清单范围内；规则变更触发持有该角色
 * 全部用户的授权投影重算。
 *
 * <p>权限：ROLE_iam_admin + iam:role:api
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/admin/roles/{roleId}/authorization-rules")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_API + "')")
public class IamRoleAuthorizationRuleAdminController {

    private final IamRoleAuthorizationRuleService ruleService;

    /**
     * 查询角色在指定应用下的授权规则。
     *
     * <p>角色对该应用无规则时返回 404（合法状态，前端编辑器按空规则呈现）。
     */
    @GetMapping("/{applicationId}")
    public ResponseEntity<RoleAuthorizationRuleResponse> getRoleAuthorizationRule(
            @PathVariable Long roleId,
            @PathVariable Long applicationId) {
        RoleAuthorizationRuleResponse response =
                ruleService.getRoleAuthorizationRule(roleId, applicationId);
        return response == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(response);
    }

    /**
     * 设置角色在指定应用下的授权规则（固定地址 upsert，幂等重放安全）。
     *
     * <p>固定地址 PUT：创建与替换统一返回 200 + 保存后的规则。
     */
    @PutMapping("/{applicationId}")
    public ResponseEntity<RoleAuthorizationRuleResponse> putRoleAuthorizationRule(
            @PathVariable Long roleId,
            @PathVariable Long applicationId,
            @RequestBody PutRoleAuthorizationRuleRequest request) {
        return ResponseEntity.ok(ruleService.putRoleAuthorizationRule(
                roleId,
                applicationId,
                request.getPagePermissions(),
                request.getApiPermissions(),
                request.getDataGrantTemplate()));
    }

    /**
     * 删除角色在指定应用下的授权规则（无规则时幂等返回 204）。
     *
     * <p>删除后持有该角色的用户投影按剩余规则收缩。
     */
    @DeleteMapping("/{applicationId}")
    public ResponseEntity<Void> deleteRoleAuthorizationRule(
            @PathVariable Long roleId,
            @PathVariable Long applicationId) {
        ruleService.deleteRoleAuthorizationRule(roleId, applicationId);
        return ResponseEntity.noContent().build();
    }
}
