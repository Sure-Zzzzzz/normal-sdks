package io.github.surezzzzzz.sdk.auth.iam.server.controller.rest;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response.PermissionResponse;
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
 * 开放 API 权限清单族（{@code /iam/api/permissions}，1.3.7）。
 *
 * <p>只读目录：外部系统权限对账/下拉。纯码控无 DATA 面（配置目录无行级范围语义）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/api/permissions")
@RequiredArgsConstructor
public class IamPermissionOpenApiController {

    private final IamRoleService roleService;

    /**
     * 分页列权限（keyword 过滤编码/名称；页码 1 起与管理台同构）。
     */
    @GetMapping
    @RequireApiPermission(SimpleIamServerConstant.BUILT_IN_PERMISSION_PERMISSION_API)
    public ResponseEntity<java.util.List<PermissionResponse>> listPermissions(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_VALUE) int page,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_SIZE_VALUE) int size) {
        // yaml 契约声明数组（集合资源），Page 只作内部分页载体不外泄包装
        Page<PermissionResponse> permissions = roleService
                .listPermissions(keyword, Math.max(page, 1), Math.min(Math.max(size, 1), SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE))
                .map(this::toResponse);
        java.util.List<PermissionResponse> content = permissions.getContent();
        log.debug("开放 API 权限清单查询：命中 {} 条", permissions.getTotalElements());
        return ResponseEntity.ok(content);
    }

    private PermissionResponse toResponse(io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamPermissionEntity entity) {
        return new PermissionResponse(entity.getId(), entity.getCode(), entity.getName(),
                entity.getDescription(), entity.getType(), entity.getBuiltIn(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
