package io.github.surezzzzzz.sdk.auth.aksk.server.controller;

import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.ApplicationAuthorizationRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ApplicationAuthorizationResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.PageResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ApplicationAuthorizationManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.CrossResourceDataPlanHelper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.annotation.CurrentDataAccessPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AKSK 应用授权管理 REST 接口。
 *
 * @author surezzzzzz
 */
@Slf4j
@RestController
@RequestMapping("/api/application-authorization")
@RequiredArgsConstructor
public class ApplicationAuthorizationController {

    private final ApplicationAuthorizationManagementService service;
    private final CrossResourceDataPlanHelper crossResourceDataPlanHelper;

    @PostMapping
    @RequireApiPermission(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_CREATE)
    @DataPermissionOperation(resource = SimpleAkskServerConstant.MANAGEMENT_RESOURCE_APPLICATION_AUTHORIZATION,
            action = SimpleAkskServerConstant.MANAGEMENT_ACTION_CREATE)
    public ResponseEntity<ApplicationAuthorizationResponse> create(
            @RequestParam String clientId,
            @RequestBody ApplicationAuthorizationRequest request,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        log.info("创建应用授权：clientId={}", clientId);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(service.create(clientId, request, plan));
    }

    @GetMapping
    @RequireApiPermission(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_READ)
    @DataPermissionOperation(resource = SimpleAkskServerConstant.MANAGEMENT_RESOURCE_APPLICATION_AUTHORIZATION,
            action = SimpleAkskServerConstant.MANAGEMENT_ACTION_READ)
    public ResponseEntity<PageResponse<ApplicationAuthorizationResponse>> list(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        log.debug("查询应用授权列表：page={}, size={}", page, size);
        return ResponseEntity.ok(service.list(page, size, plan));
    }

    @GetMapping("/{clientId}")
    @RequireApiPermission(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_READ)
    @DataPermissionOperation(resource = SimpleAkskServerConstant.MANAGEMENT_RESOURCE_APPLICATION_AUTHORIZATION,
            action = SimpleAkskServerConstant.MANAGEMENT_ACTION_READ)
    public ResponseEntity<ApplicationAuthorizationResponse> get(
            @PathVariable String clientId, @CurrentDataAccessPlan DataAccessPlan plan) {
        log.debug("查询应用授权详情：clientId={}", clientId);
        return ResponseEntity.ok(service.get(clientId, plan));
    }

    @PutMapping("/{clientId}")
    @RequireApiPermission(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_UPDATE)
    @DataPermissionOperation(resource = SimpleAkskServerConstant.MANAGEMENT_RESOURCE_APPLICATION_AUTHORIZATION,
            action = SimpleAkskServerConstant.MANAGEMENT_ACTION_UPDATE)
    public ResponseEntity<ApplicationAuthorizationResponse> replace(
            @PathVariable String clientId,
            @RequestBody ApplicationAuthorizationRequest request,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        log.info("替换应用授权：clientId={}", clientId);
        DataAccessPlan tokenPlan = crossResourceDataPlanHelper.require(
                SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN,
                SimpleAkskServerConstant.MANAGEMENT_ACTION_UPDATE,
                SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_UPDATE);
        return ResponseEntity.ok(service.replace(clientId, request, plan, tokenPlan));
    }

    @PostMapping("/{clientId}/revoke")
    @RequireApiPermission(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_REVOKE)
    @DataPermissionOperation(resource = SimpleAkskServerConstant.MANAGEMENT_RESOURCE_APPLICATION_AUTHORIZATION,
            action = SimpleAkskServerConstant.MANAGEMENT_ACTION_REVOKE)
    public ResponseEntity<Void> revoke(@PathVariable String clientId,
                                       @CurrentDataAccessPlan DataAccessPlan plan) {
        log.info("撤销应用授权：clientId={}", clientId);
        DataAccessPlan tokenPlan = crossResourceDataPlanHelper.require(
                SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN,
                SimpleAkskServerConstant.MANAGEMENT_ACTION_UPDATE,
                SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_UPDATE);
        service.revoke(clientId, plan, tokenPlan);
        return ResponseEntity.noContent().build();
    }
}
