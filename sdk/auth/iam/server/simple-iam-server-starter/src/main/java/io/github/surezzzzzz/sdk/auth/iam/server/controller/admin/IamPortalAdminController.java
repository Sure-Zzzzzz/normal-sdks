package io.github.surezzzzzz.sdk.auth.iam.server.controller.admin;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.PortalApplicationOrderRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.PortalLoginLandingRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalApplicationOrderResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalLoginLandingResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.service.portal.IamPortalApplicationOrderService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.IamTrustedApplicationService;
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

    private final IamTrustedApplicationService trustedApplicationService;
    private final IamPortalApplicationOrderService portalApplicationOrderService;

    /**
     * 获取全部 Portal 集成的全局根节点顺序。
     */
    @GetMapping("/application-order")
    public ResponseEntity<PortalApplicationOrderResponse> getApplicationOrder() {
        return ResponseEntity.ok(portalApplicationOrderService.getApplicationOrder());
    }

    /**
     * 以完整快照替换 Portal 应用根节点顺序。
     */
    @PutMapping("/application-order")
    public ResponseEntity<PortalApplicationOrderResponse> updateApplicationOrder(
            @RequestBody PortalApplicationOrderRequest request) {
        return ResponseEntity.ok(portalApplicationOrderService.updateApplicationOrder(request));
    }

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
