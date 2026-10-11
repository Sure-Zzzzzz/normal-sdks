package io.github.surezzzzzz.sdk.auth.iam.server.controller.rest;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response.IamOpenApiRoleResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开放 API 角色目录族（{@code /iam/api/role-catalog}，1.3.7）。
 *
 * <p>只读目录（内置+自定义全量）：外部对账/下拉。{@code /iam/api/roles} 已被受委托
 * 角色族（历史在册契约）占用，普通角色以 catalog 命名区隔目录语义；写管理保留在管理台。
 * 纯码控无 DATA 面。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/api/role-catalog")
@RequiredArgsConstructor
public class IamRoleCatalogOpenApiController {

    private final IamRoleService roleService;

    /**
     * 分页列角色（keyword 过滤编码/名称；页码 1 起）。
     */
    @GetMapping
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_API)
    public ResponseEntity<java.util.List<IamOpenApiRoleResponse>> listRoles(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_VALUE) int page,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_SIZE_VALUE) int size) {
        Page<IamRoleEntity> roles = roleService.listRoles(keyword, Math.max(page, 1), Math.min(Math.max(size, 1), SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE));
        Page<IamOpenApiRoleResponse> response = roles.map(role -> new IamOpenApiRoleResponse(
                role.getId(), role.getCode(), role.getName(), role.getDescription(),
                role.getBuiltIn(), role.getCreatedAt(), role.getUpdatedAt()));
        log.debug("开放 API 角色目录查询：命中 {} 条", response.getTotalElements());
        return ResponseEntity.ok(response.getContent());
    }
}
