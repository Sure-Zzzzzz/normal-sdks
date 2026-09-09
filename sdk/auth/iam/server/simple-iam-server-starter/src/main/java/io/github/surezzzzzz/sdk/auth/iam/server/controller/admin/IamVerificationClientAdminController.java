package io.github.surezzzzzz.sdk.auth.iam.server.controller.admin;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.resource.request.CreateResourceVerificationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.resource.response.ResourceVerificationClientResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.resource.response.ResourceVerificationClientSecretResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamResourceVerificationClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * IAM 管理域资源验证客户端管理 API
 *
 * <p>资源验证客户端是资源服务调用 POST /iam/resource/tokens/verify 的机器凭据
 * （Basic 认证），挂在可信应用下管理。secret 由服务端生成，仅在创建 / 轮换响应中
 * 返回一次；撤销为语义撤销，记录保留供审计，不提供物理删除。
 *
 * <p>权限：需要 ROLE_iam_admin + iam:trusted-application:api（SecurityFilterChain + 方法级鉴权）。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/admin/trusted-applications/{applicationId}/resource-verification-clients")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + SimpleIamServerConstant.BUILT_IN_PERMISSION_TRUSTED_APPLICATION_API + "')")
public class IamVerificationClientAdminController {

    private final IamResourceVerificationClientService verificationClientService;

    /**
     * 创建资源验证客户端，返回一次性明文 secret。
     */
    @PostMapping
    public ResponseEntity<ResourceVerificationClientSecretResponse> createClient(
            @PathVariable Long applicationId,
            @RequestBody CreateResourceVerificationClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(verificationClientService.createClient(applicationId, request));
    }

    /**
     * 列出应用下全部资源验证客户端摘要（不回显任何密钥形态）。
     */
    @GetMapping
    public ResponseEntity<List<ResourceVerificationClientResponse>> listClients(
            @PathVariable Long applicationId) {
        return ResponseEntity.ok(verificationClientService.listClients(applicationId));
    }

    /**
     * 查询单个资源验证客户端详情（不回显任何密钥形态）。
     */
    @GetMapping("/{clientId}")
    public ResponseEntity<ResourceVerificationClientResponse> getClient(
            @PathVariable Long applicationId, @PathVariable String clientId) {
        return ResponseEntity.ok(verificationClientService.getClient(applicationId, clientId));
    }

    /**
     * 轮换资源验证客户端 secret，旧 secret 立即失效；返回一次性新明文 secret。
     */
    @PostMapping("/{clientId}/secret")
    public ResponseEntity<ResourceVerificationClientSecretResponse> rotateSecret(
            @PathVariable Long applicationId, @PathVariable String clientId) {
        return ResponseEntity.ok(verificationClientService.rotateSecret(applicationId, clientId));
    }

    /**
     * 撤销资源验证客户端，认证立即失效；重复撤销幂等返回 204。
     */
    @DeleteMapping("/{clientId}")
    public ResponseEntity<Void> revokeClient(
            @PathVariable Long applicationId, @PathVariable String clientId) {
        verificationClientService.revokeClient(applicationId, clientId);
        return ResponseEntity.noContent().build();
    }
}
