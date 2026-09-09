package io.github.surezzzzzz.sdk.auth.iam.server.controller.rest;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalAccessibleApplication;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamPortalApplicationService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamUserDetailsSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Portal 侧边栏可访问应用 API
 *
 * <p>供 Portal 前端（qiankun 壳）启动时拉取侧边栏：当前用户可访问、启用 Portal 集成的应用 + 菜单。
 * 权限：已认证用户即可调用（按用户授权过滤应用）。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/web/portal")
@RequiredArgsConstructor
public class IamPortalRestController {

    private final IamPortalApplicationService iamPortalApplicationService;

    /**
     * 当前用户可访问且已启用 Portal 集成的应用及菜单
     */
    @GetMapping("/accessible-applications")
    public ResponseEntity<List<PortalAccessibleApplication>> listAccessibleApplications(
            @AuthenticationPrincipal IamUserDetailsSupport principal) {
        if (principal == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(iamPortalApplicationService.listPortalAccessibleApplications(principal.getUserId()));
    }
}
