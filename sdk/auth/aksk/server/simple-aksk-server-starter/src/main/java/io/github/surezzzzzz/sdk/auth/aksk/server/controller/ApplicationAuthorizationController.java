package io.github.surezzzzzz.sdk.auth.aksk.server.controller;

import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.ApplicationAuthorizationRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ApplicationAuthorizationResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.PageResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ApplicationAuthorizationManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.ManagementApiAuthorizationHelper;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * AKSK 应用授权管理 REST 接口。
 *
 * @author surezzzzzz
 */
@RestController
@RequestMapping("/api/application-authorization")
@RequiredArgsConstructor
public class ApplicationAuthorizationController {

    private final ApplicationAuthorizationManagementService service;

    @PostMapping
    public ResponseEntity<ApplicationAuthorizationResponse> create(
            @RequestParam String clientId,
            @RequestBody ApplicationAuthorizationRequest request,
            HttpServletRequest httpRequest) {
        DataAccessPlan plan = ManagementApiAuthorizationHelper.currentPlan(httpRequest);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(service.create(clientId, request, plan));
    }

    @GetMapping
    public ResponseEntity<PageResponse<ApplicationAuthorizationResponse>> list(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(service.list(page, size, ManagementApiAuthorizationHelper.currentPlan(httpRequest)));
    }

    @GetMapping("/{clientId}")
    public ResponseEntity<ApplicationAuthorizationResponse> get(
            @PathVariable String clientId, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(service.get(clientId, ManagementApiAuthorizationHelper.currentPlan(httpRequest)));
    }

    @PutMapping("/{clientId}")
    public ResponseEntity<ApplicationAuthorizationResponse> replace(
            @PathVariable String clientId,
            @RequestBody ApplicationAuthorizationRequest request,
            HttpServletRequest httpRequest) {
        DataAccessPlan plan = ManagementApiAuthorizationHelper.currentPlan(httpRequest);
        DataAccessPlan tokenPlan = ManagementApiAuthorizationHelper.requiredPlan(httpRequest,
                SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN,
                SimpleAkskServerConstant.MANAGEMENT_ACTION_UPDATE,
                SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_UPDATE);
        return ResponseEntity.ok(service.replace(clientId, request, plan, tokenPlan));
    }

    @PostMapping("/{clientId}/revoke")
    public ResponseEntity<Void> revoke(@PathVariable String clientId, HttpServletRequest httpRequest) {
        DataAccessPlan plan = ManagementApiAuthorizationHelper.currentPlan(httpRequest);
        DataAccessPlan tokenPlan = ManagementApiAuthorizationHelper.requiredPlan(httpRequest,
                SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN,
                SimpleAkskServerConstant.MANAGEMENT_ACTION_UPDATE,
                SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_UPDATE);
        service.revoke(clientId, plan, tokenPlan);
        return ResponseEntity.noContent().build();
    }
}
