package io.github.surezzzzzz.sdk.auth.iam.server.controller.admin;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.PortalLoginLandingRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalLoginLandingResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.service.TrustedApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Portal 全局管理 API。
 */
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/admin/portal")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN
        + "') and hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_TRUSTED_APPLICATION_API + "')")
public class IamPortalAdminController {

    private final TrustedApplicationService trustedApplicationService;

    /**
     * 获取无深链登录首页单例。
     */
    @GetMapping("/login-landing")
    public ResponseEntity<PortalLoginLandingResponse> getLoginLanding() {
        return ResponseEntity.ok(trustedApplicationService.getPortalLoginLanding());
    }

    /**
     * 更新或关闭无深链登录首页单例。
     */
    @PutMapping("/login-landing")
    public ResponseEntity<PortalLoginLandingResponse> updateLoginLanding(
            @RequestBody PortalLoginLandingRequest request) {
        return ResponseEntity.ok(trustedApplicationService.updatePortalLoginLanding(request));
    }
}
