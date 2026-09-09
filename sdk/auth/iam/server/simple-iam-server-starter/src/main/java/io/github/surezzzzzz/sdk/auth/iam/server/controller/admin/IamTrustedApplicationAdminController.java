package io.github.surezzzzzz.sdk.auth.iam.server.controller.admin;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.request.PutApplicationPermissionManifestRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.response.ApplicationPermissionManifestResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.response.AdminPageResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.UpdateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.UpdateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response.*;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamApplicationPermissionManifestService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.TrustedApplicationClientService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.TrustedApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * IAM 管理域可信应用管理 API（应用维度 + client 维度）
 *
 * <p>一应用可关联多个 OAuth2 客户端。应用维度管理应用主表 / Portal 集成 / 菜单；
 * client 维度挂在应用下，管理 OAuth2 客户端的 redirect_uri / scope / clientType / PKCE / secret。
 * 机器凭证（AK/SK）不在本接口创建，请通过 aksk-server。
 *
 * <p>权限：需要 ROLE_iam_admin + iam:trusted-application:api（SecurityFilterChain 鉴权）。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/admin/trusted-applications")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_TRUSTED_APPLICATION_API + "')")
public class IamTrustedApplicationAdminController {

    private final TrustedApplicationService trustedApplicationService;
    private final TrustedApplicationClientService trustedApplicationClientService;
    private final IamApplicationPermissionManifestService applicationPermissionManifestService;

    // ==================== 应用维度 ====================

    @GetMapping
    public ResponseEntity<List<TrustedApplicationResponse>> listApplications() {
        return ResponseEntity.ok(trustedApplicationService.listApplications());
    }

    /**
     * 可信应用分页
     */
    @GetMapping("/page")
    public ResponseEntity<AdminPageResponse<TrustedApplicationResponse>> listApplicationPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_VALUE) int page,
            @RequestParam(defaultValue = SimpleIamServerConstant.DEFAULT_ADMIN_PAGE_SIZE_VALUE) int size) {
        return ResponseEntity.ok(AdminPageResponse.from(
                trustedApplicationService.listApplicationPage(keyword, page, size)));
    }

    /**
     * 可信应用详情
     */
    @GetMapping("/{applicationId}")
    public ResponseEntity<TrustedApplicationDetailResponse> getApplication(
            @PathVariable Long applicationId) {
        return ResponseEntity.ok(trustedApplicationService.getApplication(applicationId));
    }

    /**
     * 创建可信应用（必须带初始客户端，secret 仅本次返回）
     */
    @PostMapping
    public ResponseEntity<TrustedApplicationCreatedResponse> createApplication(
            @RequestBody CreateTrustedApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(trustedApplicationService.createApplication(request));
    }

    /**
     * 更新可信应用
     */
    @PutMapping("/{applicationId}")
    public ResponseEntity<TrustedApplicationResponse> updateApplication(
            @PathVariable Long applicationId, @RequestBody UpdateTrustedApplicationRequest request) {
        return ResponseEntity.ok(trustedApplicationService.updateApplication(applicationId, request));
    }

    /**
     * 删除可信应用（按菜单 → Portal → 权限清单 → 授权规则 → 应用授权投影 → Consent 投影 → 客户端 → 应用 级联）
     */
    @DeleteMapping("/{applicationId}")
    public ResponseEntity<Void> deleteApplication(@PathVariable Long applicationId) {
        trustedApplicationService.deleteApplication(applicationId);
        return ResponseEntity.noContent().build();
    }

    // ==================== 客户端维度（挂在应用下） ====================

    @GetMapping("/{applicationId}/clients")
    public ResponseEntity<List<TrustedApplicationClientResponse>> listClients(
            @PathVariable Long applicationId) {
        return ResponseEntity.ok(trustedApplicationClientService.listClients(applicationId));
    }

    /**
     * OAuth 客户端详情
     */
    @GetMapping("/{applicationId}/clients/{clientId}")
    public ResponseEntity<TrustedApplicationClientResponse> getClient(
            @PathVariable Long applicationId, @PathVariable String clientId) {
        return ResponseEntity.ok(trustedApplicationClientService.getClient(applicationId, clientId));
    }

    /**
     * 新增 OAuth 客户端（secret 仅本次返回）
     */
    @PostMapping("/{applicationId}/clients")
    public ResponseEntity<TrustedApplicationClientSecretResponse> addClient(
            @PathVariable Long applicationId,
            @RequestBody CreateTrustedApplicationClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(trustedApplicationClientService.addClient(applicationId, request));
    }

    /**
     * 更新 OAuth 客户端
     */
    @PutMapping("/{applicationId}/clients/{clientId}")
    public ResponseEntity<TrustedApplicationClientResponse> updateClient(
            @PathVariable Long applicationId, @PathVariable String clientId,
            @RequestBody UpdateTrustedApplicationClientRequest request) {
        return ResponseEntity.ok(trustedApplicationClientService.updateClient(applicationId, clientId, request));
    }

    /**
     * 删除 OAuth 客户端
     */
    @DeleteMapping("/{applicationId}/clients/{clientId}")
    public ResponseEntity<Void> deleteClient(@PathVariable Long applicationId,
                                             @PathVariable String clientId) {
        trustedApplicationClientService.deleteClient(applicationId, clientId);
        return ResponseEntity.noContent().build();
    }

    // ==================== 权限清单（挂在应用下） ====================

    @GetMapping("/{applicationId}/permission-manifest")
    public ResponseEntity<ApplicationPermissionManifestResponse> getPermissionManifest(
            @PathVariable Long applicationId) {
        return ResponseEntity.ok(applicationPermissionManifestService.getManifest(applicationId));
    }

    /**
     * 上传应用权限清单（manifestVersion 递增，触发授权投影重算）
     */
    @PutMapping("/{applicationId}/permission-manifest")
    public ResponseEntity<ApplicationPermissionManifestResponse> putPermissionManifest(
            @PathVariable Long applicationId,
            @RequestBody PutApplicationPermissionManifestRequest request) {
        ApplicationPermissionManifestResponse response = applicationPermissionManifestService
                .putManifest(applicationId, request);
        HttpStatus status = response.getManifestVersion() == 1L
                ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }
}
