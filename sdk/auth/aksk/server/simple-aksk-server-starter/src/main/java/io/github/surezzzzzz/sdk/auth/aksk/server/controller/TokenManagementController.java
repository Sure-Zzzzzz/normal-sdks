package io.github.surezzzzzz.sdk.auth.aksk.server.controller;

import io.github.surezzzzzz.sdk.auth.aksk.core.model.TokenInfo;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.TokenQueryRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.*;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.TokenManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.ManagementApiAuthorizationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

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
    public ResponseEntity<PageResponse<TokenInfoResponse>> listTokens(
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) Integer clientType,
            @RequestParam(required = false) TokenInfo.TokenStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest httpRequest) {

        TokenQueryRequest request = new TokenQueryRequest();
        request.setClientId(clientId);
        request.setClientType(clientType);
        request.setStatus(status);
        request.setSearch(search);
        request.setPage(page);
        request.setSize(size);

        PageResponse<TokenInfoResponse> response = tokenManagementService.queryTokens(request,
                ManagementApiAuthorizationHelper.currentPlan(httpRequest));
        return ResponseEntity.ok(response);
    }

    /**
     * 查询Redis中的Token列表（支持状态过滤和分页）
     */
    @GetMapping("/redis")
    public ResponseEntity<PageResponse<TokenInfoResponse>> listRedisTokens(
            @RequestParam(required = false) TokenInfo.TokenStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest httpRequest) {
        PageResponse<TokenInfoResponse> response = tokenManagementService.queryRedisTokens(status, page, size,
                ManagementApiAuthorizationHelper.currentPlan(httpRequest));
        return ResponseEntity.ok(response);
    }

    /**
     * 查询Token详情（MySQL）
     */
    @GetMapping("/{id}")
    public ResponseEntity<TokenInfoResponse> getToken(@PathVariable String id, HttpServletRequest httpRequest) {
        TokenInfoResponse tokenInfo = tokenManagementService.getTokenById(id,
                ManagementApiAuthorizationHelper.currentPlan(httpRequest));
        if (tokenInfo == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(tokenInfo);
    }

    /**
     * 撤销单个Token
     */
    @PostMapping("/{id}/revoke")
    public ResponseEntity<Void> revokeToken(@PathVariable String id, HttpServletRequest httpRequest) {
        log.info("Revoking token: {}", id);
        tokenManagementService.revokeToken(id, ManagementApiAuthorizationHelper.currentPlan(httpRequest));
        return ResponseEntity.ok().build();
    }

    /**
     * 删除单个Token
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteToken(@PathVariable String id, HttpServletRequest httpRequest) {
        log.info("Deleting token: {}", id);
        tokenManagementService.deleteToken(id, ManagementApiAuthorizationHelper.currentPlan(httpRequest));
        return ResponseEntity.ok().build();
    }

    /**
     * 清理过期Token
     */
    @DeleteMapping("/expired")
    public ResponseEntity<DeleteExpiredResponse> deleteExpiredTokens(HttpServletRequest httpRequest) {
        log.info("Deleting expired tokens");
        int deletedCount = tokenManagementService.deleteExpiredTokens(
                ManagementApiAuthorizationHelper.currentPlan(httpRequest));

        DeleteExpiredResponse response = new DeleteExpiredResponse();
        response.setDeletedCount(deletedCount);
        response.setMessage(deletedCount > 0 ? "清理成功" : "没有过期Token需要清理");

        return ResponseEntity.ok(response);
    }

    /**
     * 获取Token统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<TokenStatisticsResponse> getStatistics(HttpServletRequest httpRequest) {
        TokenStatisticsResponse statistics = tokenManagementService.getStatistics(
                ManagementApiAuthorizationHelper.currentPlan(httpRequest));
        return ResponseEntity.ok(statistics);
    }

    /**
     * 批量撤销指定 Client 下所有活跃 Token
     */
    @DeleteMapping
    public ResponseEntity<BatchRevokeResponse> revokeByClientId(@RequestParam String clientId,
                                                                HttpServletRequest httpRequest) {
        log.info("Batch revoking tokens for client: {}", clientId);
        return ResponseEntity.ok(tokenManagementService.revokeAllByClientId(clientId,
                ManagementApiAuthorizationHelper.currentPlan(httpRequest)));
    }
}
