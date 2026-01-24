package io.github.surezzzzzz.sdk.auth.aksk.server.controller;

import io.github.surezzzzzz.sdk.auth.aksk.core.model.TokenInfo;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.TokenQueryRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.DeleteExpiredResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.PageResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.TokenInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.TokenStatisticsResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.TokenManagementService;
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
    public ResponseEntity<PageResponse<TokenInfoResponse>> listTokens(
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) Integer clientType,
            @RequestParam(required = false) TokenInfo.TokenStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        TokenQueryRequest request = new TokenQueryRequest();
        request.setClientId(clientId);
        request.setClientType(clientType);
        request.setStatus(status);
        request.setSearch(search);
        request.setPage(page);
        request.setSize(size);

        PageResponse<TokenInfoResponse> response = tokenManagementService.queryTokens(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 查询Redis中的Token列表（支持状态过滤和分页）
     */
    @GetMapping("/redis")
    public ResponseEntity<PageResponse<TokenInfoResponse>> listRedisTokens(
            @RequestParam(required = false) TokenInfo.TokenStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResponse<TokenInfoResponse> response = tokenManagementService.queryRedisTokens(status, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * 查询Token详情（MySQL）
     */
    @GetMapping("/{id}")
    public ResponseEntity<TokenInfoResponse> getToken(@PathVariable String id) {
        TokenInfoResponse tokenInfo = tokenManagementService.getTokenById(id);
        if (tokenInfo == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(tokenInfo);
    }

    /**
     * 删除单个Token
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteToken(@PathVariable String id) {
        log.info("Deleting token: {}", id);
        tokenManagementService.deleteToken(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 清理过期Token
     */
    @DeleteMapping("/expired")
    public ResponseEntity<DeleteExpiredResponse> deleteExpiredTokens() {
        log.info("Deleting expired tokens");
        int deletedCount = tokenManagementService.deleteExpiredTokens();

        DeleteExpiredResponse response = new DeleteExpiredResponse();
        response.setDeletedCount(deletedCount);
        response.setMessage(deletedCount > 0 ? "清理成功" : "没有过期Token需要清理");

        return ResponseEntity.ok(response);
    }

    /**
     * 获取Token统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<TokenStatisticsResponse> getStatistics() {
        TokenStatisticsResponse statistics = tokenManagementService.getStatistics();
        return ResponseEntity.ok(statistics);
    }
}
