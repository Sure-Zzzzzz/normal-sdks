package io.github.surezzzzzz.sdk.auth.iam.server.controller.admin;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response.PermissionResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.response.AdminPageResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理台权限域 API：权限码列表 / 分页 / 详情（只读——权限码为系统内置 seed，
 * 由安全链与 {@code @PreAuthorize} 在代码中消费，运行期不提供增删改）
 *
 * <p>进门门为 SecurityFilterChain 管理链（Order 4），端点级由 {@code @PreAuthorize} 强制。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/admin")
@RequiredArgsConstructor
public class IamPermissionAdminController {

    private final RoleService roleService;

    /**
     * 权限列表
     */
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_PERMISSION_API + "')")
    public ResponseEntity<List<PermissionResponse>> listPermissions() {
        return ResponseEntity.ok(roleService.getAllPermissions().stream()
                .map(PermissionResponse::from)
                .collect(Collectors.toList()));
    }

    /**
     * 权限分页
     */
    @GetMapping("/permissions/page")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_PERMISSION_API + "')")
    public ResponseEntity<AdminPageResponse<PermissionResponse>> listPermissionPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_VALUE) int page,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_SIZE_VALUE) int size) {
        return ResponseEntity.ok(AdminPageResponse.from(roleService.listPermissions(keyword, page, size)
                .map(PermissionResponse::from)));
    }

    /**
     * 权限详情
     */
    @GetMapping("/permissions/{permissionId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_PERMISSION_API + "')")
    public ResponseEntity<PermissionResponse> getPermission(@PathVariable Long permissionId) {
        return ResponseEntity.ok(PermissionResponse.from(roleService.getPermissionById(permissionId)));
    }
}
