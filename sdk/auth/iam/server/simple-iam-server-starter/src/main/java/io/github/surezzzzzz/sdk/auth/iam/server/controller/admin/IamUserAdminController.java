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
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.response.UserImportResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.web.account.response.WebPhoneStatusResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamRoleService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.department.IamDepartmentService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.user.IamExternalIdentityBindingService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.user.IamUserExcelImportService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.user.IamUserService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.web.auth.IamPhoneBindingService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.web.auth.IamUserDetails;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    private final IamUserService userService;
    private final IamPhoneBindingService phoneBindingService;
    private final IamRoleService roleService;
    private final IamDepartmentService departmentService;
    private final IamExternalIdentityBindingService externalIdentityBindingService;
    private final IamUserExcelImportService userExcelImportService;

    /**
     * 下载用户导入模板。模板即时生成，不在服务端保存。
     */
    @GetMapping("/users/import/template")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<byte[]> downloadUserImportTemplate() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(
                    SimpleIamServerConstant.USER_IMPORT_WORKBOOK_SHEET_NAME);
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(
                    SimpleIamServerConstant.USER_IMPORT_HEADER_ROW_INDEX);
            for (int index = 0; index < SimpleIamServerConstant.USER_IMPORT_HEADERS.size(); index++) {
                header.createCell(index).setCellValue(SimpleIamServerConstant.USER_IMPORT_HEADERS.get(index));
                sheet.setColumnWidth(index, SimpleIamServerConstant.USER_IMPORT_TEMPLATE_COLUMN_WIDTH);
            }
            workbook.write(output);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, String.format(
                            SimpleIamServerConstant.USER_IMPORT_TEMPLATE_CONTENT_DISPOSITION,
                            SimpleIamServerConstant.USER_IMPORT_TEMPLATE_FILE_NAME))
                    .contentType(MediaType.parseMediaType(SimpleIamServerConstant.USER_IMPORT_WORKBOOK_MEDIA_TYPE))
                    .body(output.toByteArray());
        }
    }

    /**
     * 导入本地用户。文件只在请求内存中解析，响应返回逐行结果。
     */
    @PostMapping(value = "/users/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<UserImportResponse> importUsers(
            @RequestPart(SimpleIamServerConstant.USER_IMPORT_FILE_PART_NAME) MultipartFile file) {
        return ResponseEntity.ok(userExcelImportService.importUsers(file));
    }

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
    @GetMapping("/users/{subjectId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<AdminUserResponse> getUser(@PathVariable String subjectId) {
        return ResponseEntity.ok(toAdminUserResponse(userService.getBySubjectId(subjectId)));
    }

    /**
     * 更新用户
     */
    @PutMapping("/users/{subjectId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<AdminUserResponse> updateUser(@PathVariable String subjectId,
                                                        @RequestBody UpdateUserRequest request) {
        IamUserEntity user = userService.getBySubjectId(subjectId);
        return ResponseEntity.ok(toAdminUserResponse(userService.updateUser(user.getId(), request)));
    }

    /**
     * 删除用户（级联清理角色绑定、组成员与应用授权投影）
     */
    @DeleteMapping("/users/{subjectId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<Void> deleteUser(@PathVariable String subjectId) {
        userService.deleteUser(userService.getBySubjectId(subjectId).getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * 启用用户
     */
    @PutMapping("/users/{subjectId}/enable")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<Void> enableUser(@PathVariable String subjectId) {
        userService.enableUser(userService.getBySubjectId(subjectId).getId());
        return ResponseEntity.ok().build();
    }

    /**
     * 禁用用户（全端吊销会话）
     */
    @PutMapping("/users/{subjectId}/disable")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<Void> disableUser(@PathVariable String subjectId) {
        userService.disableUser(userService.getBySubjectId(subjectId).getId());
        return ResponseEntity.ok().build();
    }

    /**
     * 手动解锁用户（清除登录失败锁定与失败计数，不等自动过期）
     */
    @PutMapping("/users/{subjectId}/unlock")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<Void> unlockUser(@PathVariable String subjectId) {
        userService.unlockUser(userService.getBySubjectId(subjectId).getId());
        return ResponseEntity.ok().build();
    }

    /**
     * 管理员重置密码（即全端吊销）
     */
    @PutMapping("/users/{subjectId}/reset-password")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<Void> resetPassword(@PathVariable String subjectId,
                                              @RequestBody ResetPasswordRequest request,
                                              @AuthenticationPrincipal UserDetails userDetails) {
        IamUserDetails iamUserDetails = requireIamUserDetails(userDetails);
        Long userId = userService.getBySubjectId(subjectId).getId();
        userService.resetPassword(userId, request.getNewPassword(), iamUserDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    /**
     * 预绑定外部身份
     */
    @PostMapping("/users/{subjectId}/external-identity")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<AdminUserResponse> bindExternalIdentity(@PathVariable String subjectId,
                                                                  @RequestBody BindExternalIdentityRequest request) {
        Long userId = userService.getBySubjectId(subjectId).getId();
        return ResponseEntity.ok(toAdminUserResponse(externalIdentityBindingService.bind(
                userId, request.getProviderCode(), request.getExternalId())));
    }

    /**
     * 解绑外部身份
     */
    @DeleteMapping("/users/{subjectId}/external-identity")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<Void> unbindExternalIdentity(@PathVariable String subjectId) {
        externalIdentityBindingService.unbind(userService.getBySubjectId(subjectId).getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * 用户手机号状态（只读，脱敏号+状态+绑定时间；验证动作只属于手机持有者）
     */
    @GetMapping("/users/{subjectId}/phone")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<WebPhoneStatusResponse> userPhoneStatus(@PathVariable String subjectId) {
        return ResponseEntity.ok(phoneBindingService.statusOf(userService.getBySubjectId(subjectId)));
    }

    /**
     * 用户已分配角色列表
     */
    @GetMapping("/users/{subjectId}/roles")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<List<RoleResponse>> getUserRoles(@PathVariable String subjectId) {
        Long userId = userService.getBySubjectId(subjectId).getId();
        return ResponseEntity.ok(roleService.getUserRoles(userId).stream()
                .map(RoleResponse::from)
                .collect(Collectors.toList()));
    }

    /**
     * 给用户分配角色
     */
    @PostMapping("/users/{subjectId}/roles/{roleId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<Void> assignRole(@PathVariable String subjectId,
                                           @PathVariable Long roleId) {
        roleService.assignRole(userService.getBySubjectId(subjectId).getId(), roleId);
        return ResponseEntity.ok().build();
    }

    /**
     * 撤销用户角色
     */
    @DeleteMapping("/users/{subjectId}/roles/{roleId}")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API + "')")
    public ResponseEntity<Void> revokeRole(@PathVariable String subjectId,
                                           @PathVariable Long roleId) {
        roleService.revokeRole(userService.getBySubjectId(subjectId).getId(), roleId);
        return ResponseEntity.noContent().build();
    }

    private IamUserDetails requireIamUserDetails(UserDetails userDetails) {
        if (userDetails instanceof IamUserDetails) {
            return (IamUserDetails) userDetails;
        }
        throw new AccessDeniedException(ServerErrorMessage.PERMISSION_DENIED);
    }

    private AdminUserResponse toAdminUserResponse(IamUserEntity user) {
        return AdminUserResponse.from(user, departmentService.getDepartmentName(user.getDepartmentId()));
    }
}
