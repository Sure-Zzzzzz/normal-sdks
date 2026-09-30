package io.github.surezzzzzz.sdk.auth.iam.server.controller.admin;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.dashboard.response.AdminSessionResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.dashboard.response.AdminSessionRevokeResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.response.AdminPageResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.service.dashboard.IamAdminSessionService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.user.IamUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * IAM 管理台会话管理接口（用户定位一律 subjectId）
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/admin/sessions")
@RequiredArgsConstructor
public class IamSessionAdminController {

    private final IamAdminSessionService adminSessionService;
    private final IamUserService userService;

    /**
     * 在期会话分页（管理面视角；subjectId 过滤参数缺省时为全局视图）
     */
    @GetMapping
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_SESSION_API + "')")
    public ResponseEntity<AdminPageResponse<AdminSessionResponse>> listActiveSessions(
            @RequestParam(required = false) String subjectId,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_VALUE) int page,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_SIZE_VALUE) int size) {
        Long userId = subjectId == null || subjectId.isEmpty()
                ? null : userService.getBySubjectId(subjectId).getId();
        return ResponseEntity.ok(AdminPageResponse.from(adminSessionService.listActiveSessions(userId, page, size)));
    }

    /**
     * 吊销用户全部会话（全端登出）
     */
    @PutMapping("/users/{subjectId}/revoke")
    @PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_SESSION_API + "')")
    public ResponseEntity<AdminSessionRevokeResponse> revokeUserSessions(@PathVariable String subjectId) {
        int revoked = adminSessionService.revokeUserSessions(userService.getBySubjectId(subjectId).getId());
        return ResponseEntity.ok(new AdminSessionRevokeResponse(revoked));
    }
}
