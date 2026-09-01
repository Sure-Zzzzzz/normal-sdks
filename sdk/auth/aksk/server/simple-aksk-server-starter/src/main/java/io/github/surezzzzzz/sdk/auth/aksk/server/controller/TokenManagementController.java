package io.github.surezzzzzz.sdk.auth.aksk.server.controller;

import io.github.surezzzzzz.sdk.auth.aksk.core.model.TokenInfo;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.TokenQueryRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.*;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.TokenManagementService;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.annotation.CurrentDataAccessPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Token Management Controller
 * 提供Token管理的REST API接口（仅内网访问）
 *
 * @author surezzzzzz
 */
@Slf4j
@RestController
@RequestMapping("/api/token")
@RequiredArgsConstructor
public class TokenManagementController {

    private final TokenManagementService tokenManagementService;

    /**
     * 查询Token列表（MySQL）
     */
    @GetMapping
    @RequireApiPermission(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_READ)
    @DataPermissionOperation(resource = SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN,
            action = SimpleAkskServerConstant.MANAGEMENT_ACTION_READ)
    public ResponseEntity<PageResponse<TokenInfoResponse>> listTokens(
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) Integer clientType,
            @RequestParam(required = false) TokenInfo.TokenStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentDataAccessPlan DataAccessPlan plan) {

        TokenQueryRequest request = new TokenQueryRequest();
        request.setClientId(clientId);
        request.setClientType(clientType);
        request.setStatus(status);
        request.setSearch(search);
        request.setPage(page);
        request.setSize(size);

        PageResponse<TokenInfoResponse> response = tokenManagementService.queryTokens(request, plan);
        return ResponseEntity.ok(response);
    }

    /**
     * 查询Redis中的Token列表（支持状态过滤和分页）
     */
    @GetMapping("/redis")
    @RequireApiPermission(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_READ)
    @DataPermissionOperation(resource = SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN,
            action = SimpleAkskServerConstant.MANAGEMENT_ACTION_READ)
    public ResponseEntity<PageResponse<TokenInfoResponse>> listRedisTokens(
            @RequestParam(required = false) TokenInfo.TokenStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        PageResponse<TokenInfoResponse> response = tokenManagementService.queryRedisTokens(status, page, size, plan);
        return ResponseEntity.ok(response);
    }

    /**
     * 查询Token详情（MySQL）
     */
    @GetMapping("/{id}")
    @RequireApiPermission(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_READ)
    @DataPermissionOperation(resource = SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN,
            action = SimpleAkskServerConstant.MANAGEMENT_ACTION_READ)
    public ResponseEntity<TokenInfoResponse> getToken(@PathVariable String id,
                                                      @CurrentDataAccessPlan DataAccessPlan plan) {
        TokenInfoResponse tokenInfo = tokenManagementService.getTokenById(id, plan);
        if (tokenInfo == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(tokenInfo);
    }

    /**
     * 撤销单个Token
     */
    @PostMapping("/{id}/revoke")
    @RequireApiPermission(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_UPDATE)
    @DataPermissionOperation(resource = SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN,
            action = SimpleAkskServerConstant.MANAGEMENT_ACTION_UPDATE)
    public ResponseEntity<Void> revokeToken(@PathVariable String id,
                                            @CurrentDataAccessPlan DataAccessPlan plan) {
        log.info("Revoking token: {}", id);
        tokenManagementService.revokeToken(id, plan);
        return ResponseEntity.ok().build();
    }

    /**
     * 删除单个Token
     */
    @DeleteMapping("/{id}")
    @RequireApiPermission(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_DELETE)
    @DataPermissionOperation(resource = SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN,
            action = SimpleAkskServerConstant.MANAGEMENT_ACTION_DELETE)
    public ResponseEntity<Void> deleteToken(@PathVariable String id,
                                            @CurrentDataAccessPlan DataAccessPlan plan) {
        log.info("Deleting token: {}", id);
        tokenManagementService.deleteToken(id, plan);
        return ResponseEntity.ok().build();
    }

    /**
     * 清理过期Token
     */
    @DeleteMapping("/expired")
    @RequireApiPermission(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_DELETE)
    @DataPermissionOperation(resource = SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN,
            action = SimpleAkskServerConstant.MANAGEMENT_ACTION_DELETE)
    public ResponseEntity<DeleteExpiredResponse> deleteExpiredTokens(@CurrentDataAccessPlan DataAccessPlan plan) {
        log.info("Deleting expired tokens");
        int deletedCount = tokenManagementService.deleteExpiredTokens(plan);

        DeleteExpiredResponse response = new DeleteExpiredResponse();
        response.setDeletedCount(deletedCount);
        response.setMessage(deletedCount > 0 ? "清理成功" : "没有过期Token需要清理");

        return ResponseEntity.ok(response);
    }

    /**
     * 获取Token统计信息
     */
    @GetMapping("/statistics")
    @RequireApiPermission(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_READ)
    @DataPermissionOperation(resource = SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN,
            action = SimpleAkskServerConstant.MANAGEMENT_ACTION_READ)
    public ResponseEntity<TokenStatisticsResponse> getStatistics(@CurrentDataAccessPlan DataAccessPlan plan) {
        TokenStatisticsResponse statistics = tokenManagementService.getStatistics(plan);
        return ResponseEntity.ok(statistics);
    }

    /**
     * 批量撤销指定 Client 下所有活跃 Token
     */
    @DeleteMapping
    @RequireApiPermission(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_UPDATE)
    @DataPermissionOperation(resource = SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN,
            action = SimpleAkskServerConstant.MANAGEMENT_ACTION_UPDATE)
    public ResponseEntity<BatchRevokeResponse> revokeByClientId(@RequestParam String clientId,
                                                                @CurrentDataAccessPlan DataAccessPlan plan) {
        log.info("Batch revoking tokens for client: {}", clientId);
        return ResponseEntity.ok(tokenManagementService.revokeAllByClientId(clientId, plan));
    }
}
