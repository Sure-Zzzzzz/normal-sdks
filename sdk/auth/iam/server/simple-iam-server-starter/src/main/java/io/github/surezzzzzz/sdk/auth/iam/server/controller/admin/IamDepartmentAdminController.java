package io.github.surezzzzzz.sdk.auth.iam.server.controller.admin;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response.RoleResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.department.request.CreateDepartmentRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.department.request.UpdateDepartmentRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.department.response.DepartmentResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.response.AdminPageResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamDepartmentEntity;
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
 * 管理台部门域 API：部门 CRUD 与分页
 *
 * <p>进门门为 SecurityFilterChain 管理链（Order 4），端点级由 {@code @PreAuthorize} 强制。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/admin")
@RequiredArgsConstructor
public class IamDepartmentAdminController {

    private final DepartmentService departmentService;
    private final RoleService roleService;

    /**
     * 部门列表
     */
    @GetMapping("/departments")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_API + "')")
    public ResponseEntity<List<DepartmentResponse>> listDepartments() {
        return ResponseEntity.ok(departmentService.getAllDepartments().stream()
                .map(this::toDepartmentResponse)
                .collect(Collectors.toList()));
    }

    /**
     * 部门分页
     */
    @GetMapping("/departments/page")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_API + "')")
    public ResponseEntity<AdminPageResponse<DepartmentResponse>> listDepartmentPage(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_VALUE) int page,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_SIZE_VALUE) int size) {
        return ResponseEntity.ok(AdminPageResponse.from(
                departmentService.listDepartments(status, keyword, page, size).map(this::toDepartmentResponse)));
    }

    /**
     * 创建部门
     */
    @PostMapping("/departments")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_API + "')")
    public ResponseEntity<DepartmentResponse> createDepartment(@RequestBody CreateDepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toDepartmentResponse(departmentService.createDepartment(request)));
    }

    /**
     * 部门详情
     */
    @GetMapping("/departments/{departmentId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_API + "')")
    public ResponseEntity<DepartmentResponse> getDepartment(@PathVariable Long departmentId) {
        return ResponseEntity.ok(toDepartmentResponse(departmentService.getById(departmentId)));
    }

    /**
     * 更新部门
     */
    @PutMapping("/departments/{departmentId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_API + "')")
    public ResponseEntity<DepartmentResponse> updateDepartment(@PathVariable Long departmentId,
                                                               @RequestBody UpdateDepartmentRequest request) {
        return ResponseEntity.ok(toDepartmentResponse(departmentService.updateDepartment(departmentId, request)));
    }

    /**
     * 删除部门
     */
    @DeleteMapping("/departments/{departmentId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_API + "')")
    public ResponseEntity<Void> deleteDepartment(@PathVariable Long departmentId) {
        departmentService.deleteDepartment(departmentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 部门当前挂载的角色列表：部门下全体成员自动继承这些角色
     */
    @GetMapping("/departments/{departmentId}/roles")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_API + "')")
    public ResponseEntity<List<RoleResponse>> listDepartmentRoles(@PathVariable Long departmentId) {
        return ResponseEntity.ok(roleService.getDepartmentRoles(departmentId).stream()
                .map(RoleResponse::from)
                .collect(Collectors.toList()));
    }

    /**
     * 给部门挂载角色
     */
    @PostMapping("/departments/{departmentId}/roles/{roleId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_API + "')")
    public ResponseEntity<Void> assignDepartmentRole(@PathVariable Long departmentId,
                                                     @PathVariable Long roleId) {
        roleService.assignDepartmentRole(departmentId, roleId);
        return ResponseEntity.ok().build();
    }

    /**
     * 撤销部门挂载的角色（最后管理员保护命中时返回明确错误信息）
     */
    @DeleteMapping("/departments/{departmentId}/roles/{roleId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_API + "')")
    public ResponseEntity<Void> revokeDepartmentRole(@PathVariable Long departmentId,
                                                     @PathVariable Long roleId) {
        roleService.revokeDepartmentRole(departmentId, roleId);
        return ResponseEntity.noContent().build();
    }

    private DepartmentResponse toDepartmentResponse(IamDepartmentEntity department) {
        return DepartmentResponse.from(department, departmentService.getDepartmentName(department.getParentId()));
    }
}
