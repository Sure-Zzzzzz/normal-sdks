package io.github.surezzzzzz.sdk.auth.aksk.server.controller;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ClientType;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.CreateClientRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.UpdateClientRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.*;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ManagementAccessDeniedException;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.CrossResourceDataPlanHelper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.annotation.CurrentDataAccessPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Client Management Controller
 * 提供Client管理的REST API接口（仅内网访问）
 *
 * @author surezzzzzz
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/client")
@RequiredArgsConstructor
public class ClientManagementController {

    private final ClientManagementService clientManagementService;
    private final CrossResourceDataPlanHelper crossResourceDataPlanHelper;

    /**
     * 创建Client（统一接口，通过type区分平台级/用户级）
     */
    @PostMapping
    @RequireApiPermission(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_CLIENT_CREATE)
    @DataPermissionOperation(resource = SimpleAkskServerConstant.MANAGEMENT_RESOURCE_CLIENT,
            action = SimpleAkskServerConstant.MANAGEMENT_ACTION_CREATE)
    public ResponseEntity<CreateClientResponse> createClient(@CurrentDataAccessPlan DataAccessPlan plan,
                                                             @RequestBody CreateClientRequest request) {
        log.info("Creating client: type={}, name={}", request.getType(), request.getName());

        ClientInfoResponse clientInfo;

        if (ClientType.PLATFORM.getValue().equalsIgnoreCase(request.getType())) {
            clientInfo = clientManagementService.createPlatformClient(request.getName(), request.getScopes(), plan);
        } else if (ClientType.USER.getValue().equalsIgnoreCase(request.getType())) {
            if (request.getOwnerUserId() == null || request.getOwnerUsername() == null) {
                return ResponseEntity.badRequest().build();
            }
            clientInfo = clientManagementService.createUserClient(
                    request.getOwnerUserId(),
                    request.getOwnerUsername(),
                    request.getName(),
                    request.getScopes(),
                    plan
            );
        } else {
            return ResponseEntity.badRequest().build();
        }

        CreateClientResponse response = new CreateClientResponse();
        response.setClientId(clientInfo.getClientId());
        response.setClientSecret(clientInfo.getClientSecret());
        response.setType(request.getType());
        response.setName(request.getName());

        return ResponseEntity.ok(response);
    }

    /**
     * 查询Client列表（支持分页和多种过滤条件）
     *
     * @param clientIds   批量查询的Client ID列表（可选，最多100个）
     * @param ownerUserId 所属用户ID（可选）
     * @param type        Client类型：platform/user（可选）
     * @param page        页码，从1开始（可选，默认1）
     * @param size        每页大小（可选，默认20）
     * @return 分页的Client列表或批量查询结果
     */
    @GetMapping
    @RequireApiPermission(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_CLIENT_READ)
    @DataPermissionOperation(resource = SimpleAkskServerConstant.MANAGEMENT_RESOURCE_CLIENT,
            action = SimpleAkskServerConstant.MANAGEMENT_ACTION_READ)
    public ResponseEntity<?> listClients(
            @RequestParam(required = false) @Size(max = 100, message = "clientIds不能超过100个") List<String> clientIds,
            @RequestParam(required = false) String ownerUserId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @CurrentDataAccessPlan DataAccessPlan plan) {

        // 优先处理批量查询
        if (clientIds != null && !clientIds.isEmpty()) {
            log.info("Batch querying clients: count={}", clientIds.size());

            // 使用SQL IN查询,一次性查询所有ID,直接返回Map
            List<String> trimmedIds = clientIds.stream()
                    .map(String::trim)
                    .collect(Collectors.toList());

            Map<String, ClientInfoResponse> clientMap = clientManagementService.batchGetClientsByIds(trimmedIds, plan);

            BatchClientResponse response = new BatchClientResponse();
            response.setClients(clientMap);

            return ResponseEntity.ok(response);
        }

        // 分页列表查询
        log.info("Querying clients: ownerUserId={}, type={}, page={}, size={}",
                ownerUserId, type, page, size);

        PageResponse<ClientInfoResponse> response = clientManagementService.listClients(
                ownerUserId, type, page, size, plan);

        return ResponseEntity.ok(response);
    }

    /**
     * 查询Client详情
     */
    @GetMapping("/{clientId}")
    @RequireApiPermission(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_CLIENT_READ)
    @DataPermissionOperation(resource = SimpleAkskServerConstant.MANAGEMENT_RESOURCE_CLIENT,
            action = SimpleAkskServerConstant.MANAGEMENT_ACTION_READ)
    public ResponseEntity<ClientInfoResponse> getClient(@PathVariable String clientId,
                                                        @CurrentDataAccessPlan DataAccessPlan plan) {
        try {
            ClientInfoResponse clientInfo = clientManagementService.getClientById(clientId, plan);
            return ResponseEntity.ok(clientInfo);
        } catch (ManagementAccessDeniedException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Client not found: {}", clientId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 删除Client
     */
    @DeleteMapping("/{clientId}")
    @RequireApiPermission(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_CLIENT_DELETE)
    @DataPermissionOperation(resource = SimpleAkskServerConstant.MANAGEMENT_RESOURCE_CLIENT,
            action = SimpleAkskServerConstant.MANAGEMENT_ACTION_DELETE)
    public ResponseEntity<Void> deleteClient(@PathVariable String clientId,
                                             @CurrentDataAccessPlan DataAccessPlan clientPlan) {
        log.info("Deleting client: {}", clientId);
        DataAccessPlan tokenPlan = crossResourceDataPlanHelper.require(
                SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN,
                SimpleAkskServerConstant.MANAGEMENT_ACTION_UPDATE,
                SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_UPDATE);
        clientManagementService.deleteClient(clientId, clientPlan, tokenPlan);
        return ResponseEntity.ok().build();
    }

    /**
     * 批量更新用户的Client权限（权限同步）
     */
    @PatchMapping
    @RequireApiPermission(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_CLIENT_UPDATE)
    @DataPermissionOperation(resource = SimpleAkskServerConstant.MANAGEMENT_RESOURCE_CLIENT,
            action = SimpleAkskServerConstant.MANAGEMENT_ACTION_UPDATE)
    public ResponseEntity<SyncScopesResponse> syncUserScopes(
            @RequestParam(SimpleAkskServerConstant.PARAM_OWNER_USER_ID) String userId,
            @RequestBody UpdateClientRequest request,
            @CurrentDataAccessPlan DataAccessPlan plan) {

        log.info("Syncing scopes for user: {}, new scopes: {}", userId, request.getScopes());

        int updatedCount = clientManagementService.syncUserScopes(userId, request.getScopes(), plan);

        SyncScopesResponse response = new SyncScopesResponse();
        response.setOwnerUserId(userId);
        response.setUpdatedCount(updatedCount);
        response.setMessage(updatedCount > 0 ? ServerErrorMessage.SYNC_SCOPES_SUCCESS : ServerErrorMessage.SYNC_SCOPES_NOT_FOUND);

        return ResponseEntity.ok(response);
    }

    /**
     * 重置 Client Secret
     */
    @PutMapping("/{clientId}/secret")
    @RequireApiPermission(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_CLIENT_UPDATE)
    @DataPermissionOperation(resource = SimpleAkskServerConstant.MANAGEMENT_RESOURCE_CLIENT,
            action = SimpleAkskServerConstant.MANAGEMENT_ACTION_UPDATE)
    public ResponseEntity<ResetSecretResponse> resetSecret(
            @PathVariable String clientId,
            @RequestParam(defaultValue = "true") boolean revokeTokens,
            @CurrentDataAccessPlan DataAccessPlan clientPlan) {
        log.info("Resetting secret for client: {}, revokeTokens={}", clientId, revokeTokens);
        DataAccessPlan tokenPlan = revokeTokens
                ? crossResourceDataPlanHelper.require(
                SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN,
                SimpleAkskServerConstant.MANAGEMENT_ACTION_UPDATE,
                SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_UPDATE)
                : null;
        return ResponseEntity.ok(clientManagementService.resetSecret(clientId, revokeTokens, clientPlan, tokenPlan));
    }

    /**
     * 更新Client信息（启用/禁用、权限范围、名称、归属信息）
     */
    @PatchMapping("/{clientId}")
    @RequireApiPermission(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_CLIENT_UPDATE)
    @DataPermissionOperation(resource = SimpleAkskServerConstant.MANAGEMENT_RESOURCE_CLIENT,
            action = SimpleAkskServerConstant.MANAGEMENT_ACTION_UPDATE)
    public ResponseEntity<ApiResponse> updateClient(
            @PathVariable String clientId,
            @RequestBody UpdateClientRequest request,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        log.info("Updating client: {}", clientId);

        if (request.getEnabled() != null) {
            if (request.getEnabled()) {
                clientManagementService.enableClient(clientId, plan);
            } else {
                clientManagementService.disableClient(clientId, plan);
            }
        } else if (request.getScopes() != null) {
            clientManagementService.updateClientScopes(clientId, request.getScopes(), plan);
        } else if (request.getName() != null) {
            clientManagementService.updateClientName(clientId, request.getName(), plan);
        } else if (request.getOwnerUserId() != null) {
            clientManagementService.updateOwnerInfo(clientId, request.getOwnerUserId(), request.getOwnerUsername(), plan);
        } else {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(new ApiResponse("更新成功"));
    }
}
